package com.hightraffic.ecommerce.inventory.domain.exception;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;

/**
 * 예약을 찾을 수 없는 예외
 */
public class ReservationNotFoundException extends InventoryDomainException {
    
    private final ReservationId reservationId;
    
    public ReservationNotFoundException(String message) {
        super(message);
        this.reservationId = null;
    }
    
    public ReservationNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.reservationId = null;
    }
    
    public ReservationNotFoundException(ReservationId reservationId) {
        super("Reservation not found: " + reservationId);
        this.reservationId = reservationId;
    }
    
    public ReservationNotFoundException(String message, ReservationId reservationId) {
        super(message);
        this.reservationId = reservationId;
    }
    
    public ReservationId getReservationId() {
        return reservationId;
    }
}