package com.hightraffic.ecommerce.order.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 ID Value Object
 * UUID 기반의 불변 식별자
 */
public final class OrderId implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String value;
    
    /**
     * 새로운 OrderId 생성
     */
    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }
    
    /**
     * 기존 ID 값으로 OrderId 생성
     */
    @JsonCreator
    public static OrderId of(String value) {
        return new OrderId(value);
    }
    
    private OrderId(String value) {
        validateId(value);
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;
    }
    
    /**
     * ID 형식 검증
     */
    private void validateId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("OrderId는 null이거나 빈 값일 수 없습니다");
        }
        
        // UUID 형식 검증
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("OrderId는 유효한 UUID 형식이어야 합니다: " + value);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OrderId orderId = (OrderId) obj;
        return Objects.equals(value, orderId.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return value;
    }
}