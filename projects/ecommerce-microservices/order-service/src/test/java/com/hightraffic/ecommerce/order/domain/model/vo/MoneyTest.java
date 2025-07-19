package com.hightraffic.ecommerce.order.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Money Value Object 테스트")
class MoneyTest {
    
    @Nested
    @DisplayName("Money 생성")
    class MoneyCreation {
        
        @Test
        @DisplayName("ZERO 메서드로 0원을 생성할 수 있다")
        void createZeroMoney() {
            Money zero = Money.ZERO();
            
            assertThat(zero.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zero.getCurrencyCode()).isEqualTo("KRW");
            assertThat(zero.isZero()).isTrue();
        }
        
        @Test
        @DisplayName("특정 통화의 0원을 생성할 수 있다")
        void createZeroMoneyWithCurrency() {
            Money zeroUSD = Money.ZERO("USD");
            
            assertThat(zeroUSD.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(zeroUSD.getCurrencyCode()).isEqualTo("USD");
        }
        
        @Test
        @DisplayName("BigDecimal과 통화 코드로 Money를 생성할 수 있다")
        void createMoneyWithBigDecimal() {
            Money money = Money.of(new BigDecimal("12345.67"), "KRW");
            
            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("12345.67"));
            assertThat(money.getCurrencyCode()).isEqualTo("KRW");
        }
        
        @Test
        @DisplayName("double 값으로 Money를 생성할 수 있다")
        void createMoneyWithDouble() {
            Money money = Money.of(12345.67, "KRW");
            
            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("12345.67"));
        }
        
