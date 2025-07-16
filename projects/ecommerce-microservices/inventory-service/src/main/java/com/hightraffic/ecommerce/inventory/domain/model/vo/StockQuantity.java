package com.hightraffic.ecommerce.inventory.domain.model.vo;

import java.util.Objects;

/**
 * 재고 수량 Value Object
 * 
 * 특징:
 * - 불변성 보장
 * - 음수 방지를 통한 비즈니스 규칙 보장
 * - 재고 관련 연산 메서드 제공
 * - 타입 안전성 확보
 */
public class StockQuantity {
    
    private final Integer value;
    
    private StockQuantity(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("StockQuantity cannot be null");
        }
        if (value < 0) {
            throw new IllegalArgumentException("StockQuantity cannot be negative: " + value);
        }
        this.value = value;
    }
    
    public static StockQuantity of(Integer value) {
        return new StockQuantity(value);
    }
    
    public static StockQuantity zero() {
        return new StockQuantity(0);
    }
    
    public static StockQuantity of(int value) {
        return new StockQuantity(value);
    }
    
    public static StockQuantity of(java.math.BigDecimal value) {
        Objects.requireNonNull(value, "BigDecimal value cannot be null");
        return new StockQuantity(value.intValue());
    }
    
    public static StockQuantity fromBigDecimal(java.math.BigDecimal value) {
        return of(value);
    }
    
    public Integer getValue() {
        return value;
    }
    
    public int intValue() {
        return value;
    }
    
    public java.math.BigDecimal toBigDecimal() {
        return java.math.BigDecimal.valueOf(value);
    }
    
    /**
     * 재고 수량 추가
     */
    public StockQuantity add(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot add null quantity");
        return new StockQuantity(this.value + other.value);
    }
    
    /**
     * 재고 수량 차감
     */
    public StockQuantity subtract(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot subtract null quantity");
        int result = this.value - other.value;
        if (result < 0) {
            throw new IllegalArgumentException("Subtraction would result in negative quantity: " + result);
        }
        return new StockQuantity(result);
    }
    
    /**
     * 재고 수량 추가 (정수)
     */
    public StockQuantity add(int quantity) {
        return add(StockQuantity.of(quantity));
    }
    
    /**
     * 재고 수량 차감 (정수)
     */
    public StockQuantity subtract(int quantity) {
        return subtract(StockQuantity.of(quantity));
    }
    
    /**
     * 수량 비교
     */
    public boolean isGreaterThan(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot compare with null quantity");
        return this.value > other.value;
    }
    
    public boolean isGreaterThanOrEqual(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot compare with null quantity");
        return this.value >= other.value;
    }
    
    public boolean isLessThan(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot compare with null quantity");
        return this.value < other.value;
    }
    
    public boolean isLessThanOrEqual(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot compare with null quantity");
        return this.value <= other.value;
    }
    
    public boolean isZero() {
        return this.value == 0;
    }
    
    public boolean isPositive() {
        return this.value > 0;
    }
    
    /**
     * 특정 수량이 현재 재고에서 차감 가능한지 확인
     */
    public boolean canSubtract(StockQuantity quantity) {
        Objects.requireNonNull(quantity, "Cannot check subtraction with null quantity");
        return this.value >= quantity.value;
    }
    
    public boolean canSubtract(int quantity) {
        return canSubtract(StockQuantity.of(quantity));
    }
    
    /**
     * 안전한 차감 (음수가 되지 않도록)
     */
    public StockQuantity safeSubtract(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot subtract null quantity");
        int result = Math.max(0, this.value - other.value);
        return new StockQuantity(result);
    }
    
    public StockQuantity safeSubtract(int quantity) {
        return safeSubtract(StockQuantity.of(quantity));
    }
    
    /**
     * 수량 나누기
     */
    public StockQuantity divide(int divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("Divisor must be positive: " + divisor);
        }
        return new StockQuantity(this.value / divisor);
    }
    
    public StockQuantity divide(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot divide by null quantity");
        if (other.value <= 0) {
            throw new IllegalArgumentException("Cannot divide by zero or negative quantity");
        }
        return new StockQuantity(this.value / other.value);
    }
    
    /**
     * 수량 곱하기
     */
    public StockQuantity multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Multiplier cannot be negative: " + multiplier);
        }
        return new StockQuantity(this.value * multiplier);
    }
    
    public StockQuantity multiply(StockQuantity other) {
        Objects.requireNonNull(other, "Cannot multiply by null quantity");
        return new StockQuantity(this.value * other.value);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockQuantity that = (StockQuantity) o;
        return Objects.equals(value, that.value);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    
    @Override
    public String toString() {
        return "StockQuantity{" + value + "}";
    }
}