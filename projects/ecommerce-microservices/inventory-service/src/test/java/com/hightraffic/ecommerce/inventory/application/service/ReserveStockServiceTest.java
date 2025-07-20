package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import com.hightraffic.ecommerce.common.event.inventory.InsufficientStockEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase.*;
import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.application.port.out.SaveProductPort;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.service.StockDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReserveStockService 테스트")
class ReserveStockServiceTest {
    
    @Mock
    private LoadProductPort loadProductPort;
    
    @Mock
    private SaveProductPort saveProductPort;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    @Mock
    private DistributedLockPort distributedLockPort;
    
    @Mock
    private StockDomainService stockDomainService;
    
    @InjectMocks
    private ReserveStockService reserveStockService;
    
    private ProductId productId;
    private Product product;
    private ReserveStockCommand validCommand;
    
    @BeforeEach
    void setUp() {
        productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
        product = new Product(productId, "테스트 상품", StockQuantity.of(100));
        
        validCommand = new ReserveStockCommand(
            productId,
            StockQuantity.of(10),
            "ORDER-001",
            30
        );
    }
    
    @Nested
    @DisplayName("단일 재고 예약")
    class SingleStockReservation {
        
        @Test
        @DisplayName("정상적인 재고 예약 성공")
        void reserveStock_WithValidCommand_Success() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            // 분산 락 동작 설정
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            ReservationResult result = reserveStockService.reserveStock(validCommand);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProductId()).isEqualTo(productId);
            assertThat(result.getReservationId()).isNotNull();
            