        @Test
        @DisplayName("long 값으로 Money를 생성할 수 있다")
        void createMoneyWithLong() {
            Money money = Money.of(12345L, "KRW");
            
            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("12345"));
        }
        
        @Test
        @DisplayName("null 금액으로 Money를 생성하면 예외가 발생한다")
        void createMoneyWithNullAmount() {
            assertThatThrownBy(() -> new Money(null, Currency.getInstance("KRW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("금액은 null일 수 없습니다");
        }
        
        @Test
        @DisplayName("null 통화로 Money를 생성하면 예외가 발생한다")
        void createMoneyWithNullCurrency() {
            assertThatThrownBy(() -> new Money(BigDecimal.ONE, (Currency) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("통화는 null일 수 없습니다");
        }
        
        @Test
        @DisplayName("유효하지 않은 통화 코드로 Money를 생성하면 예외가 발생한다")
        void createMoneyWithInvalidCurrencyCode() {
            assertThatThrownBy(() -> Money.of(1000, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Money는 소수점 2자리로 반올림된다")
        void moneyScaleAndRounding() {
            Money money = Money.of(new BigDecimal("123.456789"), "KRW");
            
            assertThat(money.getAmount().scale()).isEqualTo(2);
            assertThat(money.getAmount()).isEqualByComparingTo(new BigDecimal("123.46"));
        }
    }
    
    @Nested
    @DisplayName("Money 연산")
    class MoneyOperations {
        
        @Test
        @DisplayName("같은 통화끼리 더할 수 있다")
        void addMoney() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(2000, "KRW");
            
            Money result = money1.add(money2);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("3000"));
            assertThat(result.getCurrencyCode()).isEqualTo("KRW");
        }
        
        @Test
        @DisplayName("같은 통화끼리 뺄 수 있다")
        void subtractMoney() {
            Money money1 = Money.of(3000, "KRW");
            Money money2 = Money.of(1000, "KRW");
            
            Money result = money1.subtract(money2);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("2000"));
        }
        
        @Test
        @DisplayName("음수 결과도 처리할 수 있다")
        void subtractToNegative() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(3000, "KRW");
            
            Money result = money1.subtract(money2);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-2000"));
            assertThat(result.isNegative()).isTrue();
        }
        
        @Test
        @DisplayName("BigDecimal로 곱할 수 있다")
        void multiplyByBigDecimal() {
            Money money = Money.of(1000, "KRW");
            
            Money result = money.multiply(new BigDecimal("2.5"));
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("2500"));
        }
        
        @Test
        @DisplayName("정수로 곱할 수 있다")
        void multiplyByInt() {
            Money money = Money.of(1000, "KRW");
            
            Money result = money.multiply(3);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("3000"));
        }
        
        @Test
        @DisplayName("실수로 곱할 수 있다")
        void multiplyByDouble() {
            Money money = Money.of(1000, "KRW");
            
            Money result = money.multiply(1.5);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1500"));
        }
        
        @Test
        @DisplayName("BigDecimal로 나눌 수 있다")
        void divideByBigDecimal() {
            Money money = Money.of(3000, "KRW");
            
            Money result = money.divide(new BigDecimal("3"));
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        }
        
        @Test
        @DisplayName("정수로 나눌 수 있다")
        void divideByInt() {
            Money money = Money.of(3000, "KRW");
            
            Money result = money.divide(3);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        }
        
        @Test
        @DisplayName("나눗셈 결과는 반올림된다")
        void divisionRounding() {
            Money money = Money.of(1000, "KRW");
            
            Money result = money.divide(3);
            
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("333.33"));
        }
        
        @Test
        @DisplayName("퍼센트를 계산할 수 있다")
        void calculatePercentage() {
            Money money = Money.of(10000, "KRW");
            
            Money tenPercent = money.percentage(new BigDecimal("10"));
            Money fiftyPercent = money.percentage(new BigDecimal("50"));
            Money hundredPercent = money.percentage(new BigDecimal("100"));
            
            assertThat(tenPercent.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
            assertThat(fiftyPercent.getAmount()).isEqualByComparingTo(new BigDecimal("5000"));
            assertThat(hundredPercent.getAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        }
        
        @Test
        @DisplayName("다른 통화끼리 연산하면 예외가 발생한다")
        void operationsWithDifferentCurrencies() {
            Money krw = Money.of(1000, "KRW");
            Money usd = Money.of(10, "USD");
            
            assertThatThrownBy(() -> krw.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 통화끼리는 연산할 수 없습니다");
            
            assertThatThrownBy(() -> krw.subtract(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 통화끼리는 연산할 수 없습니다");
        }
        
        @Test
        @DisplayName("null과 연산하면 예외가 발생한다")
        void operationsWithNull() {
            Money money = Money.of(1000, "KRW");
            
            assertThatThrownBy(() -> money.add(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비교 대상 Money는 null일 수 없습니다");
        }
    }
    
    @Nested
    @DisplayName("Money 비교")
    class MoneyComparison {
        
        @Test
        @DisplayName("음수, 양수, 0을 판별할 수 있다")
        void checkSign() {
            Money positive = Money.of(1000, "KRW");
            Money negative = Money.of(-1000, "KRW");
            Money zero = Money.ZERO();
            
            assertThat(positive.isPositive()).isTrue();
            assertThat(positive.isNegative()).isFalse();
            assertThat(positive.isZero()).isFalse();
            
            assertThat(negative.isPositive()).isFalse();
            assertThat(negative.isNegative()).isTrue();
            assertThat(negative.isZero()).isFalse();
            
            assertThat(zero.isPositive()).isFalse();
            assertThat(zero.isNegative()).isFalse();
            assertThat(zero.isZero()).isTrue();
        }
        
        @Test
        @DisplayName("크기를 비교할 수 있다")
        void compareMoney() {
            Money small = Money.of(1000, "KRW");
            Money large = Money.of(2000, "KRW");
            
            assertThat(small.isLessThan(large)).isTrue();
            assertThat(small.isLessThanOrEqual(large)).isTrue();
            assertThat(small.isGreaterThan(large)).isFalse();
            assertThat(small.isGreaterThanOrEqual(large)).isFalse();
            
            assertThat(large.isGreaterThan(small)).isTrue();
            assertThat(large.isGreaterThanOrEqual(small)).isTrue();
            assertThat(large.isLessThan(small)).isFalse();
            assertThat(large.isLessThanOrEqual(small)).isFalse();
        }
        
        @Test
        @DisplayName("같은 금액끼리 비교할 수 있다")
        void compareEqualMoney() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(1000, "KRW");
            
            assertThat(money1.isLessThanOrEqual(money2)).isTrue();
            assertThat(money1.isGreaterThanOrEqual(money2)).isTrue();
            assertThat(money1.isLessThan(money2)).isFalse();
            assertThat(money1.isGreaterThan(money2)).isFalse();
        }
        
        @Test
        @DisplayName("다른 통화끼리 비교하면 예외가 발생한다")
        void compareDifferentCurrencies() {
            Money krw = Money.of(1000, "KRW");
            Money usd = Money.of(10, "USD");
            
            assertThatThrownBy(() -> krw.isGreaterThan(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("다른 통화끼리는 연산할 수 없습니다");
        }
        
        @Test
        @DisplayName("Comparable 인터페이스를 구현한다")
        void comparable() {
            Money small = Money.of(1000, "KRW");
            Money medium = Money.of(2000, "KRW");
            Money large = Money.of(3000, "KRW");
            
            assertThat(small.compareTo(medium)).isLessThan(0);
            assertThat(medium.compareTo(medium)).isEqualTo(0);
            assertThat(large.compareTo(medium)).isGreaterThan(0);
        }
    }
    
    @Nested
    @DisplayName("Money 동등성")
    class MoneyEquality {
        
        @Test
        @DisplayName("같은 금액과 통화를 가진 Money는 동등하다")
        void equalMoney() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(1000, "KRW");
            
            assertThat(money1).isEqualTo(money2);
            assertThat(money1.hashCode()).isEqualTo(money2.hashCode());
        }
        
        @Test
        @DisplayName("다른 금액을 가진 Money는 동등하지 않다")
        void notEqualByAmount() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(2000, "KRW");
            
            assertThat(money1).isNotEqualTo(money2);
        }
        
        @Test
        @DisplayName("다른 통화를 가진 Money는 동등하지 않다")
        void notEqualByCurrency() {
            Money money1 = Money.of(1000, "KRW");
            Money money2 = Money.of(1000, "USD");
            
            assertThat(money1).isNotEqualTo(money2);
        }
    }
    
    @Nested
    @DisplayName("Money 출력")
    class MoneyDisplay {
        
        @Test
        @DisplayName("toString은 금액과 통화 코드를 포함한다")
        void toStringFormat() {
            Money money = Money.of(12345.67, "KRW");
            
            String str = money.toString();
            
            assertThat(str).contains("12345.67");
            assertThat(str).contains("KRW");
        }
        
        @Test
        @DisplayName("format은 통화 기호와 금액을 포함한다")
        void formatWithCurrencySymbol() {
            Money krw = Money.of(12345, "KRW");
            Money usd = Money.of(123.45, "USD");
            
            String krwFormat = krw.format();
            String usdFormat = usd.format();
            
            assertThat(krwFormat).contains("₩");
            assertThat(krwFormat).contains("12345");
            assertThat(usdFormat).contains("$");
            assertThat(usdFormat).contains("123.45");
        }
    }
}