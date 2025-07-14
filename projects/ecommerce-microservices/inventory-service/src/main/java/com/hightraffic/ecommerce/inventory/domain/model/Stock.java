package com.hightraffic.ecommerce.inventory.domain.model;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.exception.ReservationNotFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 재고 엔티티
 * 
 * 책임:
 * - 사용 가능 수량 관리
 * - 예약 수량 관리
 * - 동시성 제어를 위한 버전 관리
 * - 재고 불변성 보장
 */
public class Stock {
    
    private StockQuantity availableQuantity;
    private StockQuantity reservedQuantity;
    private StockQuantity totalQuantity;
    private final Map<ReservationId, StockReservation> reservations;
    private Long version;
    private LocalDateTime lastModifiedAt;
    
    public Stock(StockQuantity initialQuantity) {
        this.availableQuantity = Objects.requireNonNull(initialQuantity, "Initial quantity cannot be null");
        this.reservedQuantity = StockQuantity.zero();
        this.totalQuantity = initialQuantity;
        this.reservations = new HashMap<>();
        this.version = 0L;
        this.lastModifiedAt = LocalDateTime.now();
        
        validateStockConsistency();
    }
    
    /**
     * 재고 예약
     * 
     * @param reservationId 예약 ID
     * @param quantity 예약할 수량
     * @return 예약 정보
     * @throws InsufficientStockException 재고 부족 시
     * @throws InvalidStockOperationException 이미 존재하는 예약 ID
     */
    public StockReservation reserveStock(ReservationId reservationId, StockQuantity quantity) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        
        if (reservations.containsKey(reservationId)) {
            throw new InvalidStockOperationException("Reservation already exists: " + reservationId);
        }
        
        if (!availableQuantity.canSubtract(quantity)) {
            throw new InsufficientStockException(
                String.format("Insufficient stock. Available: %d, Required: %d", 
                    availableQuantity.getValue(), quantity.getValue())
            );
        }
        
        // 재고 예약 처리
        this.availableQuantity = availableQuantity.subtract(quantity);
        this.reservedQuantity = reservedQuantity.add(quantity);
        
        // 예약 정보 생성
        StockReservation reservation = new StockReservation(reservationId, quantity);
        reservations.put(reservationId, reservation);
        
        updateModificationTime();
        validateStockConsistency();
        
