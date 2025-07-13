package com.hightraffic.ecommerce.order.domain.exception;

import com.hightraffic.ecommerce.order.domain.model.ProductId;

/**
 * 중복된 주문 아이템을 추가하려 할 때 발생하는 예외
 */
public class DuplicateOrderItemException extends OrderDomainException {
    
    private static final String ERROR_CODE = "DUPLICATE_ORDER_ITEM";
    
    public DuplicateOrderItemException(ProductId productId) {
        super(ERROR_CODE, String.format("이미 주문에 포함된 상품입니다: %s", productId.getValue()));
    }
    
    public DuplicateOrderItemException(String productId) {
        super(ERROR_CODE, String.format("이미 주문에 포함된 상품입니다: %s", productId));
    }
}