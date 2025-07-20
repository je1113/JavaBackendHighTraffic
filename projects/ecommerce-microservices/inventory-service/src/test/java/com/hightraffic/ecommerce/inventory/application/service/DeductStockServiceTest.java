package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.common.event.inventory.StockDeductedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.DeductStockUseCase.*;
import com.hightraffic.ecommerce.inventory.application.port.in.DeductStockUseCase.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.application.port.in.DeductStockUseCase.ReservationNotFoundException;
import com.hightraffic.ecommerce.inventory.application.port.in.DeductStockUseCase.InvalidReservationException;
import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.application.port.out.SaveProductPort;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeductStockService 테스트")
class DeductStockServiceTest {
    
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
    private DeductStockService deductStockService;
    
    private ProductId productId;
    private Product product;
    
    @BeforeEach
    void setUp() {
        productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
        product = new Product(productId, "테스트 상품", StockQuantity.of(100));
    }
    
    @Nested
    @DisplayName("직접 재고 차감")
    class DirectStockDeduction {
        
        @Test
        @DisplayName("정상적인 직접 재고 차감")
        void deductStockDirectly_Success() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(30),
                "직접 판매",
                "REF-001"
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            // 분산 락 동작 설정
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            deductStockService.deductStockDirectly(command);
            
            // then
            verify(stockDomainService).validateStockAvailability(product, command.getQuantity());
            verify(saveProductPort).saveProduct(any(Product.class));
            verify(publishEventPort).publishEvents(anyList());
            verify(publishEventPort).publishEvent(any(StockDeductedEvent.class));
        }
        
