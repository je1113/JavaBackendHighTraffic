package com.hightraffic.ecommerce.inventory.domain.exception;

/**
 * 잘못된 재고 작업 예외
 */
public class InvalidStockOperationException extends InventoryDomainException {
    
    public InvalidStockOperationException(String message) {
        super(message);
    }
    
    public InvalidStockOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}