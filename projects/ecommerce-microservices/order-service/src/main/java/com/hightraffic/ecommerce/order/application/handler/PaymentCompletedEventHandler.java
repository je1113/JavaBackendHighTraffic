package com.hightraffic.ecommerce.order.application.handler;

import com.hightraffic.ecommerce.common.event.order.OrderPaidEvent;
import com.hightraffic.ecommerce.common.event.payment.PaymentCompletedEvent;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 결제 완료 이벤트 핸들러
 * 
 * 책임:
 * - Payment Service로부터 결제 완료 이벤트 수신
 * - 주문 상태를 PAID로 업데이트
 * - 배송 준비 프로세스 트리거
 * 
 * Saga 패턴의 일부로 결제 완료 후 후속 프로세스 진행
 */
@Component
@Transactional
public class PaymentCompletedEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedEventHandler.class);
    
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final PublishEventPort publishEventPort;
    
    public PaymentCompletedEventHandler(LoadOrderPort loadOrderPort,
                                      SaveOrderPort saveOrderPort,
                                      PublishEventPort publishEventPort) {
        this.loadOrderPort = loadOrderPort;
        this.saveOrderPort = saveOrderPort;
        this.publishEventPort = publishEventPort;
    }
    
    /**
     * 결제 완료 이벤트 처리
     * 
     * @param event 결제 완료 이벤트
     */
    public void handle(PaymentCompletedEvent event) {
        log.info("Handling payment completed event for order: {}", event.getOrderId());
        
        try {
            // 1. 주문 조회
            OrderId orderId = OrderId.of(event.getOrderId());
            Order order = loadOrderPort.loadOrder(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
            
            // 2. 주문 상태 검증
            validateOrderStatus(order);
            
            // 3. 결제 정보 저장
            savePaymentInfo(order, event);
            
            // 4. 주문 상태를 PAID로 업데이트
            order.markAsPaid();
            Order paidOrder = saveOrderPort.saveOrder(order);
            
            // 5. OrderPaidEvent 발행 (배송 서비스가 소비)
            publishOrderPaidEvent(paidOrder, event);
            
            log.info("Order marked as paid successfully: {}", orderId);
            
        } catch (Exception e) {
            log.error("Failed to handle payment completed event for order: {}", 
                event.getOrderId(), e);
            handlePaymentCompletionFailure(event);
        }
    }
    
    /**
     * 주문 상태 검증
     */
    private void validateOrderStatus(Order order) {
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && 
            order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            throw new InvalidOrderStateException(
                String.format("Order %s is not in payment pending/processing state. Current state: %s",
                    order.getOrderId(), order.getStatus())
            );
        }
    }
    
    /**
     * 결제 정보 저장
     */
    private void savePaymentInfo(Order order, PaymentCompletedEvent event) {
        String paymentInfo = String.format(
            "Payment completed - Transaction ID: %s, Amount: %s, Method: %s",
            event.getTransactionId(),
            event.getAmount(),
            event.getPaymentMethod()
        );
        order.addNotes(paymentInfo);
    }
    
    /**
     * OrderPaidEvent 발행
     */
    private void publishOrderPaidEvent(Order order, PaymentCompletedEvent paymentEvent) {
        OrderPaidEvent event = new OrderPaidEvent(
            order.getOrderId().getValue().toString(),
            order.getCustomerId().getValue().toString(),
            order.getItems().stream()
                .map(item -> new OrderPaidEvent.OrderItem(
                    item.getProductId().getValue().toString(),
                    item.getProductName(),
                    item.getQuantity()
                ))
                .collect(Collectors.toList()),
            order.getTotalAmount().getAmount(),
            paymentEvent.getTransactionId(),
            LocalDateTime.now()
        );
        
        publishEventPort.publishEvent(event);
    }
    
    /**
     * 결제 완료 처리 실패 핸들링
     */
    private void handlePaymentCompletionFailure(PaymentCompletedEvent event) {
        try {
            OrderId orderId = OrderId.of(event.getOrderId());
            Order order = loadOrderPort.loadOrder(orderId).orElse(null);
            
            if (order != null) {
                // 상태에 따라 다른 처리
                if (order.getStatus() == OrderStatus.PAYMENT_PENDING || 
                    order.getStatus() == OrderStatus.PAYMENT_PROCESSING) {
                    // 재시도 가능한 상태로 유지
                    order.addNotes("Payment completion handling failed. Will retry.");
                    saveOrderPort.saveOrder(order);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle payment completion failure for order: {}", 
                event.getOrderId(), e);
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
    
    /**
     * 잘못된 주문 상태 예외
     */
    private static class InvalidOrderStateException extends RuntimeException {
        public InvalidOrderStateException(String message) {
            super(message);
        }
    }
}