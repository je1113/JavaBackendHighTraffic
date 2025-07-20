package com.hightraffic.ecommerce.inventory.domain.service;

import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

@DisplayName("StockDomainService 테스트")
class StockDomainServiceTest {
    
    private StockDomainService stockDomainService;
    private Product product;
    
    @BeforeEach
    void setUp() {
        stockDomainService = new StockDomainService();
        product = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "테스트 상품", StockQuantity.of(100));
    }
    
    @Nested
    @DisplayName("재고 가용성 검증")
    class StockAvailabilityValidation {
        
        @Test
        @DisplayName("정상적인 재고 가용성 검증")
        void validateStockAvailability_WithSufficientStock_Success() {
            // given
            StockQuantity requestedQuantity = StockQuantity.of(50);
            
            // when & then
            assertThatCode(() -> stockDomainService.validateStockAvailability(product, requestedQuantity))
                .doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("재고 부족시 예외 발생")
        void validateStockAvailability_WithInsufficientStock_ThrowsException() {
            // given
            StockQuantity requestedQuantity = StockQuantity.of(150);
            
            // when & then
            assertThatThrownBy(() -> stockDomainService.validateStockAvailability(product, requestedQuantity))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock")
                .hasMessageContaining("Available: 100")
                .hasMessageContaining("Required: 150");
        }
        
        @Test
        @DisplayName("비활성 상품 검증시 예외 발생")
        void validateStockAvailability_WithInactiveProduct_ThrowsException() {
            // given
            product.deactivate();
            StockQuantity requestedQuantity = StockQuantity.of(10);
            
            // when & then
            assertThatThrownBy(() -> stockDomainService.validateStockAvailability(product, requestedQuantity))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot reserve stock for inactive product");
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void validateStockAvailability_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> stockDomainService.validateStockAvailability(null, StockQuantity.of(10)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Product cannot be null");
                
            assertThatThrownBy(() -> stockDomainService.validateStockAvailability(product, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Requested quantity cannot be null");
        }
    }
    
    @Nested
    @DisplayName("배치 재고 가용성 검증")
    class BatchStockAvailabilityValidation {
        
        @Test
        @DisplayName("정상적인 배치 검증")
        void validateBatchStockAvailability_WithSufficientStock_Success() {
            // given
            Product product1 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", StockQuantity.of(100));
            Product product2 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", StockQuantity.of(200));
            
            List<Product> products = Arrays.asList(product1, product2);
            List<StockQuantity> quantities = Arrays.asList(StockQuantity.of(50), StockQuantity.of(100));
            
            // when & then
            assertThatCode(() -> stockDomainService.validateBatchStockAvailability(products, quantities))
                .doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("하나라도 재고 부족시 예외 발생")
        void validateBatchStockAvailability_WithInsufficientStock_ThrowsException() {
            // given
            Product product1 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", StockQuantity.of(100));
            Product product2 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", StockQuantity.of(50));
            
            List<Product> products = Arrays.asList(product1, product2);
            List<StockQuantity> quantities = Arrays.asList(StockQuantity.of(50), StockQuantity.of(100));
            
            // when & then
            assertThatThrownBy(() -> stockDomainService.validateBatchStockAvailability(products, quantities))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
        }
        
        @Test
        @DisplayName("리스트 크기 불일치시 예외 발생")
        void validateBatchStockAvailability_WithMismatchedSizes_ThrowsException() {
            // given
            Product product1 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", StockQuantity.of(100));
            Product product2 = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", StockQuantity.of(50));
            
            List<Product> products = Arrays.asList(product1, product2);
            List<StockQuantity> quantities = Arrays.asList(StockQuantity.of(50)); // 크기 불일치
            
            // when & then
            assertThatThrownBy(() -> stockDomainService.validateBatchStockAvailability(products, quantities))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Products and quantities lists must have the same size");
        }
    }
    
    @Nested
    @DisplayName("재고 부족 심각도 평가")
    class StockSeverityEvaluation {
        
        @Test
        @DisplayName("재고 충분 상태")
        void evaluateStockSeverity_WithSufficientStock_Returns0() {
            // given
            product = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "테스트 상품", StockQuantity.of(100));
            product.setLowStockThreshold(StockQuantity.of(20));
            
            // when
            int severity = stockDomainService.evaluateStockSeverity(product);
            
            // then
            assertThat(severity).isEqualTo(0);
        }
        
        @Test
        @DisplayName("재고 부족 상태 (임계값 이하)")
        void evaluateStockSeverity_WithLowStock_Returns1() {
            // given
            product = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "테스트 상품", StockQuantity.of(15));
            product.setLowStockThreshold(StockQuantity.of(20));
            
            // when
            int severity = stockDomainService.evaluateStockSeverity(product);
            
            // then
            assertThat(severity).isEqualTo(1);
        }
        
        @Test
        @DisplayName("재고 매우 부족 상태 (임계값의 절반 이하)")
        void evaluateStockSeverity_WithVeryLowStock_Returns2() {
            // given
            product = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "테스트 상품", StockQuantity.of(8));
            product.setLowStockThreshold(StockQuantity.of(20));
            
            // when
            int severity = stockDomainService.evaluateStockSeverity(product);
            
            // then
            assertThat(severity).isEqualTo(2);
        }
        
        @Test
        @DisplayName("품절 상태")
        void evaluateStockSeverity_WithOutOfStock_Returns3() {
            // given
            product = new Product(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "테스트 상품", StockQuantity.zero());
            
            // when
            int severity = stockDomainService.evaluateStockSeverity(product);
            
            // then
            assertThat(severity).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("적정 재고 수량 계산")
    class OptimalStockLevelCalculation {
        
        @Test
        @DisplayName("정상적인 적정 재고 계산")
        void calculateOptimalStockLevel_WithValidParameters_Success() {
            // given
            StockQuantity averageDailySales = StockQuantity.of(10);
            int leadTimeDays = 7;
            double safetyStockMultiplier = 0.5;
            
            // when
            StockQuantity optimal = stockDomainService.calculateOptimalStockLevel(
                averageDailySales, leadTimeDays, safetyStockMultiplier);
            
            // then
            // 기본 재고: 10 * 7 = 70
            // 안전 재고: 70 * 0.5 = 35
            // 적정 재고: 70 + 35 = 105
            assertThat(optimal).isEqualTo(StockQuantity.of(105));
        }
        
        @Test
        @DisplayName("리드타임 0일 계산")
        void calculateOptimalStockLevel_WithZeroLeadTime_Success() {
            // given
            StockQuantity averageDailySales = StockQuantity.of(10);
            int leadTimeDays = 0;
            double safetyStockMultiplier = 1.0;
            
            // when
            StockQuantity optimal = stockDomainService.calculateOptimalStockLevel(
                averageDailySales, leadTimeDays, safetyStockMultiplier);
            
            // then
            assertThat(optimal).isEqualTo(StockQuantity.zero());
        }
        
        @Test
        @DisplayName("유효하지 않은 파라미터")
        void calculateOptimalStockLevel_WithInvalidParameters_ThrowsException() {
            // given
            StockQuantity averageDailySales = StockQuantity.of(10);
            
            // when & then - 음수 리드타임
            assertThatThrownBy(() -> 
                stockDomainService.calculateOptimalStockLevel(averageDailySales, -1, 0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Lead time days cannot be negative");
                
            // when & then - 음수 안전 재고 배수
            assertThatThrownBy(() -> 
                stockDomainService.calculateOptimalStockLevel(averageDailySales, 7, -0.5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Safety stock multiplier cannot be negative");
                
            // when & then - null 일평균 판매량
            assertThatThrownBy(() -> 
                stockDomainService.calculateOptimalStockLevel(null, 7, 0.5))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Average daily sales cannot be null");
        }
    }
    
    @Nested
    @DisplayName("예약 효율성 평가")
    class ReservationEfficiencyEvaluation {
        
        @Test
        @DisplayName("예약이 없는 경우")
        void evaluateReservationEfficiency_WithNoReservations_Returns0() {
            // when
            double efficiency = stockDomainService.evaluateReservationEfficiency(product);
            
            // then
            assertThat(efficiency).isEqualTo(0.0);
        }
        
        @Test
        @DisplayName("적절한 예약이 있는 경우")
        void evaluateReservationEfficiency_WithModerateReservations_ReturnsPositiveValue() {
            // given
            product.reserveStock(StockQuantity.of(30), "ORDER-001");
            
            // when
            double efficiency = stockDomainService.evaluateReservationEfficiency(product);
            
            // then
            // 예약율: 30/100 = 0.3
            // 예약 건수 페널티: 1 * 0.1 = 0.1
            // 효율성: 0.3 - 0.1 = 0.2
            assertThat(efficiency).isCloseTo(0.2, within(0.0001));
        }
        
        @Test
        @DisplayName("많은 예약 건수로 인한 효율성 저하")
        void evaluateReservationEfficiency_WithManyReservations_ReturnsLowerValue() {
            // given
            for (int i = 0; i < 6; i++) {
                product.reserveStock(StockQuantity.of(10), "ORDER-" + i);
            }
            
            // when
            double efficiency = stockDomainService.evaluateReservationEfficiency(product);
            
            // then
            // 예약율: 60/100 = 0.6
            // 예약 건수 페널티: min(6 * 0.1, 0.5) = 0.5
            // 효율성: 0.6 - 0.5 = 0.1
            assertThat(efficiency).isCloseTo(0.1, within(0.0001));
        }
        
        @Test
        @DisplayName("재고가 0인 경우")
        void evaluateReservationEfficiency_WithZeroStock_Returns0() {
            // given
            Product emptyProduct = new Product(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "빈 상품", 
                StockQuantity.zero()
            );
            
            // when
            double efficiency = stockDomainService.evaluateReservationEfficiency(emptyProduct);
            
            // then
            assertThat(efficiency).isEqualTo(0.0);
        }
    }
    
    @Nested
    @DisplayName("재고 조정 안전성 검증")
    class StockAdjustmentSafetyValidation {
        
        @Test
        @DisplayName("안전한 재고 조정")
        void validateStockAdjustmentSafety_WithSafeAdjustment_Success() {
            // given
            product.reserveStock(StockQuantity.of(30), "ORDER-001");
            StockQuantity newTotal = StockQuantity.of(80);
            
            // when & then
            assertThatCode(() -> stockDomainService.validateStockAdjustmentSafety(product, newTotal))
                .doesNotThrowAnyException();
        }
        
        @Test
        @DisplayName("예약 수량보다 적게 조정시 예외 발생")
        void validateStockAdjustmentSafety_BelowReservedQuantity_ThrowsException() {
            // given
            product.reserveStock(StockQuantity.of(40), "ORDER-001");
            StockQuantity newTotal = StockQuantity.of(30);
            
            // when & then
            assertThatThrownBy(() -> stockDomainService.validateStockAdjustmentSafety(product, newTotal))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot adjust stock below reserved quantity")
                .hasMessageContaining("Reserved: 40")
                .hasMessageContaining("New Total: 30");
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void validateStockAdjustmentSafety_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> 
                stockDomainService.validateStockAdjustmentSafety(null, StockQuantity.of(100)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Product cannot be null");
                
            assertThatThrownBy(() -> 
                stockDomainService.validateStockAdjustmentSafety(product, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("New total quantity cannot be null");
        }
    }
    
    @Nested
    @DisplayName("예약 연장 가능성 확인")
    class ReservationExtensionCheck {
        
        @Test
        @DisplayName("유효한 예약 연장 가능")
        void canExtendReservation_WithValidReservation_ReturnsTrue() {
            // given
            ReservationId reservationId = product.reserveStock(StockQuantity.of(30), "ORDER-001");
            
            // when
            boolean canExtend = stockDomainService.canExtendReservation(product, reservationId, 30);
            
            // then
            assertThat(canExtend).isTrue();
        }
        
        @Test
        @DisplayName("최대 연장 시간 초과")
        void canExtendReservation_ExceedingMaxExtension_ReturnsFalse() {
            // given
            ReservationId reservationId = product.reserveStock(StockQuantity.of(30), "ORDER-001");
            
            // when
            boolean canExtend = stockDomainService.canExtendReservation(product, reservationId, 150);
            
            // then
            assertThat(canExtend).isFalse();
        }
        
        @Test
        @DisplayName("존재하지 않는 예약")
        void canExtendReservation_WithNonExistentReservation_ReturnsFalse() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when
            boolean canExtend = stockDomainService.canExtendReservation(product, nonExistentId, 30);
            
            // then
            assertThat(canExtend).isFalse();
        }
    }
    
    @Nested
    @DisplayName("재고 상태 요약 생성")
    class StockStatusSummaryGeneration {
        
        @Test
        @DisplayName("정상적인 재고 상태 요약 생성")
        void generateStockStatusSummary_Success() {
            // given
            product.setLowStockThreshold(StockQuantity.of(20));
            product.reserveStock(StockQuantity.of(30), "ORDER-001");
            
            // when
            StockDomainService.StockStatusSummary summary = stockDomainService.generateStockStatusSummary(product);
            
            // then
            assertThat(summary.getProductId()).isEqualTo(product.getProductId());
            assertThat(summary.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(summary.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(summary.getTotalQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(summary.getReservationCount()).isEqualTo(1);
            assertThat(summary.getStockSeverity()).isEqualTo(0); // 충분
            assertThat(summary.getReservationEfficiency()).isCloseTo(0.2, within(0.0001));
            assertThat(summary.isLowStock()).isFalse();
            assertThat(summary.isOutOfStock()).isFalse();
            assertThat(summary.getGeneratedAt()).isNotNull();
        }
        
        @Test
        @DisplayName("낮은 재고 상태 요약")
        void generateStockStatusSummary_WithLowStock_Success() {
            // given
            Product lowStockProduct = new Product(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "낮은 재고 상품", 
                StockQuantity.of(15)
            );
            lowStockProduct.setLowStockThreshold(StockQuantity.of(20));
            
            // when
            StockDomainService.StockStatusSummary summary = stockDomainService.generateStockStatusSummary(lowStockProduct);
            
            // then
            assertThat(summary.getStockSeverity()).isEqualTo(1); // 부족
            assertThat(summary.isLowStock()).isTrue();
            assertThat(summary.isOutOfStock()).isFalse();
        }
        
        @Test
        @DisplayName("toString 메서드 확인")
        void stockStatusSummary_ToString_ReturnsFormattedString() {
            // given
            StockDomainService.StockStatusSummary summary = stockDomainService.generateStockStatusSummary(product);
            
            // when
            String result = summary.toString();
            
            // then
            assertThat(result).contains("StockStatusSummary");
            assertThat(result).contains("productId=");
            assertThat(result).contains("available=100");
            assertThat(result).contains("reserved=0");
            assertThat(result).contains("severity=0");
        }
    }
    
    @Nested
    @DisplayName("만료된 예약 처리")
    class ExpiredReservationProcessing {
        
        @Test
        @DisplayName("만료된 예약 정리")
        void processExpiredReservations_Success() {
            // given
            product.reserveStock(StockQuantity.of(30), "ORDER-001");
            int initialCount = product.getStock().getReservationCount();
            
            // when
            int processed = stockDomainService.processExpiredReservations(product);
            
            // then
            // 실제 만료 시간이 지나지 않았으므로 처리된 건수는 0
            assertThat(processed).isEqualTo(0);
            assertThat(product.getStock().getReservationCount()).isEqualTo(initialCount);
        }
        
        @Test
        @DisplayName("null 상품 처리")
        void processExpiredReservations_WithNullProduct_ThrowsException() {
            assertThatThrownBy(() -> stockDomainService.processExpiredReservations(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Product cannot be null");
        }
    }
}