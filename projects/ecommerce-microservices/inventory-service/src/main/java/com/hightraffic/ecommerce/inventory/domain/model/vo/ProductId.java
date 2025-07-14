package com.hightraffic.ecommerce.inventory.domain.model.vo;

import java.util.Objects;
import java.util.UUID;

/**
 * 상품 식별자 Value Object
 * 
 * 특징:
 * - 불변성 보장
 * - UUID 기반 글로벌 고유 식별자
 * - 유효성 검증 내장
 * - 도메인 의미 표현
 */
public class ProductId {
    
    private final UUID value;
    
    private ProductId(UUID value) {
        this.value = Objects.requireNonNull(value, "ProductId cannot be null");
    }
    
    public static ProductId of(UUID value) {
        return new ProductId(value);
    }
    
    public static ProductId of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ProductId cannot be null or empty");
        }
        
        try {
            return new ProductId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ProductId format: " + value, e);
        }
    }
    
    
    public UUID getValue() {
        return value;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductId productId = (ProductId) o;
        return Objects.equals(value, productId.value);
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