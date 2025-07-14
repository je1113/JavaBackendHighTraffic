package com.hightraffic.ecommerce.inventory.domain.exception;

/**
 * 상품을 찾을 수 없는 예외
 */
public class ProductNotFoundException extends InventoryDomainException {
    
    public ProductNotFoundException(String message) {
        super(message);
    }
    
    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}