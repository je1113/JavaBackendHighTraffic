package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 주문 취소 Use Case
 * 
 * 책임:
 * - 주문 취소 요청 처리
 * - 취소 가능 여부 검증
 * - 취소에 따른 보상 트랜잭션 트리거
 * 
 * 주문 취소는 고객의 요청 또는 시스템의 판단에 의해
 * 주문을 무효화하고 관련 리소스를 해제하는 과정
 */
public interface CancelOrderUseCase {
    
    /**
     * 주문 취소
     * 
     * @param command 주문 취소 명령
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws OrderNotCancellableException 취소할 수 없는 상태인 경우
     * @throws OrderAlreadyCancelledException 이미 취소된 주문인 경우
     */
    void cancelOrder(@Valid CancelOrderCommand command);
    
    /**
     * 주문 취소 명령
     * 
     * 취소 사유는 필수이며, 고객 서비스 및 분석을 위해 사용
     */
    class CancelOrderCommand {
        
        @NotNull(message = "Order ID is required")
        private final OrderId orderId;
        
        @NotBlank(message = "Cancellation reason is required")
        private final String cancellationReason;
        
        private final boolean isCustomerInitiated;
        
        public CancelOrderCommand(OrderId orderId, String cancellationReason, boolean isCustomerInitiated) {
            this.orderId = orderId;
            this.cancellationReason = cancellationReason;
            this.isCustomerInitiated = isCustomerInitiated;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
        
        public String getCancellationReason() {
            return cancellationReason;
        }
        
        public boolean isCustomerInitiated() {
            return isCustomerInitiated;
        }
    }
    
    /**
     * 주문을 찾을 수 없는 경우 발생하는 예외
     */
    class OrderNotFoundException extends RuntimeException {
        private final OrderId orderId;
        
        public OrderNotFoundException(OrderId orderId) {
            super("Order not found: " + orderId);
            this.orderId = orderId;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
    }
    
    /**
     * 취소할 수 없는 주문 상태인 경우 발생하는 예외
     */
    class OrderNotCancellableException extends RuntimeException {
        private final OrderId orderId;
        private final String currentState;
        private final String reason;
        
        public OrderNotCancellableException(OrderId orderId, String currentState, String reason) {
            super(String.format("Cannot cancel order %s in state %s: %s", orderId, currentState, reason));
            this.orderId = orderId;
            this.currentState = currentState;
            this.reason = reason;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
        
        public String getCurrentState() {
            return currentState;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * 이미 취소된 주문인 경우 발생하는 예외
     */
    class OrderAlreadyCancelledException extends RuntimeException {
        private final OrderId orderId;
        
        public OrderAlreadyCancelledException(OrderId orderId) {
            super("Order already cancelled: " + orderId);
            this.orderId = orderId;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
    }
}