        @Test
        @DisplayName("재고 부족으로 직접 차감 실패")
        void deductStockDirectly_InsufficientStock_ThrowsException() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(150),
                "직접 판매",
                null
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            doThrow(new InsufficientStockException("재고 부족"))
                .when(stockDomainService).validateStockAvailability(product, command.getQuantity());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> deductStockService.deductStockDirectly(command))
                .isInstanceOf(InsufficientStockException.class);
                
            verify(saveProductPort, never()).saveProduct(any());
            verify(publishEventPort, never()).publishEvent(any(StockDeductedEvent.class));
        }
        
        @Test
        @DisplayName("존재하지 않는 상품 차감 시도")
        void deductStockDirectly_ProductNotFound_ThrowsException() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(10),
                "직접 판매",
                null
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.empty());
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            assertThatThrownBy(() -> deductStockService.deductStockDirectly(command))
                .isInstanceOf(ProductNotFoundException.class);
        }
        
        @Test
        @DisplayName("직접 차감 이벤트 상세 검증")
        void deductStockDirectly_VerifyEventDetails() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(30),
                "직접 판매",
                "REF-001"
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            deductStockService.deductStockDirectly(command);
            
            // then
            ArgumentCaptor<StockDeductedEvent> eventCaptor = ArgumentCaptor.forClass(StockDeductedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockDeductedEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("REF-001");
            assertThat(event.getProductId()).isEqualTo(productId.getValue().toString());
            assertThat(event.getDeductedQuantity()).isEqualTo(30);
            assertThat(event.getRemainingQuantity()).isEqualTo(70); // 100 - 30
        }
    }
    
    @Nested
    @DisplayName("예약된 재고 차감")
    class ReservedStockDeduction {
        
        @Test
        @DisplayName("예약으로 상품 찾기 미구현 확인")
        void deductReservedStock_NotImplemented_ThrowsException() throws Exception {
            // given
            ReservationId reservationId = ReservationId.generate();
            DeductReservedStockCommand command = new DeductReservedStockCommand(
                reservationId,
                "ORDER-001",
                "주문 완료"
            );
            
            lenient().when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when & then
            // 현재 구현에서는 findProductByReservation이 미구현 상태
            assertThatThrownBy(() -> deductStockService.deductReservedStock(command))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Finding product by reservation needs to be implemented");
        }
        
        // 실제 구현이 완료되면 아래 테스트들을 활성화
        /*
        @Test
        @DisplayName("정상적인 예약 재고 차감")
        void deductReservedStock_Success() {
            // given
            ReservationId reservationId = product.reserveStock(StockQuantity.of(30), "ORDER-001");
            DeductReservedStockCommand command = new DeductReservedStockCommand(
                reservationId,
                "ORDER-001",
                "주문 완료"
            );
            
            // findProductByReservation이 구현되었다고 가정
            // when
            deductStockService.deductReservedStock(command);
            
            // then
            assertThat(product.getTotalQuantity()).isEqualTo(StockQuantity.of(70));
            assertThat(product.getReservedQuantity()).isEqualTo(StockQuantity.zero());
        }
        
        @Test
        @DisplayName("만료된 예약 차감 시도")
        void deductReservedStock_ExpiredReservation_ThrowsException() {
            // 만료된 예약에 대한 테스트
        }
        
        @Test
        @DisplayName("존재하지 않는 예약 차감 시도")
        void deductReservedStock_NonExistentReservation_ThrowsException() {
            // 존재하지 않는 예약에 대한 테스트
        }
        */
    }
    
    @Nested
    @DisplayName("기본 재고 차감 (호환성)")
    class BasicStockDeduction {
        
        @Test
        @DisplayName("DeductStockCommand를 직접 차감으로 변환")
        void deductStock_ConvertsToDirectDeduction() throws Exception {
            // given
            DeductStockCommand command = new DeductStockCommand(
                productId,
                StockQuantity.of(20)
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            deductStockService.deductStock(command);
            
            // then
            verify(stockDomainService).validateStockAvailability(product, command.getQuantity());
            verify(saveProductPort).saveProduct(any(Product.class));
            
            // 이벤트 검증
            ArgumentCaptor<StockDeductedEvent> eventCaptor = ArgumentCaptor.forClass(StockDeductedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockDeductedEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("DIRECT_DEDUCTION");
            assertThat(event.getDeductedQuantity()).isEqualTo(20);
        }
    }
    
    @Nested
    @DisplayName("배치 예약 재고 차감")
    class BatchReservedStockDeduction {
        
        @Test
        @DisplayName("배치 차감 - 현재 미구현으로 모두 실패")
        void deductBatchReservedStock_CurrentlyFails() {
            // given
            List<DeductBatchReservedStockCommand.DeductionItem> items = Arrays.asList(
                new DeductBatchReservedStockCommand.DeductionItem(
                    ReservationId.generate(),
                    productId
                ),
                new DeductBatchReservedStockCommand.DeductionItem(
                    ReservationId.generate(),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002")
                )
            );
            
            DeductBatchReservedStockCommand command = new DeductBatchReservedStockCommand(
                items,
                "ORDER-001"
            );
            
            // when
            List<DeductionResult> results = deductStockService.deductBatchReservedStock(command);
            
            // then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(result -> !result.isSuccess());
            assertThat(results).allMatch(result -> result.getFailureReason() != null);
        }
    }
    
    @Nested
    @DisplayName("분산 락 처리")
    class DistributedLockHandling {
        
        @Test
        @DisplayName("락 획득 실패시 예외 발생")
        void lockAcquisitionFailure_ThrowsException() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(10),
                "직접 판매",
                null
            );
            
            when(distributedLockPort.executeWithLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenThrow(new DistributedLockPort.LockAcquisitionException("lockKey", "Lock timeout"));
            
            // when & then
            assertThatThrownBy(() -> deductStockService.deductStockDirectly(command))
                .isInstanceOf(DistributedLockPort.LockAcquisitionException.class);
                
            verify(loadProductPort, never()).loadProduct(any());
        }
        
        @Test
        @DisplayName("올바른 락 키 생성 확인")
        void generateLockKey_CorrectFormat() throws Exception {
            // given
            DeductStockDirectlyCommand command = new DeductStockDirectlyCommand(
                productId,
                StockQuantity.of(10),
                "직접 판매",
                null
            );
            
            when(loadProductPort.loadProduct(productId)).thenReturn(Optional.of(product));
            when(saveProductPort.saveProduct(any(Product.class))).thenReturn(product);
            
            ArgumentCaptor<String> lockKeyCaptor = ArgumentCaptor.forClass(String.class);
            
            when(distributedLockPort.executeWithLock(lockKeyCaptor.capture(), anyLong(), anyLong(), any(TimeUnit.class), any(Supplier.class)))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(4);
                    return supplier.get();
                });
            
            // when
            deductStockService.deductStockDirectly(command);
            
            // then
            String lockKey = lockKeyCaptor.getValue();
            assertThat(lockKey).isEqualTo("stock:lock:product:" + productId.getValue());
        }
    }
    
}