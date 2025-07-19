package com.hightraffic.ecommerce.order.domain.service;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import com.hightraffic.ecommerce.order.domain.service.OrderPricingPolicy;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 도메인 서비스
 * 여러 Aggregate 간의 협력이나 복잡한 비즈니스 규칙을 처리
 * 정책 인터페이스를 통해 비즈니스 규칙을 유연하게 관리
 */
public class OrderDomainService {
    
    private final OrderRepository orderRepository;
    private final OrderPricingPolicy pricingPolicy;
    
    public OrderDomainService(OrderRepository orderRepository, OrderPricingPolicy pricingPolicy) {
        this.orderRepository = orderRepository;
        this.pricingPolicy = pricingPolicy;
    }
    
    
    /**
     * 고가 주문 여부 확인
     * 특별한 처리가 필요한 고가 주문인지 판단
     */
    public boolean isHighValueOrder(Order order) {
        return order.getTotalAmount().getAmount()
            .compareTo(pricingPolicy.getVipThreshold()) >= 0;
    }
    
    /**
     * 주문 병합 가능 여부 확인
     * 동일 고객의 PENDING 상태 주문들을 병합할 수 있는지 검증
     */
    public boolean canMergeOrders(Order order1, Order order2) {
        // 1. 동일 고객 검증
        if (!order1.getCustomerId().equals(order2.getCustomerId())) {
            return false;
        }
        
        // 2. 두 주문 모두 PENDING 상태인지 검증
        return order1.getStatus() == OrderStatus.PENDING && 
               order2.getStatus() == OrderStatus.PENDING;
    }
    
    /**
     * 주문 병합 실행
     * 두 개의 주문을 하나로 병합
     */
    public Order mergeOrders(Order targetOrder, Order sourceOrder) {
        if (!canMergeOrders(targetOrder, sourceOrder)) {
            throw new IllegalStateException("주문을 병합할 수 없습니다");
        }
        
        // 소스 주문의 모든 아이템을 타겟 주문으로 이동
        sourceOrder.getItems().forEach(item -> {
            if (!targetOrder.hasItem(item.getProductId())) {
                targetOrder.addItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
                );
            } else {
                // 이미 존재하는 상품이면 수량만 증가
                targetOrder.updateItemQuantity(
                    item.getProductId(), 
                    item.getQuantity()
                );
            }
        });
        
        // 소스 주문은 취소 처리
        sourceOrder.cancel("주문이 다른 주문과 병합되었습니다");
        
        // 병합된 주문에 대한 이벤트 발행
        // TODO: OrderMergedEvent 발행
        
        return targetOrder;
    }
    
    /**
     * 주문 우선순위 계산
     * 고객 등급, 주문 금액, 주문 시간 등을 고려한 우선순위 계산
     */
    public int calculateOrderPriority(Order order) {
        int priority = 0;
        
        // 1. 고가 주문에 높은 우선순위
        if (isHighValueOrder(order)) {
            priority += 100;
        }
        
        // 2. 주문 금액에 따른 가중치 (만원당 1점)
        BigDecimal amountScore = order.getTotalAmount().getAmount()
            .divide(new BigDecimal("10000"), 0, BigDecimal.ROUND_DOWN);
        priority += amountScore.intValue();
        
        // 3. 대기 시간에 따른 가중치 (1시간당 10점)
        if (order.getCreatedAt() != null) {
            long hoursWaiting = java.time.Duration.between(
                order.getCreatedAt(), 
                LocalDateTime.now()
            ).toHours();
            priority += (int) (hoursWaiting * 10);
        }
        
        return priority;
    }
    
    /**
     * 반복 주문 패턴 감지
     * 고객의 주문 패턴을 분석하여 반복 주문인지 확인
     */
    public boolean isRepeatOrder(CustomerId customerId, List<ProductId> productIds) {
        // 최근 3개월 내 동일한 상품 조합으로 주문한 이력 확인
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        List<Order> recentOrders = orderRepository.findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(
            customerId, 
            threeMonthsAgo
        );
        
        return recentOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.COMPLETED)
            .anyMatch(order -> {
                List<ProductId> orderProductIds = order.getItems().stream()
                    .map(item -> item.getProductId())
                    .sorted()
                    .toList();
                return orderProductIds.equals(productIds.stream().sorted().toList());
            });
    }
    
    /**
     * 주문 통계 정보 계산
     * 고객의 주문 통계 정보를 계산하여 비즈니스 결정에 활용
     */
    public OrderStatistics calculateCustomerOrderStatistics(CustomerId customerId) {
        List<Order> completedOrders = orderRepository.findByCustomerIdAndStatus(
            customerId, 
            OrderStatus.COMPLETED
        );
        
        if (completedOrders.isEmpty()) {
            return OrderStatistics.empty();
        }
        
        // 총 주문 금액
        Money totalAmount = completedOrders.stream()
            .map(Order::getTotalAmount)
            .reduce(Money.ZERO("KRW"), Money::add);
        
        // 평균 주문 금액
        BigDecimal averageAmount = totalAmount.getAmount()
            .divide(new BigDecimal(completedOrders.size()), 2, BigDecimal.ROUND_HALF_UP);
        
        // 최대 주문 금액
        Money maxAmount = completedOrders.stream()
            .map(Order::getTotalAmount)
            .max(Money::compareTo)
            .orElse(Money.ZERO("KRW"));
        
        return new OrderStatistics(
            completedOrders.size(),
            totalAmount,
            new Money(averageAmount, "KRW"),
            maxAmount
        );
    }
    
    /**
     * 주문 통계 정보 클래스
     */
    public static class OrderStatistics {
        private final int totalOrders;
        private final Money totalAmount;
        private final Money averageAmount;
        private final Money maxAmount;
        
        public OrderStatistics(int totalOrders, Money totalAmount, Money averageAmount, Money maxAmount) {
            this.totalOrders = totalOrders;
            this.totalAmount = totalAmount;
            this.averageAmount = averageAmount;
            this.maxAmount = maxAmount;
        }
        
        public static OrderStatistics empty() {
            return new OrderStatistics(0, Money.ZERO("KRW"), Money.ZERO("KRW"), Money.ZERO("KRW"));
        }
        
        // Getters
        public int getTotalOrders() { return totalOrders; }
        public Money getTotalAmount() { return totalAmount; }
        public Money getAverageAmount() { return averageAmount; }
        public Money getMaxAmount() { return maxAmount; }
    }
}