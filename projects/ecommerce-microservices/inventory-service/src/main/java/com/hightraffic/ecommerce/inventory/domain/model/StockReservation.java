package com.hightraffic.ecommerce.inventory.domain.model;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 재고 예약 정보
 * 
 * 책임:
 * - 예약 ID와 수량 관리
 * - 예약 만료 시간 추적
 * - 예약 상태 관리
 */
public class StockReservation {
    
    private final ReservationId reservationId;
    private final StockQuantity quantity;
    private final LocalDateTime reservedAt;
    private final LocalDateTime expiresAt;
    
    // 예약 유효 시간 (기본 30분)
    private static final int DEFAULT_RESERVATION_MINUTES = 30;
    
    public StockReservation(ReservationId reservationId, StockQuantity quantity) {
        this(reservationId, quantity, DEFAULT_RESERVATION_MINUTES);
    }
    
    public StockReservation(ReservationId reservationId, StockQuantity quantity, int reservationMinutes) {
        this.reservationId = Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
        this.reservedAt = LocalDateTime.now();
        this.expiresAt = reservedAt.plusMinutes(reservationMinutes);
        
        if (quantity.isZero()) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }
    }
    
    /**
     * 예약이 만료되었는지 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 예약 남은 시간 (분)
     */
    public long getRemainingMinutes() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(expiresAt)) {
            return 0;
        }
        
        return java.time.Duration.between(now, expiresAt).toMinutes();
    }
    
    /**
     * 예약이 유효한지 확인
     */
    public boolean isValid() {
        return !isExpired();
    }
    
    // Getters
    public ReservationId getReservationId() {
        return reservationId;
    }
    
    public StockQuantity getQuantity() {
        return quantity;
    }
    
    public LocalDateTime getReservedAt() {
        return reservedAt;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockReservation that = (StockReservation) o;
        return Objects.equals(reservationId, that.reservationId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(reservationId);
    }
    
    @Override
    public String toString() {
        return String.format("StockReservation{id=%s, quantity=%d, reservedAt=%s, expiresAt=%s}", 
            reservationId, quantity.getValue(), reservedAt, expiresAt);
    }
}