        return reservation;
    }
    
    /**
     * 예약 해제 (재고 복원)
     * 
     * @param reservationId 해제할 예약 ID
     * @throws ReservationNotFoundException 예약이 존재하지 않는 경우
     */
    public void releaseReservation(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        
        StockReservation reservation = reservations.remove(reservationId);
        if (reservation == null) {
            throw new ReservationNotFoundException("Reservation not found: " + reservationId);
        }
        
        // 예약된 수량을 사용 가능 수량으로 복원
        this.availableQuantity = availableQuantity.add(reservation.getQuantity());
        this.reservedQuantity = reservedQuantity.subtract(reservation.getQuantity());
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 재고 차감 (예약을 실제 차감으로 확정)
     * 
     * @param reservationId 차감할 예약 ID
     * @throws ReservationNotFoundException 예약이 존재하지 않는 경우
     */
    public void deductStock(ReservationId reservationId) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        
        StockReservation reservation = reservations.remove(reservationId);
        if (reservation == null) {
            throw new ReservationNotFoundException("Reservation not found: " + reservationId);
        }
        
        // 예약된 수량을 총 재고에서 차감
        this.reservedQuantity = reservedQuantity.subtract(reservation.getQuantity());
        this.totalQuantity = totalQuantity.subtract(reservation.getQuantity());
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 재고 직접 차감 (예약 없이)
     * 
     * @param quantity 차감할 수량
     * @throws InsufficientStockException 재고 부족 시
     */
    public void deductStockDirectly(StockQuantity quantity) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        
        if (!availableQuantity.canSubtract(quantity)) {
            throw new InsufficientStockException(
                String.format("Insufficient stock. Available: %d, Required: %d", 
                    availableQuantity.getValue(), quantity.getValue())
            );
        }
        
        this.availableQuantity = availableQuantity.subtract(quantity);
        this.totalQuantity = totalQuantity.subtract(quantity);
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 재고 추가 (입고 처리)
     * 
     * @param quantity 추가할 수량
     */
    public void addStock(StockQuantity quantity) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        
        this.availableQuantity = availableQuantity.add(quantity);
        this.totalQuantity = totalQuantity.add(quantity);
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 재고 조정 (전체 재고 수량 변경)
     * 
     * @param newTotalQuantity 새로운 총 재고 수량
     * @throws InvalidStockOperationException 예약된 수량보다 적은 경우
     */
    public void adjustStock(StockQuantity newTotalQuantity) {
        Objects.requireNonNull(newTotalQuantity, "New total quantity cannot be null");
        
        if (newTotalQuantity.isLessThan(reservedQuantity)) {
            throw new InvalidStockOperationException(
                String.format("Cannot adjust stock below reserved quantity. Reserved: %d, New Total: %d",
                    reservedQuantity.getValue(), newTotalQuantity.getValue())
            );
        }
        
        this.totalQuantity = newTotalQuantity;
        this.availableQuantity = totalQuantity.subtract(reservedQuantity);
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 특정 예약 정보 조회
     */
    public StockReservation getReservation(ReservationId reservationId) {
        return reservations.get(reservationId);
    }
    
    /**
     * 모든 예약 정보 조회
     */
    public Map<ReservationId, StockReservation> getAllReservations() {
        return new HashMap<>(reservations);
    }
    
    /**
     * 재고 여부 확인
     */
    public boolean isStockAvailable(StockQuantity quantity) {
        return availableQuantity.canSubtract(quantity);
    }
    
    /**
     * 재고 부족 여부 확인
     */
    public boolean isOutOfStock() {
        return availableQuantity.isZero();
    }
    
    /**
     * 낮은 재고 여부 확인
     */
    public boolean isLowStock(StockQuantity threshold) {
        return availableQuantity.isLessThanOrEqual(threshold);
    }
    
    /**
     * 예약 만료 처리
     */
    public void expireReservation(ReservationId reservationId) {
        StockReservation reservation = reservations.get(reservationId);
        if (reservation != null && reservation.isExpired()) {
            releaseReservation(reservationId);
        }
    }
    
    /**
     * 만료된 모든 예약 정리
     */
    public void cleanupExpiredReservations() {
        reservations.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                StockQuantity expiredQuantity = entry.getValue().getQuantity();
                this.availableQuantity = availableQuantity.add(expiredQuantity);
                this.reservedQuantity = reservedQuantity.subtract(expiredQuantity);
                return true;
            }
            return false;
        });
        
        updateModificationTime();
        validateStockConsistency();
    }
    
    /**
     * 재고 일관성 검증
     */
    private void validateStockConsistency() {
        StockQuantity calculatedTotal = availableQuantity.add(reservedQuantity);
        if (!calculatedTotal.equals(totalQuantity)) {
            throw new InvalidStockOperationException(
                String.format("Stock inconsistency detected. Available: %d, Reserved: %d, Total: %d",
                    availableQuantity.getValue(), reservedQuantity.getValue(), totalQuantity.getValue())
            );
        }
    }
    
    private void updateModificationTime() {
        this.lastModifiedAt = LocalDateTime.now();
        this.version++;
    }
    
    // Getters
    public StockQuantity getAvailableQuantity() {
        return availableQuantity;
    }
    
    public StockQuantity getReservedQuantity() {
        return reservedQuantity;
    }
    
    public StockQuantity getTotalQuantity() {
        return totalQuantity;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }
    
    public int getReservationCount() {
        return reservations.size();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stock stock = (Stock) o;
        return Objects.equals(availableQuantity, stock.availableQuantity) &&
               Objects.equals(reservedQuantity, stock.reservedQuantity) &&
               Objects.equals(totalQuantity, stock.totalQuantity) &&
               Objects.equals(version, stock.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(availableQuantity, reservedQuantity, totalQuantity, version);
    }
    
    @Override
    public String toString() {
        return String.format("Stock{available=%d, reserved=%d, total=%d, version=%d}", 
            availableQuantity.getValue(), reservedQuantity.getValue(), 
            totalQuantity.getValue(), version);
    }
}