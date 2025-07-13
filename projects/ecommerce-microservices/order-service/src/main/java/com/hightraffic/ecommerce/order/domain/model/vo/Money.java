package com.hightraffic.ecommerce.order.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * 금액 Value Object
 * 통화와 금액을 표현하는 불변 객체
 */
public final class Money implements Serializable, Comparable<Money> {
    
    private static final long serialVersionUID = 1L;
    private static final int SCALE = 2; // 소수점 2자리
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    
    @JsonProperty("amount")
    private final BigDecimal amount;
    
    @JsonProperty("currency")
    private final Currency currency;
    
    /**
     * 0원 생성 (기본 통화: KRW)
     */
    public static Money ZERO() {
        return ZERO("KRW");
    }
    
    /**
     * 특정 통화의 0원 생성
     */
    public static Money ZERO(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }
    
    /**
     * 금액과 통화 코드로 Money 생성
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }
    
    /**
     * 금액과 통화 코드로 Money 생성 (double)
     */
    public static Money of(double amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }
    
    /**
     * 금액과 통화 코드로 Money 생성 (long)
     */
    public static Money of(long amount, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyCode);
    }
    
    @JsonCreator
    public Money(@JsonProperty("amount") BigDecimal amount, 
                 @JsonProperty("currency") Currency currency) {
        validateAmount(amount);
        validateCurrency(currency);
        
        this.amount = amount.setScale(SCALE, ROUNDING_MODE);
        this.currency = currency;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public Currency getCurrency() {
        return currency;
    }
    
    public String getCurrencyCode() {
        return currency.getCurrencyCode();
    }
    
    /**
     * 더하기
     */
    public Money add(Money other) {
        validateSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }
    
    /**
     * 빼기
     */
    public Money subtract(Money other) {
        validateSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }
    
    /**
     * 곱하기
     */
    public Money multiply(BigDecimal factor) {
        return new Money(amount.multiply(factor), currency);
    }
    
    /**
     * 곱하기 (int)
     */
    public Money multiply(int factor) {
        return multiply(BigDecimal.valueOf(factor));
    }
    
    /**
     * 곱하기 (double)
     */
    public Money multiply(double factor) {
        return multiply(BigDecimal.valueOf(factor));
    }
    
    /**
     * 나누기
     */
    public Money divide(BigDecimal divisor) {
        return new Money(amount.divide(divisor, SCALE, ROUNDING_MODE), currency);
    }
    
    /**
     * 나누기 (int)
     */
    public Money divide(int divisor) {
        return divide(BigDecimal.valueOf(divisor));
    }
    
    /**
     * 퍼센트 계산
     */
    public Money percentage(BigDecimal percentage) {
        BigDecimal factor = percentage.divide(BigDecimal.valueOf(100), 4, ROUNDING_MODE);
        return multiply(factor);
    }
    
    /**
     * 음수인지 확인
     */
    public boolean isNegative() {
        return amount.compareTo(BigDecimal.ZERO) < 0;
    }
    
    /**
     * 양수인지 확인
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 0인지 확인
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
    
    /**
     * 다른 Money보다 큰지 확인
     */
    public boolean isGreaterThan(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount) > 0;
    }
    
    /**
     * 다른 Money보다 작은지 확인
     */
    public boolean isLessThan(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount) < 0;
    }
    
    /**
     * 다른 Money보다 크거나 같은지 확인
     */
    public boolean isGreaterThanOrEqual(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount) >= 0;
    }
    
    /**
     * 다른 Money보다 작거나 같은지 확인
     */
    public boolean isLessThanOrEqual(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount) <= 0;
    }
    
    /**
     * 금액 검증
     */
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("금액은 null일 수 없습니다");
        }
    }
    
    /**
     * 통화 검증
     */
    private void validateCurrency(Currency currency) {
        if (currency == null) {
            throw new IllegalArgumentException("통화는 null일 수 없습니다");
        }
    }
    
    /**
     * 같은 통화인지 검증
     */
    private void validateSameCurrency(Money other) {
        if (other == null) {
            throw new IllegalArgumentException("비교 대상 Money는 null일 수 없습니다");
        }
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                String.format("다른 통화끼리는 연산할 수 없습니다: %s != %s", 
                    currency.getCurrencyCode(), other.currency.getCurrencyCode())
            );
        }
    }
    
    @Override
    public int compareTo(Money other) {
        validateSameCurrency(other);
        return amount.compareTo(other.amount);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Money money = (Money) obj;
        return Objects.equals(amount, money.amount) && 
               Objects.equals(currency, money.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }
    
    @Override
    public String toString() {
        return String.format("%s %s", amount.toPlainString(), currency.getCurrencyCode());
    }
    
    /**
     * 포맷팅된 문자열 반환 (통화 기호 포함)
     */
    public String format() {
        return String.format("%s%s", currency.getSymbol(), amount.toPlainString());
    }
}