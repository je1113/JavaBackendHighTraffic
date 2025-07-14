package com.hightraffic.ecommerce.inventory.domain.exception;

/**
 * 예약을 찾을 수 없는 예외
 */
public class ReservationNotFoundException extends InventoryDomainException {
    
    public ReservationNotFoundException(String message) {
        super(message);
    }
    
    public ReservationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}