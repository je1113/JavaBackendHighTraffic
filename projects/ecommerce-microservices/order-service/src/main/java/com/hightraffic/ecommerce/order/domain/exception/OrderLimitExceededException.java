package com.hightraffic.ecommerce.order.domain.exception;

/**
 * 주문 제한을 초과했을 때 발생하는 예외
 */
public class OrderLimitExceededException extends OrderDomainException {
    
    private static final String ERROR_CODE = "ORDER_LIMIT_EXCEEDED";
    
    public OrderLimitExceededException(String message) {
        super(ERROR_CODE, message);
    }
    
    public OrderLimitExceededException(int currentCount, int maxLimit) {
        super(ERROR_CODE, String.format("주문 아이템 개수가 제한을 초과했습니다: 현재 %d개, 최대 %d개", 
                currentCount, maxLimit));
    }
}