package com.hightraffic.ecommerce.inventory.domain.model.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * 재고 예약 식별자 Value Object
 * 
 * 특징:
 * - 불변성 보장
 * - UUID 기반 글로벌 고유 식별자
 * - 재고 예약 추적을 위한 도메인 의미 표현
 * - 타입 안전성 확보
 */
public class ReservationId {
    
    private final UUID value;
    
    private ReservationId(UUID value) {
        this.value = Objects.requireNonNull(value, "ReservationId cannot be null");
    }
    
    public static ReservationId of(UUID value) {
        return new ReservationId(value);
    }
    
    public static ReservationId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ReservationId cannot be null or empty");
        }
        
        try {
            return new ReservationId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ReservationId format: " + value, e);
        }
    }
    
    public static ReservationId generate() {
        return new ReservationId(UUID.randomUUID());
    }
    
    public UUID getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReservationId that = (ReservationId) o;
        return Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value.toString();
    }
}