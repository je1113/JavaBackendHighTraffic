package com.hightraffic.ecommerce.order.application.handler;

import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PaymentProcessingPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 재고 예약 완료 이벤트 핸들러
 * 
 * 책임:
 * - Inventory Service로부터 재고 예약 완료 이벤트 수신
 * - 주문 상태 업데이트
 * - 결제 프로세스 시작
 * 
 * Saga 패턴의 일부로 주문-재고-결제 프로세스를 조율
 */
@Component
@Transactional
public class StockReservedEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(StockReservedEventHandler.class);
    
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final PaymentProcessingPort paymentProcessingPort;
    private final PublishEventPort publishEventPort;
    
    public StockReservedEventHandler(LoadOrderPort loadOrderPort,
                                   SaveOrderPort saveOrderPort,
                                   PaymentProcessingPort paymentProcessingPort,
                                   PublishEventPort publishEventPort) {
        this.loadOrderPort = loadOrderPort;
        this.saveOrderPort = saveOrderPort;
        this.paymentProcessingPort = paymentProcessingPort;
        this.publishEventPort = publishEventPort;
    }
    
    /**
     * 재고 예약 완료 이벤트 처리
     * 
     * @param event 재고 예약 완료 이벤트
     */
    public void handle(StockReservedEvent event) {
        log.info("Handling stock reserved event for order: {}", event.getOrderId());
        
        try {
            // 1. 주문 조회
            OrderId orderId = OrderId.of(UUID.fromString(event.getOrderId()));
            Order order = loadOrderPort.loadOrder(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
            
            // 2. 주문 상태 검증
            if (order.getStatus() != OrderStatus.CONFIRMED) {
                log.warn("Order {} is not in CONFIRMED state. Current state: {}", 
                    orderId, order.getStatus());
                return;
            }
            
            // 3. 재고 예약 정보 저장 (추후 취소 시 필요)
            order.addReservationInfo(event.getReservationId(), event.getProductId());
            
            // 4. 결제 프로세스 시작
            initiatePayment(order);
            
            // 5. 주문 상태 업데이트 (CONFIRMED → PAYMENT_PENDING)
            order.markAsPaymentPending();
            saveOrderPort.saveOrder(order);
            
            log.info("Payment initiated for order: {} after stock reservation", orderId);
            
        } catch (Exception e) {
            log.error("Failed to handle stock reserved event for order: {}", 
                event.getOrderId(), e);
            handleStockReservationFailure(event);
        }
    }
    
    /**
     * 결제 프로세스 시작
     */
    private void initiatePayment(Order order) {
        PaymentProcessingPort.PaymentRequest paymentRequest = 
            new PaymentProcessingPort.PaymentRequest(
                order.getOrderId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                "CREDIT_CARD", // 기본 결제 수단 (실제로는 고객이 선택)
                "Order payment for " + order.getOrderId(),
                null // 실제 결제 상세 정보는 별도 조회
            );
        
        // 비동기 결제 처리 시작
        paymentProcessingPort.processPaymentAsync(paymentRequest)
            .exceptionally(throwable -> {
                log.error("Payment processing failed for order: {}", 
                    order.getOrderId(), throwable);
                handlePaymentInitiationFailure(order);
                return null;
            });
    }
    
    /**
     * 재고 예약 실패 처리
     */
    private void handleStockReservationFailure(StockReservedEvent event) {
        try {
            OrderId orderId = OrderId.of(UUID.fromString(event.getOrderId()));
            Order order = loadOrderPort.loadOrder(orderId).orElse(null);
            
            if (order != null && order.getStatus() == OrderStatus.CONFIRMED) {
                // 주문을 실패 상태로 변경
                order.markAsFailed("Stock reservation handling failed");
                saveOrderPort.saveOrder(order);
            }
        } catch (Exception e) {
            log.error("Failed to handle stock reservation failure for order: {}", 
                event.getOrderId(), e);
        }
    }
    
    /**
     * 결제 시작 실패 처리
     */
    private void handlePaymentInitiationFailure(Order order) {
        try {
            // 재고 예약 해제가 필요함 (보상 트랜잭션)
            // 이는 별도의 보상 이벤트 핸들러에서 처리
            order.markAsFailed("Payment initiation failed");
            saveOrderPort.saveOrder(order);
            
            // TODO: 재고 예약 해제 이벤트 발행
            
        } catch (Exception e) {
            log.error("Failed to handle payment initiation failure for order: {}", 
                order.getOrderId(), e);
        }
    }
    
    /**
     * 주문을 찾을 수 없는 경우 예외
     */
    private static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(OrderId orderId) {
            super("Order not found: " + orderId);
        }
    }
}