package com.hightraffic.ecommerce.inventory.domain.model;

import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.exception.ReservationNotFoundException;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Stock 엔티티 테스트")
class StockTest {
    
    private Stock stock;
    
    @BeforeEach
    void setUp() {
        stock = new Stock(StockQuantity.of(100));
    }
    
    @Nested
    @DisplayName("생성자 검증")
    class ConstructorValidation {
        
        @Test
        @DisplayName("정상적인 재고 생성")
        void createStock_WithValidQuantity_Success() {
            // given
            StockQuantity initialQuantity = StockQuantity.of(50);
            
            // when
            Stock newStock = new Stock(initialQuantity);
            
            // then
            assertThat(newStock.getAvailableQuantity()).isEqualTo(initialQuantity);
            assertThat(newStock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(newStock.getTotalQuantity()).isEqualTo(initialQuantity);
            assertThat(newStock.getVersion()).isEqualTo(0L);
            assertThat(newStock.getLastModifiedAt()).isNotNull();
            assertThat(newStock.getReservationCount()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("null 초기 수량으로 생성 시도")
        void createStock_WithNullQuantity_ThrowsException() {
            assertThatThrownBy(() -> new Stock(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Initial quantity cannot be null");
        }
    }
    
    @Nested
    @DisplayName("재고 예약 기능")
    class StockReservationTest {
        
        @Test
        @DisplayName("정상적인 재고 예약")
        void reserveStock_WithSufficientStock_Success() {
            // given
            ReservationId reservationId = ReservationId.generate();
            StockQuantity reserveQuantity = StockQuantity.of(30);
            
            // when
            com.hightraffic.ecommerce.inventory.domain.model.StockReservation reservation = stock.reserveStock(reservationId, reserveQuantity);
            
            // then
            assertThat(reservation).isNotNull();
            assertThat(reservation.getReservationId()).isEqualTo(reservationId);
            assertThat(reservation.getQuantity()).isEqualTo(reserveQuantity);
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(stock.getVersion()).isEqualTo(1L);
        }
        
        @Test
        @DisplayName("재고 부족시 예약 실패")
        void reserveStock_WithInsufficientStock_ThrowsException() {
            // given
            ReservationId reservationId = ReservationId.generate();
            StockQuantity reserveQuantity = StockQuantity.of(150);
            
            // when & then
            assertThatThrownBy(() -> stock.reserveStock(reservationId, reserveQuantity))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock")
                .hasMessageContaining("Available: 100")
                .hasMessageContaining("Required: 150");
                
            // 재고 상태 변경 없음 확인
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getVersion()).isEqualTo(0L);
        }
        
        @Test
        @DisplayName("중복된 예약 ID로 예약 시도")
        void reserveStock_WithDuplicateReservationId_ThrowsException() {
            // given
            ReservationId reservationId = ReservationId.generate();
            stock.reserveStock(reservationId, StockQuantity.of(10));
            
            // when & then
            assertThatThrownBy(() -> stock.reserveStock(reservationId, StockQuantity.of(20)))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Reservation already exists");
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void reserveStock_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> stock.reserveStock(null, StockQuantity.of(10)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reservation ID cannot be null");
                
            assertThatThrownBy(() -> stock.reserveStock(ReservationId.generate(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Quantity cannot be null");
        }
        
        @Test
        @DisplayName("여러 건의 예약 처리")
        void reserveStock_MultipleReservations_Success() {
            // given
            ReservationId id1 = ReservationId.generate();
            ReservationId id2 = ReservationId.generate();
            ReservationId id3 = ReservationId.generate();
            
            // when
            stock.reserveStock(id1, StockQuantity.of(20));
            stock.reserveStock(id2, StockQuantity.of(30));
            stock.reserveStock(id3, StockQuantity.of(15));
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(35));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(65));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(stock.getReservationCount()).isEqualTo(3);
            assertThat(stock.getVersion()).isEqualTo(3L);
        }
    }
    
    @Nested
    @DisplayName("예약 해제 기능")
    class ReservationRelease {
        
        @Test
        @DisplayName("정상적인 예약 해제")
        void releaseReservation_WithValidReservation_Success() {
            // given
            ReservationId reservationId = ReservationId.generate();
            stock.reserveStock(reservationId, StockQuantity.of(30));
            
            // when
            stock.releaseReservation(reservationId);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getReservationCount()).isEqualTo(0);
            assertThat(stock.getVersion()).isEqualTo(2L);
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 해제 시도")
        void releaseReservation_WithNonExistentReservation_ThrowsException() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when & then
            assertThatThrownBy(() -> stock.releaseReservation(nonExistentId))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("Reservation not found");
        }
        
        @Test
        @DisplayName("null 예약 ID로 해제 시도")
        void releaseReservation_WithNullId_ThrowsException() {
            assertThatThrownBy(() -> stock.releaseReservation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reservation ID cannot be null");
        }
    }
    
    @Nested
    @DisplayName("재고 차감 기능")
    class StockDeduction {
        
        @Test
        @DisplayName("예약된 재고 차감")
        void deductStock_WithValidReservation_Success() {
            // given
            ReservationId reservationId = ReservationId.generate();
            stock.reserveStock(reservationId, StockQuantity.of(30));
            
            // when
            stock.deductStock(reservationId);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(stock.getReservationCount()).isEqualTo(0);
            assertThat(stock.getVersion()).isEqualTo(2L);
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 차감 시도")
        void deductStock_WithNonExistentReservation_ThrowsException() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when & then
            assertThatThrownBy(() -> stock.deductStock(nonExistentId))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining("Reservation not found");
        }
        
        @Test
        @DisplayName("직접 재고 차감")
        void deductStockDirectly_WithSufficientStock_Success() {
            // given
            StockQuantity deductQuantity = StockQuantity.of(40);
            
            // when
            stock.deductStockDirectly(deductQuantity);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(60));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(60));
            assertThat(stock.getVersion()).isEqualTo(1L);
        }
        
        @Test
        @DisplayName("재고 부족시 직접 차감 실패")
        void deductStockDirectly_WithInsufficientStock_ThrowsException() {
            // given
            StockQuantity deductQuantity = StockQuantity.of(150);
            
            // when & then
            assertThatThrownBy(() -> stock.deductStockDirectly(deductQuantity))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");
        }
        
        @Test
        @DisplayName("예약된 수량 고려한 직접 차감")
        void deductStockDirectly_ConsideringReservedQuantity_Success() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            
            // when
            stock.deductStockDirectly(StockQuantity.of(50));
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(20));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(50));
        }
    }
    
