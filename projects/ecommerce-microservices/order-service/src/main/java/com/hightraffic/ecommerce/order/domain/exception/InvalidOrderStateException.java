package com.hightraffic.ecommerce.order.domain.exception;

import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;

import java.util.List;

/**
 * 주문 상태가 유효하지 않을 때 발생하는 예외
 */
public class InvalidOrderStateException extends OrderDomainException {
    
    private static final String ERROR_CODE = "INVALID_ORDER_STATE";
    
    private final OrderStatus currentState;
    private final OrderStatus targetState;
    private final List<OrderStatus> allowedTransitions;
    
    public InvalidOrderStateException(String message) {
        super(ERROR_CODE, message);
        this.currentState = null;
        this.targetState = null;
        this.allowedTransitions = null;
    }
    
    public InvalidOrderStateException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(ERROR_CODE, String.format("주문 상태를 %s에서 %s로 변경할 수 없습니다", 
                currentStatus.getDescription(), targetStatus.getDescription()));
        this.currentState = currentStatus;
        this.targetState = targetStatus;
        this.allowedTransitions = getValidTransitions(currentStatus);
    }
    
    public InvalidOrderStateException(OrderStatus currentStatus, String operation) {
        super(ERROR_CODE, String.format("현재 주문 상태(%s)에서는 %s 작업을 수행할 수 없습니다", 
                currentStatus.getDescription(), operation));
        this.currentState = currentStatus;
        this.targetState = null;
        this.allowedTransitions = getValidTransitions(currentStatus);
    }
    
    public OrderStatus getCurrentState() {
        return currentState;
    }
    
    public OrderStatus getTargetState() {
        return targetState;
    }
    
    public List<OrderStatus> getAllowedTransitions() {
        return allowedTransitions;
    }
    
    private List<OrderStatus> getValidTransitions(OrderStatus currentStatus) {
        if (currentStatus == null) {
            return List.of();
        }
        
        return switch (currentStatus) {
            case PENDING -> List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
            case CONFIRMED -> List.of(OrderStatus.PAID, OrderStatus.CANCELLED);
            case PAID -> List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
            case COMPLETED -> List.of(OrderStatus.CANCELLED);
            case CANCELLED -> List.of();
            default -> List.of();
        };
    }
}