            // 이벤트 발행 검증
            verify(publishEventPort).publishEvents(anyList());
            verify(publishEventPort).publishEvent(any(StockReservedEvent.class));
            verify(stockDomainService).validateStockAvailability(product, validCommand.getQuantity());
        }
        
        @Test
        @DisplayName("존재하지 않는 상품으로 예약 시도")
        void reserveStock_WithNonExistentProduct_ThrowsException() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.empty());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> reserveStockService.reserveStock(validCommand))
                .isInstanceOf(ReserveStockUseCase.ProductNotFoundException.class);
                
            verify(publishEventPort, never()).publishEvent(any(StockReservedEvent.class));
        }
        
        @Test
        @DisplayName("재고 부족으로 예약 실패")
        void reserveStock_WithInsufficientStock_PublishesInsufficientStockEvent() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            doThrow(new InsufficientStockException("재고 부족"))
                .when(stockDomainService).validateStockAvailability(product, validCommand.getQuantity());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> reserveStockService.reserveStock(validCommand))
                .isInstanceOf(InsufficientStockException.class);
                
            // 재고 부족 이벤트 발행 검증
            ArgumentCaptor<InsufficientStockEvent> eventCaptor = ArgumentCaptor.forClass(InsufficientStockEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            InsufficientStockEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getProductId()).isEqualTo(productId.getValue().toString());
        }
        
        @Test
        @DisplayName("분산 락 획득 실패")
        void reserveStock_FailedToAcquireLock_ThrowsException() throws Exception {
            // given
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenThrow(new DistributedLockPort.LockAcquisitionException("lockKey", "Failed to acquire lock"));
            
            // when & then
            assertThatThrownBy(() -> reserveStockService.reserveStock(validCommand))
                .hasMessageContaining("System is busy");
                
            verify(loadProductPort, never()).loadProduct(any());
        }
        
        @Test
        @DisplayName("비활성 상품 예약 시도")
        void reserveStock_WithInactiveProduct_ThrowsException() throws Exception {
            // given
            product.deactivate();
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            doThrow(new InvalidStockOperationException("비활성 상품"))
                .when(stockDomainService).validateStockAvailability(product, validCommand.getQuantity());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> reserveStockService.reserveStock(validCommand))
                .isInstanceOf(InvalidStockOperationException.class);
        }
    }
    
    @Nested
    @DisplayName("배치 재고 예약")
    class BatchStockReservation {
        
        @Test
        @DisplayName("모든 상품 예약 성공")
        void reserveBatchStock_AllSuccess() throws Exception {
            // given
            ProductId productId2 = ProductId.of("550e8400-e29b-41d4-a716-446655440002");
            Product product2 = new Product(productId2, "테스트 상품2", StockQuantity.of(200));
            
            List<ReserveBatchStockCommand.StockItem> items = Arrays.asList(
                new ReserveBatchStockCommand.StockItem(productId, StockQuantity.of(10)),
                new ReserveBatchStockCommand.StockItem(productId2, StockQuantity.of(20))
            );
            
            ReserveBatchStockCommand command = new ReserveBatchStockCommand(items, "ORDER-001", false);
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(loadProductPort.loadProduct(productId2)).thenReturn(Optional.of(product2));
            when(saveProductPort.saveProduct(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            List<ReservationResult> results = reserveStockService.reserveBatchStock(command);
            
            // then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(ReservationResult::isSuccess);
            verify(publishEventPort, times(2)).publishEvent(any(StockReservedEvent.class));
        }
        
        @Test
        @DisplayName("원자적 예약 - 하나 실패시 전체 롤백")
        void reserveBatchStock_AtomicReservation_RollbackOnFailure() throws Exception {
            // given
            ProductId productId2 = ProductId.of("550e8400-e29b-41d4-a716-446655440002");
            
            List<ReserveBatchStockCommand.StockItem> items = Arrays.asList(
                new ReserveBatchStockCommand.StockItem(productId, StockQuantity.of(10)),
                new ReserveBatchStockCommand.StockItem(productId2, StockQuantity.of(20))
            );
            
            ReserveBatchStockCommand command = new ReserveBatchStockCommand(items, "ORDER-001", true);
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(loadProductPort.loadProduct(productId2)).thenReturn(Optional.empty()); // 두 번째 상품 없음
            when(saveProductPort.saveProduct(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> reserveStockService.reserveBatchStock(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Batch reservation failed")
                .hasCauseInstanceOf(ReserveStockUseCase.ProductNotFoundException.class);
                
            // 롤백 로직이 실행되었는지 간접적으로 확인
            // 실제로는 RestoreStockService를 통해 처리되어야 함
        }
        
        @Test
        @DisplayName("부분 성공 허용 모드")
        void reserveBatchStock_PartialSuccess() throws Exception {
            // given
            ProductId productId2 = ProductId.of("550e8400-e29b-41d4-a716-446655440002");
            Product product2 = new Product(productId2, "테스트 상품2", StockQuantity.of(5)); // 재고 부족
            
            List<ReserveBatchStockCommand.StockItem> items = Arrays.asList(
                new ReserveBatchStockCommand.StockItem(productId, StockQuantity.of(10)),
                new ReserveBatchStockCommand.StockItem(productId2, StockQuantity.of(20))
            );
            
            ReserveBatchStockCommand command = new ReserveBatchStockCommand(items, "ORDER-001", false);
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(loadProductPort.loadProduct(productId2)).thenReturn(Optional.of(product2));
            when(saveProductPort.saveProduct(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // 두 번째 상품은 재고 부족
            doNothing().when(stockDomainService).validateStockAvailability(eq(product), any());
            doThrow(new InsufficientStockException("재고 부족"))
                .when(stockDomainService).validateStockAvailability(eq(product2), any());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            List<ReservationResult> results = reserveStockService.reserveBatchStock(command);
            
            // then
            assertThat(results).hasSize(2);
            assertThat(results.get(0).isSuccess()).isTrue();
            assertThat(results.get(1).isSuccess()).isFalse();
            assertThat(results.get(1).getFailureReason()).contains("재고 부족");
        }
    }
    
    @Nested
    @DisplayName("Controller 호환 배치 예약")
    class ControllerCompatibleBatchReservation {
        
        @Test
        @DisplayName("정상적인 배치 예약")
        void reserveStockBatch_Success() throws Exception {
            // given
            List<ReserveStockCommand.ReservationItem> items = Arrays.asList(
                new ReserveStockCommand.ReservationItem(productId, StockQuantity.of(10))
            );
            
            BatchReserveStockCommand command = new BatchReserveStockCommand(
                "RESERVATION-001",
                items,
                Duration.ofMinutes(30)
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            BatchReservationResult result = reserveStockService.reserveStockBatch(command);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo("RESERVATION-001");
            assertThat(result.getResults()).hasSize(1);
            assertThat(result.isAllSuccess()).isTrue();
        }
        
        @Test
        @DisplayName("타임아웃 설정 확인")
        void reserveStockBatch_WithCustomTimeout() throws Exception {
            // given
            List<ReserveStockCommand.ReservationItem> items = Arrays.asList(
                new ReserveStockCommand.ReservationItem(productId, StockQuantity.of(10))
            );
            
            BatchReserveStockCommand command = new BatchReserveStockCommand(
                "RESERVATION-001",
                items,
                Duration.ofMinutes(60) // 60분 타임아웃
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            reserveStockService.reserveStockBatch(command);
            
            // then
            ArgumentCaptor<StockReservedEvent> eventCaptor = ArgumentCaptor.forClass(StockReservedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReservedEvent event = eventCaptor.getValue();
            // 만료 시간이 대략 60분 후인지 확인 (정확한 시간 비교는 어려움)
            assertThat(event.getExpiresAt()).isAfter(java.time.Instant.now().plusSeconds(3500));
        }
    }
    
    @Nested
    @DisplayName("이벤트 발행")
    class EventPublishing {
        
        @Test
        @DisplayName("재고 예약 성공 이벤트 상세 검증")
        void publishStockReservedEvent_VerifyDetails() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            reserveStockService.reserveStock(validCommand);
            
            // then
            ArgumentCaptor<StockReservedEvent> eventCaptor = ArgumentCaptor.forClass(StockReservedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReservedEvent event = eventCaptor.getValue();
            assertThat(event.getInventoryId()).isEqualTo(productId.getValue().toString());
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getReservedItems()).hasSize(1);
            
            StockReservedEvent.ReservedItem item = event.getReservedItems().get(0);
            assertThat(item.getProductId()).isEqualTo(productId.getValue().toString());
            assertThat(item.getProductName()).isEqualTo("테스트 상품");
            assertThat(item.getQuantity()).isEqualTo(10);
            assertThat(item.getWarehouseId()).isEqualTo("MAIN_WAREHOUSE");
        }
        
        @Test
        @DisplayName("도메인 이벤트와 추가 이벤트 모두 발행")
        void publishBothDomainAndAdditionalEvents() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            reserveStockService.reserveStock(validCommand);
            
            // then
            // 도메인 이벤트 발행
            verify(publishEventPort).publishEvents(anyList());
            // 추가 이벤트 발행
            verify(publishEventPort).publishEvent(any(StockReservedEvent.class));
        }
    }
    
    @Nested
    @DisplayName("락 키 생성")
    class LockKeyGeneration {
        
        @Test
        @DisplayName("올바른 락 키 형식 확인")
        void generateLockKey_CorrectFormat() throws Exception {
            // given
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
            
            when(distributedLockPort.executeWithLock(lockKeyCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            reserveStockService.reserveStock(validCommand);
            
            // then
            String lockKey = lockKeyCaptor.getValue();
            assertThat(lockKey).isEqualTo("stock:lock:product:" + productId.getValue());
        }
    }
}