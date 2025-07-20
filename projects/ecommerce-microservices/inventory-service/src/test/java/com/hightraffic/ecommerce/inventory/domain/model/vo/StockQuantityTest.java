package com.hightraffic.ecommerce.inventory.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StockQuantity Value Object 테스트")
class StockQuantityTest {
    
    @Nested
    @DisplayName("생성 및 팩토리 메서드")
    class Creation {
        
        @Test
        @DisplayName("정수로 생성")
        void of_WithInteger_Success() {
            // when
            StockQuantity quantity = StockQuantity.of(100);
            
            // then
            assertThat(quantity.getValue()).isEqualTo(100);
            assertThat(quantity.intValue()).isEqualTo(100);
        }
        
        @Test
        @DisplayName("Integer 객체로 생성")
        void of_WithIntegerObject_Success() {
            // given
            Integer value = Integer.valueOf(50);
            
            // when
            StockQuantity quantity = StockQuantity.of(value);
            
            // then
            assertThat(quantity.getValue()).isEqualTo(50);
        }
        
        @Test
        @DisplayName("BigDecimal로 생성")
        void of_WithBigDecimal_Success() {
            // given
            BigDecimal value = BigDecimal.valueOf(75.5);
            
            // when
            StockQuantity quantity = StockQuantity.of(value);
            
            // then
            assertThat(quantity.getValue()).isEqualTo(75); // 소수점 버림
        }
        
        @Test
        @DisplayName("fromBigDecimal 메서드")
        void fromBigDecimal_Success() {
            // given
            BigDecimal value = BigDecimal.valueOf(123);
            
            // when
            StockQuantity quantity = StockQuantity.fromBigDecimal(value);
            
            // then
            assertThat(quantity.getValue()).isEqualTo(123);
        }
        
        @Test
        @DisplayName("zero 팩토리 메서드")
        void zero_ReturnsZeroQuantity() {
            // when
            StockQuantity zero = StockQuantity.zero();
            
            // then
            assertThat(zero.getValue()).isEqualTo(0);
            assertThat(zero.isZero()).isTrue();
        }
        
