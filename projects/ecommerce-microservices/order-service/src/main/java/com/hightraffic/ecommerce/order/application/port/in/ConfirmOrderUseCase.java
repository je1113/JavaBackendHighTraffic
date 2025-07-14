package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 주문 확정 Use Case
 * 
 * 책임:
 * - 주문 확정 요청 처리
 * - 주문 확정 가능 여부 검증
 * - 확정된 주문에 대한 후속 프로세스 트리거
 * 
 * 주문 확정은 주문 생성 후 재고 확인이 완료되고
 * 결제 프로세스를 시작할 준비가 된 상태를 의미
 */
public interface ConfirmOrderUseCase {
    
    /**
     * 주문 확정
     * 
     * @param command 주문 확정 명령
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     * @throws InvalidOrderStateException 확정할 수 없는 상태인 경우
     * @throws OrderAlreadyConfirmedException 이미 확정된 주문인 경우
     */
    void confirmOrder(@Valid ConfirmOrderCommand command);
    
    /**
     * 주문 확정 명령
     * 
     * 주문 ID와 확정 사유를 포함
     */
    class ConfirmOrderCommand {
        
        @NotNull(message = "Order ID is required")
        private final OrderId orderId;
        
        private final String confirmationNote;
        
        public ConfirmOrderCommand(OrderId orderId, String confirmationNote) {
            this.orderId = orderId;
            this.confirmationNote = confirmationNote;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
        
        public String getConfirmationNote() {
            return confirmationNote;
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
     * 잘못된 주문 상태인 경우 발생하는 예외
     */
    class InvalidOrderStateException extends RuntimeException {
        private final OrderId orderId;
        private final String currentState;
        
        public InvalidOrderStateException(OrderId orderId, String currentState) {
            super(String.format("Cannot confirm order %s in state: %s", orderId, currentState));
            this.orderId = orderId;
            this.currentState = currentState;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
        
        public String getCurrentState() {
            return currentState;
        }
    }
    
    /**
     * 이미 확정된 주문인 경우 발생하는 예외
     */
    class OrderAlreadyConfirmedException extends RuntimeException {
        private final OrderId orderId;
        
        public OrderAlreadyConfirmedException(OrderId orderId) {
            super("Order already confirmed: " + orderId);
            this.orderId = orderId;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
    }
}