package com.hightraffic.ecommerce.inventory.domain.model;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import com.hightraffic.ecommerce.common.event.inventory.LowStockAlertEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockAdjustedEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReleasedEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Product Aggregate 테스트")
class ProductTest {
    
    private ProductId productId;
    private Product product;
    
    @BeforeEach
    void setUp() {
        productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
        product = new Product(productId, "테스트 상품", StockQuantity.of(100));
    }
    
    @Nested
    @DisplayName("생성자 검증")
    class ConstructorValidation {
        
        @Test
        @DisplayName("정상적인 상품 생성")
        void createProduct_WithValidParameters_Success() {
            // given
            String productName = "새로운 상품";
            StockQuantity initialStock = StockQuantity.of(50);
            
            // when
            Product newProduct = new Product(productId, productName, initialStock);
            
            // then
            assertThat(newProduct.getProductId()).isEqualTo(productId);
            assertThat(newProduct.getProductName()).isEqualTo(productName);
            assertThat(newProduct.getTotalQuantity()).isEqualTo(initialStock);
            assertThat(newProduct.getAvailableQuantity()).isEqualTo(initialStock);
            assertThat(newProduct.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            assertThat(newProduct.isActive()).isTrue();
            assertThat(newProduct.getLowStockThreshold()).isEqualTo(StockQuantity.of(10));
            assertThat(newProduct.getVersion()).isEqualTo(0L);
            assertThat(newProduct.getCreatedAt()).isNotNull();
            assertThat(newProduct.getLastModifiedAt()).isNotNull();
        }
        
        @Test
        @DisplayName("필수 매개변수 null 체크")
        void createProduct_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> new Product(null, "상품명", StockQuantity.of(10)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Product ID cannot be null");
                
            assertThatThrownBy(() -> new Product(productId, null, StockQuantity.of(10)))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Product name cannot be null");
                
            assertThatThrownBy(() -> new Product(productId, "상품명", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Initial stock cannot be null");
        }
        
        @Test
        @DisplayName("상품명 유효성 검증")
        void createProduct_WithInvalidProductName_ThrowsException() {
            assertThatThrownBy(() -> new Product(productId, "", StockQuantity.of(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name cannot be null or empty");
                
            assertThatThrownBy(() -> new Product(productId, "   ", StockQuantity.of(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name cannot be null or empty");
                
            String longName = "a".repeat(256);
            assertThatThrownBy(() -> new Product(productId, longName, StockQuantity.of(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product name cannot exceed 255 characters");
        }
    }
    
    @Nested
    @DisplayName("재고 예약 기능")
    class StockReservation {
        
        @Test
        @DisplayName("정상적인 재고 예약")
        void reserveStock_WithSufficientStock_Success() {
            // given
            StockQuantity reserveQuantity = StockQuantity.of(30);
            String orderId = "ORDER-001";
            
            // when
            ReservationId reservationId = product.reserveStock(reserveQuantity, orderId);
            
            // then
            assertThat(reservationId).isNotNull();
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(product.getReservedQuantity()).isEqualTo(StockQuantity.of(30));
            assertThat(product.getTotalQuantity()).isEqualTo(StockQuantity.of(100));
            
            // 도메인 이벤트 검증
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(StockReservedEvent.class);
            
            StockReservedEvent event = (StockReservedEvent) events.get(0);
            assertThat(event.getOrderId()).isEqualTo(orderId);
            assertThat(event.getReservedItems()).hasSize(1);
            assertThat(event.getReservedItems().get(0).getQuantity()).isEqualTo(30);
        }
        
        @Test
        @DisplayName("재고 부족시 예약 실패")
        void reserveStock_WithInsufficientStock_ThrowsException() {
            // given
            StockQuantity reserveQuantity = StockQuantity.of(150);
            String orderId = "ORDER-001";
            
            // when & then
            assertThatThrownBy(() -> product.reserveStock(reserveQuantity, orderId))
                .isInstanceOf(InsufficientStockException.class);
                
            // 재고 상태 변경 없음 확인
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(product.getReservedQuantity()).isEqualTo(StockQuantity.zero());
        }
        
        @Test
        @DisplayName("비활성 상품 예약 시도")
        void reserveStock_WithInactiveProduct_ThrowsException() {
            // given
            product.deactivate();
            StockQuantity reserveQuantity = StockQuantity.of(10);
            String orderId = "ORDER-001";
            
            // when & then
            assertThatThrownBy(() -> product.reserveStock(reserveQuantity, orderId))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot perform stock operations on inactive product");
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void reserveStock_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> product.reserveStock(null, "ORDER-001"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Quantity cannot be null");
                
            assertThatThrownBy(() -> product.reserveStock(StockQuantity.of(10), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Order ID cannot be null");
        }
        
        @Test
        @DisplayName("낮은 재고 임계값 도달시 알림 이벤트 발생")
        void reserveStock_ReachingLowStockThreshold_EmitsLowStockEvent() {
            // given
            product = new Product(productId, "테스트 상품", StockQuantity.of(15));
            product.setLowStockThreshold(StockQuantity.of(10));
            
            // when
            product.reserveStock(StockQuantity.of(6), "ORDER-001");
            
            // then
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(2);
            
            boolean hasLowStockEvent = events.stream()
                .anyMatch(event -> event instanceof LowStockAlertEvent);
            assertThat(hasLowStockEvent).isTrue();
            
            LowStockAlertEvent lowStockEvent = events.stream()
                .filter(event -> event instanceof LowStockAlertEvent)
                .map(event -> (LowStockAlertEvent) event)
                .findFirst()
                .orElseThrow();
                
            assertThat(lowStockEvent.getAlertLevel()).isEqualTo("WARNING");
            assertThat(lowStockEvent.getAlertType()).isEqualTo("BELOW_MINIMUM");
        }
    }
    
    @Nested
    @DisplayName("예약 해제 기능")
    class ReservationRelease {
        
        @Test
        @DisplayName("정상적인 예약 해제")
        void releaseReservation_WithValidReservation_Success() {
            // given
            ReservationId reservationId = product.reserveStock(StockQuantity.of(30), "ORDER-001");
            product.pullDomainEvents(); // 기존 이벤트 정리
            
            // when
            product.releaseReservation(reservationId, "ORDER-001");
            
            // then
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(100));
            assertThat(product.getReservedQuantity()).isEqualTo(StockQuantity.zero());
            
            // 도메인 이벤트 검증
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(StockReleasedEvent.class);
            
            StockReleasedEvent event = (StockReleasedEvent) events.get(0);
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getReleaseReason()).isEqualTo("ORDER_CANCELLED");
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 해제 시도")
        void releaseReservation_WithNonExistentReservation_NoEffect() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when
            product.releaseReservation(nonExistentId, "ORDER-001");
            
            // then
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(100));
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).isEmpty();
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void releaseReservation_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> product.releaseReservation(null, "ORDER-001"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reservation ID cannot be null");
                
            assertThatThrownBy(() -> product.releaseReservation(ReservationId.generate(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Order ID cannot be null");
        }
    }
    
    @Nested
    @DisplayName("재고 차감 기능")
    class StockDeduction {
        
        @Test
        @DisplayName("예약된 재고 차감")
        void deductStock_WithValidReservation_Success() {
            // given
            ReservationId reservationId = product.reserveStock(StockQuantity.of(30), "ORDER-001");
            product.pullDomainEvents(); // 기존 이벤트 정리
            
            // when
            product.deductStock(reservationId, "ORDER-001");
            
            // then
            assertThat(product.getTotalQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(product.getReservedQuantity()).isEqualTo(StockQuantity.zero());
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 차감 시도")
        void deductStock_WithNonExistentReservation_ThrowsException() {
            // given
            ReservationId nonExistentId = ReservationId.generate();
            
            // when & then
            assertThatThrownBy(() -> product.deductStock(nonExistentId, "ORDER-001"))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot deduct: reservation not found");
        }
        
        @Test
        @DisplayName("직접 재고 차감")
        void deductStockDirectly_WithSufficientStock_Success() {
            // given
            StockQuantity deductQuantity = StockQuantity.of(40);
            String reason = "직접 판매";
            
            // when
            product.deductStockDirectly(deductQuantity, reason);
            
            // then
            assertThat(product.getTotalQuantity()).isEqualTo(StockQuantity.of(60));
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(60));
        }
        
        @Test
        @DisplayName("재고 부족시 직접 차감 실패")
        void deductStockDirectly_WithInsufficientStock_ThrowsException() {
            // given
            StockQuantity deductQuantity = StockQuantity.of(150);
            String reason = "직접 판매";
            
            // when & then
            assertThatThrownBy(() -> product.deductStockDirectly(deductQuantity, reason))
                .isInstanceOf(InsufficientStockException.class);
        }
        
        @Test
        @DisplayName("비활성 상품 직접 차감 시도")
        void deductStockDirectly_WithInactiveProduct_ThrowsException() {
            // given
            product.deactivate();
            
            // when & then
            assertThatThrownBy(() -> product.deductStockDirectly(StockQuantity.of(10), "직접 판매"))
                .isInstanceOf(InvalidStockOperationException.class)
                .hasMessageContaining("Cannot perform stock operations on inactive product");
        }
    }
    
    @Nested
    @DisplayName("재고 추가 및 조정 기능")
    class StockAdditionAndAdjustment {
        
        @Test
        @DisplayName("재고 추가")
        void addStock_WithValidQuantity_Success() {
            // given
            StockQuantity addQuantity = StockQuantity.of(50);
            String reason = "입고";
            
            // when
            product.addStock(addQuantity, reason);
            
            // then
            assertThat(product.getTotalQuantity()).isEqualTo(StockQuantity.of(150));
            assertThat(product.getAvailableQuantity()).isEqualTo(StockQuantity.of(150));
            
            // 도메인 이벤트 검증
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(StockAdjustedEvent.class);
            
            StockAdjustedEvent event = (StockAdjustedEvent) events.get(0);
            assertThat(event.getAdjustmentType()).isEqualTo("INBOUND");
            assertThat(event.getAdjustmentReason()).isEqualTo(reason);
        }
        
        @Test
        @DisplayName("재고 조정 - 증가")
        void adjustStock_Increase_Success() {
            // given
            StockQuantity newTotal = StockQuantity.of(120);
            String reason = "재고 실사 조정";
            
            // when
            product.adjustStock(newTotal, reason);
            
            // then
            assertThat(product.getTotalQuantity()).isEqualTo(newTotal);
            assertThat(product.getAvailableQuantity()).isEqualTo(newTotal);
            
            // 도메인 이벤트 검증
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(StockAdjustedEvent.class);
        }
        
        @Test
        @DisplayName("재고 조정 - 감소 (예약된 수량보다 적게)")
        void adjustStock_DecreaseBelowReserved_ThrowsException() {
            // given
            product.reserveStock(StockQuantity.of(40), "ORDER-001");
            product.pullDomainEvents(); // 기존 이벤트 정리
            
            StockQuantity newTotal = StockQuantity.of(30); // 예약된 40보다 적음
            
            // when & then
            assertThatThrownBy(() -> product.adjustStock(newTotal, "재고 조정"))
                .isInstanceOf(InvalidStockOperationException.class);
        }
        
        @Test
        @DisplayName("null 파라미터 처리")
        void addAndAdjustStock_WithNullParameters_ThrowsException() {
            assertThatThrownBy(() -> product.addStock(null, "입고"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Quantity cannot be null");
                
            assertThatThrownBy(() -> product.addStock(StockQuantity.of(10), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reason cannot be null");
                
            assertThatThrownBy(() -> product.adjustStock(null, "조정"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("New total quantity cannot be null");
                
            assertThatThrownBy(() -> product.adjustStock(StockQuantity.of(100), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Reason cannot be null");
        }
    }
    
    @Nested
    @DisplayName("상품 활성화/비활성화")
    class ProductActivation {
        
        @Test
        @DisplayName("상품 비활성화")
        void deactivate_Success() throws InterruptedException {
            // given
            LocalDateTime beforeModified = product.getLastModifiedAt();
            Thread.sleep(1); // 최소한의 시간 지연
            
            // when
            product.deactivate();
            
            // then
            assertThat(product.isActive()).isFalse();
            assertThat(product.getLastModifiedAt()).isAfter(beforeModified);
            assertThat(product.getVersion()).isEqualTo(1L);
        }
        
        @Test
        @DisplayName("상품 재활성화")
        void activate_Success() throws InterruptedException {
            // given
            product.deactivate();
            LocalDateTime beforeModified = product.getLastModifiedAt();
            Thread.sleep(1); // 최소한의 시간 지연
            
            // when
            product.activate();
            
            // then
            assertThat(product.isActive()).isTrue();
            assertThat(product.getLastModifiedAt()).isAfter(beforeModified);
            assertThat(product.getVersion()).isEqualTo(2L);
        }
    }
    
    @Nested
    @DisplayName("낮은 재고 임계값 관리")
    class LowStockThreshold {
        
        @Test
        @DisplayName("임계값 설정")
        void setLowStockThreshold_Success() {
            // given
            StockQuantity newThreshold = StockQuantity.of(20);
            
            // when
            product.setLowStockThreshold(newThreshold);
            
            // then
            assertThat(product.getLowStockThreshold()).isEqualTo(newThreshold);
        }
        
        @Test
        @DisplayName("임계값 설정시 낮은 재고 감지")
        void setLowStockThreshold_DetectsLowStock_EmitsEvent() {
            // given
            product = new Product(productId, "테스트 상품", StockQuantity.of(15));
            
            // when
            product.setLowStockThreshold(StockQuantity.of(20));
            
            // then
            assertThat(product.isLowStock()).isTrue();
            
            List<DomainEvent> events = product.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(LowStockAlertEvent.class);
        }
        
        @Test
        @DisplayName("null 임계값 처리")
        void setLowStockThreshold_WithNull_ThrowsException() {
            assertThatThrownBy(() -> product.setLowStockThreshold(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("Threshold cannot be null");
        }
    }
    
    @Nested
    @DisplayName("기타 기능")
    class OtherFeatures {
        
        @Test
        @DisplayName("상품명 변경")
        void updateProductName_Success() throws InterruptedException {
            // given
            String newName = "변경된 상품명";
            LocalDateTime beforeModified = product.getLastModifiedAt();
            Thread.sleep(1); // 최소한의 시간 지연
            
            // when
            product.updateProductName(newName);
            
            // then
            assertThat(product.getProductName()).isEqualTo(newName);
            assertThat(product.getLastModifiedAt()).isAfter(beforeModified);
            assertThat(product.getVersion()).isEqualTo(1L);
        }
        
        @Test
        @DisplayName("재고 상태 확인 메서드")
        void stockStatusMethods_WorkCorrectly() {
            // given
            product = new Product(productId, "테스트 상품", StockQuantity.of(15));
            product.setLowStockThreshold(StockQuantity.of(20));
            
            // then
            assertThat(product.isStockAvailable(StockQuantity.of(10))).isTrue();
            assertThat(product.isStockAvailable(StockQuantity.of(20))).isFalse();
            assertThat(product.isOutOfStock()).isFalse();
            assertThat(product.isLowStock()).isTrue();
            
            // when
            product.deductStockDirectly(StockQuantity.of(15), "전량 판매");
            
            // then
            assertThat(product.isOutOfStock()).isTrue();
        }
        
        @Test
        @DisplayName("equals와 hashCode")
        void equalsAndHashCode_WorkCorrectly() {
            // given
            Product sameProduct = new Product(productId, "다른 이름", StockQuantity.of(200));
            Product differentProduct = new Product(
                ProductId.of("550e8400-e29b-41d4-a716-446655440002"), 
                "테스트 상품", 
                StockQuantity.of(100)
            );
            
            // then
            assertThat(product).isEqualTo(sameProduct);
            assertThat(product.hashCode()).isEqualTo(sameProduct.hashCode());
            assertThat(product).isNotEqualTo(differentProduct);
        }
        
        @Test
        @DisplayName("toString 메서드")
        void toString_ReturnsFormattedString() {
            // when
            String result = product.toString();
            
            // then
            assertThat(result).contains("Product");
            assertThat(result).contains(productId.toString());
            assertThat(result).contains("테스트 상품");
            assertThat(result).contains("available=100");
            assertThat(result).contains("reserved=0");
            assertThat(result).contains("total=100");
            assertThat(result).contains("active=true");
        }
        
        @Test
        @DisplayName("도메인 이벤트 관리")
        void domainEvents_AreProperlyManaged() {
            // given
            product.reserveStock(StockQuantity.of(10), "ORDER-001");
            product.addStock(StockQuantity.of(20), "입고");
            
            // when - 첫 번째 pull
            List<DomainEvent> firstPull = product.pullDomainEvents();
            
            // then
            assertThat(firstPull).hasSize(2);
            assertThat(firstPull.get(0)).isInstanceOf(StockReservedEvent.class);
            assertThat(firstPull.get(1)).isInstanceOf(StockAdjustedEvent.class);
            
            // when - 두 번째 pull
            List<DomainEvent> secondPull = product.pullDomainEvents();
            
            // then - 이벤트가 정리되어 비어있음
            assertThat(secondPull).isEmpty();
        }
    }
}