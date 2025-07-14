package com.hightraffic.ecommerce.order.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 고객 ID Value Object
 * UUID 기반의 불변 식별자
 */
public final class CustomerId implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String value;
    
    
    /**
     * 기존 ID 값으로 CustomerId 생성
     */
    @JsonCreator
    public static CustomerId of(String value) {
        return new CustomerId(value);
    }
    
    private CustomerId(String value) {
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
            throw new IllegalArgumentException("CustomerId는 null이거나 빈 값일 수 없습니다");
        }
        
        // UUID 형식 검증
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("CustomerId는 유효한 UUID 형식이어야 합니다: " + value);
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CustomerId customerId = (CustomerId) obj;
        return Objects.equals(value, customerId.value);
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