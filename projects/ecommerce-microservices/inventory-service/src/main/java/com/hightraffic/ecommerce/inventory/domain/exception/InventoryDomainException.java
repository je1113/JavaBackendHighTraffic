package com.hightraffic.ecommerce.inventory.domain.exception;

/**
 * 재고 도메인 예외 기본 클래스
 */
public abstract class InventoryDomainException extends RuntimeException {
    
    public InventoryDomainException(String message) {
        super(message);
    }
    
    public InventoryDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}