package com.hightraffic.ecommerce.order.domain.exception;

import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;

/**
 * 주문 상태가 유효하지 않을 때 발생하는 예외
 */
public class InvalidOrderStateException extends OrderDomainException {
    
    private static final String ERROR_CODE = "INVALID_ORDER_STATE";
    
    public InvalidOrderStateException(String message) {
        super(ERROR_CODE, message);
    }
    
    public InvalidOrderStateException(OrderStatus currentStatus, OrderStatus targetStatus) {
        super(ERROR_CODE, String.format("주문 상태를 %s에서 %s로 변경할 수 없습니다", 
                currentStatus.getDescription(), targetStatus.getDescription()));
    }
    
    public InvalidOrderStateException(OrderStatus currentStatus, String operation) {
        super(ERROR_CODE, String.format("현재 주문 상태(%s)에서는 %s 작업을 수행할 수 없습니다", 
                currentStatus.getDescription(), operation));
    }
}