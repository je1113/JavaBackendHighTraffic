package com.hightraffic.ecommerce.inventory.domain.exception;

/**
 * 재고 부족 예외
 */
public class InsufficientStockException extends InventoryDomainException {
    
    public InsufficientStockException(String message) {
        super(message);
    }
    
    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
    }
}