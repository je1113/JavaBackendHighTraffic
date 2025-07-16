package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.common.event.order.OrderCancelledEvent;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 취소 Use Case 구현체
 * 
 * 책임:
 * - 주문 취소 요청 처리
 * - 취소 가능 여부 검증
 * - 보상 트랜잭션 트리거
 * - 도메인 이벤트 발행
 */
@Service
@Transactional
public class CancelOrderService implements CancelOrderUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(CancelOrderService.class);
    
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final PublishEventPort publishEventPort;
    private final OrderBusinessRulesConfig businessRulesConfig;
    
    public CancelOrderService(LoadOrderPort loadOrderPort,
                            SaveOrderPort saveOrderPort,
                            PublishEventPort publishEventPort,
                            OrderBusinessRulesConfig businessRulesConfig) {
        this.loadOrderPort = loadOrderPort;
        this.saveOrderPort = saveOrderPort;
        this.publishEventPort = publishEventPort;
        this.businessRulesConfig = businessRulesConfig;
    }
    
    @Override
    public void cancelOrder(CancelOrderCommand command) {
        log.info("Cancelling order: {} with reason: {}", 
            command.getOrderId(), command.getCancellationReason());
        
        // 1. 주문 조회
        Order order = loadOrderPort.loadOrder(command.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(command.getOrderId()));
        
        // 2. 이미 취소된 주문인지 확인
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(command.getOrderId());
        }
        
        // 3. 취소 가능한 상태인지 검증
        validateCancellationEligibility(order, command.isCustomerInitiated());
        
        // 4. 주문 취소
        order.cancel(command.getCancellationReason());
        
        // 5. 변경사항 저장
        Order cancelledOrder = saveOrderPort.saveOrder(order);
        
        // 6. 도메인 이벤트 생성 및 발행
        OrderCancelledEvent event = createOrderCancelledEvent(
            cancelledOrder, 
            command.getCancellationReason(),
            command.isCustomerInitiated()
        );
        publishEventPort.publishEvent(event);
        
        log.info("Order cancelled successfully: {}", cancelledOrder.getOrderId());
    }
    
    /**
     * 취소 가능 여부 검증
     * 
     * - 고객 주도 취소: 설정된 시간 내에만 가능
     * - 시스템 주도 취소: 언제나 가능 (재고 부족, 결제 실패 등)
     */
    private void validateCancellationEligibility(Order order, boolean isCustomerInitiated) {
        // 취소 불가능한 상태 체크
        if (!order.isCancellable()) {
            throw new OrderNotCancellableException(
                order.getOrderId(),
                order.getStatus().name(),
                "Order cannot be cancelled in current state"
            );
        }
        
        // 고객 주도 취소인 경우 시간 제한 확인
        if (isCustomerInitiated) {
            LocalDateTime cancellationDeadline = order.getCreatedAt()
                .plus(businessRulesConfig.getTime().getOrderCancellationHours(), ChronoUnit.HOURS);
            
            if (LocalDateTime.now().isAfter(cancellationDeadline)) {
                throw new OrderNotCancellableException(
                    order.getOrderId(),
                    order.getStatus().name(),
                    String.format("Cancellation period of %d hours has expired", 
                        businessRulesConfig.getTime().getOrderCancellationHours())
                );
            }
        }
    }
    
    /**
     * OrderCancelledEvent 생성
     * 
     * 이 이벤트는 재고 서비스에서 예약된 재고를 해제하는데 사용됨
     */
    private OrderCancelledEvent createOrderCancelledEvent(Order order, 
                                                         String cancellationReason,
                                                         boolean isCustomerInitiated) {
        return new OrderCancelledEvent(
            order.getOrderId().getValue().toString(),
            order.getCustomerId().getValue().toString(),
            order.getStatus().name(), // previousStatus
            cancellationReason,
            "STANDARD_CANCELLATION", // cancelReasonCode
            isCustomerInitiated ? order.getCustomerId().getValue().toString() : "SYSTEM", // cancelledBy
            isCustomerInitiated ? "CUSTOMER" : "SYSTEM", // cancelledByType
            order.getTotalAmount().getAmount(), // refundAmount
            List.of(new OrderCancelledEvent.CompensationAction(
                "STOCK_RELEASE",
                "inventory-service",
                "Release reserved stock for order " + order.getOrderId().getValue(),
                1
            )), // compensationActions
            "Order cancelled successfully" // cancellationNotes
        );
    }
}