        @Test
        @DisplayName("음수 값으로 생성 시도")
        void of_WithNegativeValue_ThrowsException() {
            assertThatThrownBy(() -> StockQuantity.of(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("StockQuantity cannot be negative");
        }
        
        @Test
        @DisplayName("null 값으로 생성 시도")
        void of_WithNullValue_ThrowsException() {
            assertThatThrownBy(() -> StockQuantity.of((Integer) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("StockQuantity cannot be null");
                
            assertThatThrownBy(() -> StockQuantity.of((BigDecimal) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("BigDecimal value cannot be null");
        }
    }
    
    @Nested
    @DisplayName("연산 메서드")
    class Operations {
        
        @Test
        @DisplayName("더하기 연산")
        void add_WithValidQuantity_Success() {
            // given
            StockQuantity quantity1 = StockQuantity.of(100);
            StockQuantity quantity2 = StockQuantity.of(50);
            
            // when
            StockQuantity result = quantity1.add(quantity2);
            
            // then
            assertThat(result.getValue()).isEqualTo(150);
            // 불변성 확인
            assertThat(quantity1.getValue()).isEqualTo(100);
            assertThat(quantity2.getValue()).isEqualTo(50);
        }
        
        @Test
        @DisplayName("더하기 연산 - 정수")
        void add_WithInteger_Success() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when
            StockQuantity result = quantity.add(30);
            
            // then
            assertThat(result.getValue()).isEqualTo(130);
        }
        
        @Test
        @DisplayName("빼기 연산")
        void subtract_WithValidQuantity_Success() {
            // given
            StockQuantity quantity1 = StockQuantity.of(100);
            StockQuantity quantity2 = StockQuantity.of(30);
            
            // when
            StockQuantity result = quantity1.subtract(quantity2);
            
            // then
            assertThat(result.getValue()).isEqualTo(70);
        }
        
        @Test
        @DisplayName("빼기 연산 - 정수")
        void subtract_WithInteger_Success() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when
            StockQuantity result = quantity.subtract(25);
            
            // then
            assertThat(result.getValue()).isEqualTo(75);
        }
        
        @Test
        @DisplayName("빼기 연산 - 음수 결과 방지")
        void subtract_WithLargerQuantity_ThrowsException() {
            // given
            StockQuantity quantity1 = StockQuantity.of(50);
            StockQuantity quantity2 = StockQuantity.of(100);
            
            // when & then
            assertThatThrownBy(() -> quantity1.subtract(quantity2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subtraction would result in negative quantity");
        }
        
        @Test
        @DisplayName("안전한 빼기 연산")
        void safeSubtract_WithVariousQuantities() {
            // given
            StockQuantity quantity = StockQuantity.of(50);
            
            // when & then - 정상 차감
            assertThat(quantity.safeSubtract(30).getValue()).isEqualTo(20);
            
            // when & then - 0 결과
            assertThat(quantity.safeSubtract(50).getValue()).isEqualTo(0);
            
            // when & then - 음수 방지
            assertThat(quantity.safeSubtract(100).getValue()).isEqualTo(0);
            
            // when & then - 정수 버전
            assertThat(quantity.safeSubtract(70).getValue()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("곱하기 연산")
        void multiply_WithValidMultiplier_Success() {
            // given
            StockQuantity quantity = StockQuantity.of(20);
            
            // when & then - 정수 곱하기
            assertThat(quantity.multiply(3).getValue()).isEqualTo(60);
            
            // when & then - StockQuantity 곱하기
            assertThat(quantity.multiply(StockQuantity.of(4)).getValue()).isEqualTo(80);
        }
        
        @Test
        @DisplayName("곱하기 연산 - 음수 방지")
        void multiply_WithNegativeMultiplier_ThrowsException() {
            // given
            StockQuantity quantity = StockQuantity.of(20);
            
            // when & then
            assertThatThrownBy(() -> quantity.multiply(-2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiplier cannot be negative");
        }
        
        @Test
        @DisplayName("나누기 연산")
        void divide_WithValidDivisor_Success() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when & then - 정수 나누기
            assertThat(quantity.divide(4).getValue()).isEqualTo(25);
            
            // when & then - StockQuantity 나누기
            assertThat(quantity.divide(StockQuantity.of(5)).getValue()).isEqualTo(20);
            
            // when & then - 나머지 버림
            assertThat(quantity.divide(3).getValue()).isEqualTo(33);
        }
        
        @Test
        @DisplayName("나누기 연산 - 0 또는 음수 방지")
        void divide_WithInvalidDivisor_ThrowsException() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when & then - 0으로 나누기
            assertThatThrownBy(() -> quantity.divide(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divisor must be positive");
                
            // when & then - 음수로 나누기
            assertThatThrownBy(() -> quantity.divide(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Divisor must be positive");
                
            // when & then - StockQuantity 0으로 나누기
            assertThatThrownBy(() -> quantity.divide(StockQuantity.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot divide by zero or negative quantity");
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void operations_WithNullParameter_ThrowsException() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when & then
            assertThatThrownBy(() -> quantity.add(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot add null quantity");
                
            assertThatThrownBy(() -> quantity.subtract(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot subtract null quantity");
                
            assertThatThrownBy(() -> quantity.multiply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot multiply by null quantity");
                
            assertThatThrownBy(() -> quantity.divide(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot divide by null quantity");
        }
    }
    
    @Nested
    @DisplayName("비교 메서드")
    class Comparison {
        
        @Test
        @DisplayName("크기 비교 - isGreaterThan")
        void isGreaterThan_VariousQuantities() {
            // given
            StockQuantity quantity50 = StockQuantity.of(50);
            StockQuantity quantity100 = StockQuantity.of(100);
            StockQuantity quantity50_2 = StockQuantity.of(50);
            
            // then
            assertThat(quantity100.isGreaterThan(quantity50)).isTrue();
            assertThat(quantity50.isGreaterThan(quantity100)).isFalse();
            assertThat(quantity50.isGreaterThan(quantity50_2)).isFalse();
        }
        
        @Test
        @DisplayName("크기 비교 - isGreaterThanOrEqual")
        void isGreaterThanOrEqual_VariousQuantities() {
            // given
            StockQuantity quantity50 = StockQuantity.of(50);
            StockQuantity quantity100 = StockQuantity.of(100);
            StockQuantity quantity50_2 = StockQuantity.of(50);
            
            // then
            assertThat(quantity100.isGreaterThanOrEqual(quantity50)).isTrue();
            assertThat(quantity50.isGreaterThanOrEqual(quantity100)).isFalse();
            assertThat(quantity50.isGreaterThanOrEqual(quantity50_2)).isTrue();
        }
        
        @Test
        @DisplayName("크기 비교 - isLessThan")
        void isLessThan_VariousQuantities() {
            // given
            StockQuantity quantity50 = StockQuantity.of(50);
            StockQuantity quantity100 = StockQuantity.of(100);
            StockQuantity quantity50_2 = StockQuantity.of(50);
            
            // then
            assertThat(quantity50.isLessThan(quantity100)).isTrue();
            assertThat(quantity100.isLessThan(quantity50)).isFalse();
            assertThat(quantity50.isLessThan(quantity50_2)).isFalse();
        }
        
        @Test
        @DisplayName("크기 비교 - isLessThanOrEqual")
        void isLessThanOrEqual_VariousQuantities() {
            // given
            StockQuantity quantity50 = StockQuantity.of(50);
            StockQuantity quantity100 = StockQuantity.of(100);
            StockQuantity quantity50_2 = StockQuantity.of(50);
            
            // then
            assertThat(quantity50.isLessThanOrEqual(quantity100)).isTrue();
            assertThat(quantity100.isLessThanOrEqual(quantity50)).isFalse();
            assertThat(quantity50.isLessThanOrEqual(quantity50_2)).isTrue();
        }
        
        @Test
        @DisplayName("비교 메서드 - null 처리")
        void comparison_WithNull_ThrowsException() {
            // given
            StockQuantity quantity = StockQuantity.of(50);
            
            // when & then
            assertThatThrownBy(() -> quantity.isGreaterThan(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot compare with null quantity");
                
            assertThatThrownBy(() -> quantity.isLessThan(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot compare with null quantity");
        }
    }
    
    @Nested
    @DisplayName("상태 확인 메서드")
    class StatusCheck {
        
        @Test
        @DisplayName("영(0) 확인")
        void isZero_VariousQuantities() {
            // given
            StockQuantity zero = StockQuantity.zero();
            StockQuantity nonZero = StockQuantity.of(1);
            
            // then
            assertThat(zero.isZero()).isTrue();
            assertThat(nonZero.isZero()).isFalse();
        }
        
        @Test
        @DisplayName("양수 확인")
        void isPositive_VariousQuantities() {
            // given
            StockQuantity zero = StockQuantity.zero();
            StockQuantity positive = StockQuantity.of(1);
            
            // then
            assertThat(zero.isPositive()).isFalse();
            assertThat(positive.isPositive()).isTrue();
        }
        
        @Test
        @DisplayName("차감 가능 여부 확인")
        void canSubtract_VariousQuantities() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // then
            assertThat(quantity.canSubtract(StockQuantity.of(50))).isTrue();
            assertThat(quantity.canSubtract(StockQuantity.of(100))).isTrue();
            assertThat(quantity.canSubtract(StockQuantity.of(101))).isFalse();
            
            // 정수 버전
            assertThat(quantity.canSubtract(50)).isTrue();
            assertThat(quantity.canSubtract(100)).isTrue();
            assertThat(quantity.canSubtract(101)).isFalse();
        }
        
        @Test
        @DisplayName("canSubtract - null 처리")
        void canSubtract_WithNull_ThrowsException() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when & then
            assertThatThrownBy(() -> quantity.canSubtract(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Cannot check subtraction with null quantity");
        }
    }
    
    @Nested
    @DisplayName("변환 메서드")
    class Conversion {
        
        @Test
        @DisplayName("BigDecimal로 변환")
        void toBigDecimal_Success() {
            // given
            StockQuantity quantity = StockQuantity.of(123);
            
            // when
            BigDecimal result = quantity.toBigDecimal();
            
            // then
            assertThat(result).isEqualTo(BigDecimal.valueOf(123));
        }
    }
    
    @Nested
    @DisplayName("일반 메서드")
    class GeneralMethods {
        
        @Test
        @DisplayName("equals와 hashCode")
        void equalsAndHashCode_WorkCorrectly() {
            // given
            StockQuantity quantity1 = StockQuantity.of(100);
            StockQuantity quantity2 = StockQuantity.of(100);
            StockQuantity quantity3 = StockQuantity.of(200);
            
            // then
            assertThat(quantity1).isEqualTo(quantity2);
            assertThat(quantity1.hashCode()).isEqualTo(quantity2.hashCode());
            assertThat(quantity1).isNotEqualTo(quantity3);
            assertThat(quantity1).isNotEqualTo(null);
            assertThat(quantity1).isNotEqualTo("100");
        }
        
        @Test
        @DisplayName("toString 메서드")
        void toString_ReturnsFormattedString() {
            // given
            StockQuantity quantity = StockQuantity.of(100);
            
            // when
            String result = quantity.toString();
            
            // then
            assertThat(result).isEqualTo("StockQuantity{100}");
        }
    }
    
    @Nested
    @DisplayName("불변성 테스트")
    class Immutability {
        
        @Test
        @DisplayName("모든 연산이 새로운 객체를 반환")
        void allOperations_ReturnNewObject() {
            // given
            StockQuantity original = StockQuantity.of(100);
            
            // when
            StockQuantity added = original.add(50);
            StockQuantity subtracted = original.subtract(30);
            StockQuantity multiplied = original.multiply(2);
            StockQuantity divided = original.divide(4);
            
            // then - 원본은 변경되지 않음
            assertThat(original.getValue()).isEqualTo(100);
            assertThat(added).isNotSameAs(original);
            assertThat(subtracted).isNotSameAs(original);
            assertThat(multiplied).isNotSameAs(original);
            assertThat(divided).isNotSameAs(original);
        }
    }
}