    @Nested
    @DisplayName("재고 추가 기능")
    class StockAddition {
        
        @Test
        @DisplayName("정상적인 재고 추가")
        void addStock_WithValidQuantity_Success() {
            // given
            StockQuantity addQuantity = StockQuantity.of(50);
            
            // when
            stock.addStock(addQuantity);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(150));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(150));
            assertThat(stock.getVersion()).isEqualTo(1L);
        }
        
        @Test
        @DisplayName("예약이 있는 상태에서 재고 추가")
        void addStock_WithReservations_Success() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            
            // when
            stock.addStock(StockQuantity.of(50));
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(120));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(150));
        }
        
        @Test
        @DisplayName("null 수량으로 추가 시도")
        void addStock_WithNullQuantity_ThrowsException() {
            assertThatThrownBy(() -> stock.addStock(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Quantity cannot be null");
        }
    }
    
    @Nested
    @DisplayName("재고 조정 기능")
    class StockAdjustment {
        
        @Test
        @DisplayName("재고 조정 - 증가")
        void adjustStock_Increase_Success() {
            // given
            StockQuantity newTotal = StockQuantity.of(120);
            
            // when
            stock.adjustStock(newTotal);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(120));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(120));
        }
        
        @Test
        @DisplayName("재고 조정 - 감소")
        void adjustStock_Decrease_Success() {
            // given
            StockQuantity newTotal = StockQuantity.of(80);
            
            // when
            stock.adjustStock(newTotal);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(80));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(80));
        }
        
        @Test
        @DisplayName("예약된 수량보다 적게 조정 시도")
        void adjustStock_BelowReservedQuantity_ThrowsException() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(40));
            StockQuantity newTotal = StockQuantity.of(30);
            
            // when & then
            assertThatThrownBy(() -> stock.adjustStock(newTotal))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot adjust stock below reserved quantity");
        }
        
        @Test
        @DisplayName("예약이 있는 상태에서 재고 조정")
        void adjustStock_WithReservations_Success() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            StockQuantity newTotal = StockQuantity.of(80);
            
            // when
            stock.adjustStock(newTotal);
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(50));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(80));
        }
    }
    
    @Nested
    @DisplayName("예약 조회 기능")
    class ReservationQuery {
        
        @Test
        @DisplayName("특정 예약 조회")
        void getReservation_WithValidId_ReturnsReservation() {
            // given
            ReservationId reservationId = ReservationId.generate();
            stock.reserveStock(reservationId, StockQuantity.of(30));
            
            // when
            com.hightraffic.ecommerce.inventory.domain.model.StockReservation reservation = stock.getReservation(reservationId);
            
            // then
            assertThat(reservation).isNotNull();
            assertThat(reservation.getReservationId()).isEqualTo(reservationId);
            assertThat(reservation.getQuantity()).isEqualTo(StockQuantity.of(30));
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 조회")
        void getReservation_WithNonExistentId_ReturnsNull() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when
            StockReservation reservation = stock.getReservation(nonExistentId);
            
            // then
            assertThat(reservation).isNull();
        }
        
        @Test
        @DisplayName("모든 예약 조회")
        void getAllReservations_ReturnsAllReservations() {
            // given
            ReservationId id1 = ReservationId.generate();
            ReservationId id2 = ReservationId.generate();
            stock.reserveStock(id1, StockQuantity.of(20));
            stock.reserveStock(id2, StockQuantity.of(30));
            
            // when
            Map<ReservationId, com.hightraffic.ecommerce.inventory.domain.model.StockReservation> allReservations = stock.getAllReservations();
            
            // then
            assertThat(allReservations).hasSize(2);
            assertThat(allReservations.containsKey(id1)).isTrue();
            assertThat(allReservations.containsKey(id2)).isTrue();
        }
    }
    
    @Nested
    @DisplayName("재고 상태 확인 기능")
    class StockStatusCheck {
        
        @Test
        @DisplayName("재고 가용성 확인")
        void isStockAvailable_VariousQuantities_ReturnsCorrectResult() {
            // given
            stock = new Stock(StockQuantity.of(50));
            
            // then
            assertThat(stock.isStockAvailable(StockQuantity.of(30))).isTrue();
            assertThat(stock.isStockAvailable(StockQuantity.of(50))).isTrue();
            assertThat(stock.isStockAvailable(StockQuantity.of(51))).isFalse();
        }
        
        @Test
        @DisplayName("재고 부족 확인")
        void isOutOfStock_VariousStates_ReturnsCorrectResult() {
            // when - 초기 상태
            assertThat(stock.isOutOfStock()).isFalse();
            
            // when - 전체 재고 차감
            stock.deductStockDirectly(StockQuantity.of(100));
            assertThat(stock.isOutOfStock()).isTrue();
            
            // when - 재고 추가
            stock.addStock(StockQuantity.of(10));
            assertThat(stock.isOutOfStock()).isFalse();
        }
        
        @Test
        @DisplayName("낮은 재고 확인")
        void isLowStock_WithThreshold_ReturnsCorrectResult() {
            // given
            StockQuantity threshold = StockQuantity.of(20);
            
            // when - 초기 상태 (100)
            assertThat(stock.isLowStock(threshold)).isFalse();
            
            // when - 재고 차감 후 (15)
            stock.deductStockDirectly(StockQuantity.of(85));
            assertThat(stock.isLowStock(threshold)).isTrue();
            
            // when - 임계값과 동일 (20)
            stock.addStock(StockQuantity.of(5));
            assertThat(stock.isLowStock(threshold)).isTrue(); // isLessThanOrEqual
        }
    }
    
    @Nested
    @DisplayName("만료된 예약 처리")
    class ExpiredReservationHandling {
        
        @Test
        @DisplayName("만료되지 않은 예약은 정리되지 않음")
        void cleanupExpiredReservations_DoesNotRemoveValidReservations() {
            // given - 유효한 예약 생성
            ReservationId id1 = ReservationId.generate();
            ReservationId id2 = ReservationId.generate();
            stock.reserveStock(id1, StockQuantity.of(30));
            stock.reserveStock(id2, StockQuantity.of(20));
            
            int beforeCount = stock.getReservationCount();
            StockQuantity beforeAvailable = stock.getAvailableQuantity();
            StockQuantity beforeReserved = stock.getReservedQuantity();
            
            // when
            stock.cleanupExpiredReservations();
            
            // then - 만료되지 않은 예약은 그대로 유지
            assertThat(stock.getReservationCount()).isEqualTo(beforeCount);
            assertThat(stock.getAvailableQuantity()).isEqualTo(beforeAvailable);
            assertThat(stock.getReservedQuantity()).isEqualTo(beforeReserved);
            
            // 예약이 여전히 존재하는지 확인
            assertThat(stock.getReservation(id1)).isNotNull();
            assertThat(stock.getReservation(id2)).isNotNull();
        }
        
        // 만료된 예약 정리 테스트는 StockReservation의 시간 제어가 가능해질 때 추가
        // 현재 구조에서는 LocalDateTime.now()를 직접 사용하므로 테스트 불가능
    }
    
    @Nested
    @DisplayName("Persistence Adapter 호환 메서드")
    class PersistenceAdapterCompatibility {
        
        @Test
        @DisplayName("가용 수량 조정")
        void adjustAvailableQuantity_Success() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            
            // when
            stock.adjustAvailableQuantity(StockQuantity.of(80));
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(80));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(110));
        }
        
        @Test
        @DisplayName("예약 수량 조정")
        void adjustReservedQuantity_Success() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            
            // when
            stock.adjustReservedQuantity(StockQuantity.of(40));
            
            // then
            assertThat(stock.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(stock.getReservedQuantity()).isEqualTo(StockQuantity.of(40));
            assertThat(stock.getTotalQuantity()).isEqualTo(StockQuantity.of(110));
        }
    }
    
    @Nested
    @DisplayName("일반 메서드")
    class GeneralMethods {
        
        @Test
        @DisplayName("equals와 hashCode")
        void equalsAndHashCode_WorkCorrectly() {
            // given
            Stock stock1 = new Stock(StockQuantity.of(100));
            Stock stock2 = new Stock(StockQuantity.of(100));
            Stock stock3 = new Stock(StockQuantity.of(200));
            
            // then
            assertThat(stock1).isEqualTo(stock2);
            assertThat(stock1.hashCode()).isEqualTo(stock2.hashCode());
            assertThat(stock1).isNotEqualTo(stock3);
            
            // when - 상태 변경 후
            stock1.addStock(StockQuantity.of(50));
            assertThat(stock1).isNotEqualTo(stock2);
        }
        
        @Test
        @DisplayName("toString 메서드")
        void toString_ReturnsFormattedString() {
            // given
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(30));
            
            // when
            String result = stock.toString();
            
            // then
            assertThat(result).contains("Stock");
            assertThat(result).contains("available=70");
            assertThat(result).contains("reserved=30");
            assertThat(result).contains("total=100");
            assertThat(result).contains("version=1");
        }
        
        @Test
        @DisplayName("버전 증가 확인")
        void versionIncrement_OnEveryModification() {
            // given
            assertThat(stock.getVersion()).isEqualTo(0L);
            
            // when & then
            stock.reserveStock(ReservationId.generate(), StockQuantity.of(10));
            assertThat(stock.getVersion()).isEqualTo(1L);
            
            stock.addStock(StockQuantity.of(20));
            assertThat(stock.getVersion()).isEqualTo(2L);
            
            stock.adjustStock(StockQuantity.of(150));
            assertThat(stock.getVersion()).isEqualTo(3L);
        }
    }
}