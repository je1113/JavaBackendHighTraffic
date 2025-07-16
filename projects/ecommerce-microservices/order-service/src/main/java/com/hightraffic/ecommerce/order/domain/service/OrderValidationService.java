package com.hightraffic.ecommerce.order.domain.service;

import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.OrderItem;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주문 검증 도메인 서비스
 * 주문과 관련된 복잡한 검증 로직을 담당
 * 설정 기반으로 검증 규칙을 유연하게 관리
 */
@Service
public class OrderValidationService {
    
    private final OrderRepository orderRepository;
    private final OrderBusinessRulesConfig config;
    
    public OrderValidationService(OrderRepository orderRepository, OrderBusinessRulesConfig config) {
        this.orderRepository = orderRepository;
        this.config = config;
    }
    
    
    
    /**
     * 중복 주문 검증
     * 짧은 시간 내 동일한 주문 방지
     */
    public void validateDuplicateOrder(CustomerId customerId, List<OrderItem> items) {
        int preventionMinutes = config.getTime().getDuplicateOrderPreventionMinutes();
        LocalDateTime preventionTime = LocalDateTime.now().minusMinutes(preventionMinutes);
        
        List<Order> recentOrders = orderRepository.findByCustomerIdAndCreatedAtAfter(
            customerId, preventionTime
        );
        
        boolean hasDuplicateOrder = recentOrders.stream()
            .anyMatch(order -> isSameItemComposition(order.getItems(), items));
        
        if (hasDuplicateOrder) {
            throw new IllegalStateException("동일한 주문이 이미 접수되었습니다. 잠시 후 다시 시도해주세요.");
        }
    }
    
    
    /**
     * 주문 취소 가능 여부 검증
     * 주문 상태와 시간에 따른 취소 가능 여부 확인
     */
    public boolean canCancelOrder(Order order) {
        // 이미 취소 불가능한 상태인지 확인
        if (!order.getStatus().isCancellable()) {
            return false;
        }
        
        // 설정된 시간이 지났는지 확인
        int cancellationHours = config.getTime().getOrderCancellationHours();
        LocalDateTime limitTime = LocalDateTime.now().minusHours(cancellationHours);
        if (order.getCreatedAt().isBefore(limitTime)) {
            return false;
        }
        
        // 배송이 시작된 경우 취소 불가
        if (order.getStatus().isAfterShipping()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 기본적인 주문 항목 검증
     * 수량이 양수인지만 확인 (다른 제한은 제거)
     */
    public void validateBasicOrderItems(List<OrderItem> items) {
        boolean hasZeroQuantity = items.stream()
            .anyMatch(item -> item.getQuantity() <= 0);
        
        if (hasZeroQuantity) {
            throw new IllegalArgumentException("주문 수량은 1개 이상이어야 합니다.");
        }
    }
    
    // Helper methods
    
    private boolean isSameItemComposition(List<OrderItem> items1, List<OrderItem> items2) {
        if (items1.size() != items2.size()) {
            return false;
        }
        
        Map<ProductId, Integer> itemMap1 = items1.stream()
            .collect(Collectors.toMap(
                OrderItem::getProductId,
                OrderItem::getQuantity
            ));
        
        Map<ProductId, Integer> itemMap2 = items2.stream()
            .collect(Collectors.toMap(
                OrderItem::getProductId,
                OrderItem::getQuantity
            ));
        
        return itemMap1.equals(itemMap2);
    }
    
}