package com.hightraffic.ecommerce.order.domain.exception;

import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;

/**
 * 주문을 찾을 수 없을 때 발생하는 예외
 */
public class OrderNotFoundException extends OrderDomainException {
    
    private static final String ERROR_CODE = "ORDER_NOT_FOUND";
    
    public OrderNotFoundException(OrderId orderId) {
        super(ERROR_CODE, String.format("주문을 찾을 수 없습니다: %s", orderId.getValue()));
    }
    
    public OrderNotFoundException(String orderId) {
        super(ERROR_CODE, String.format("주문을 찾을 수 없습니다: %s", orderId));
    }
}