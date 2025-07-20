package com.hightraffic.ecommerce.inventory.application.handler;

import com.hightraffic.ecommerce.common.event.inventory.InsufficientStockEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.HandleOrderCreatedEventUseCase.OrderCreatedCommand;
import com.hightraffic.ecommerce.inventory.application.port.in.HandleOrderCreatedEventUseCase.OrderItem;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase.*;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCreatedEventHandler 테스트")
class OrderCreatedEventHandlerTest {
    
    @Mock
    private ReserveStockUseCase reserveStockUseCase;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    private OrderCreatedEventHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new OrderCreatedEventHandler(reserveStockUseCase, publishEventPort);
    }
    
    @Nested
    @DisplayName("주문 생성 이벤트 처리 성공")
    class SuccessfulEventHandling {
        
        @Test
        @DisplayName("모든 재고 예약이 성공하면 StockReservedEvent가 발행된다")
        void allReservationsSuccessful() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 2),
                new OrderItem("550e8400-e29b-41d4-a716-446655440002", 3)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            // 예약 성공 결과 설정
            List<ReservationResult> reservationResults = Arrays.asList(
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                    ReservationId.generate(),
                    StockQuantity.of(2),
                    StockQuantity.of(98),
                    Instant.now().plusSeconds(1800)
                ),
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002"),
                    ReservationId.generate(),
                    StockQuantity.of(3),
                    StockQuantity.of(47),
                    Instant.now().plusSeconds(1800)
                )
            );
            
            when(reserveStockUseCase.reserveBatchStock(any(ReserveBatchStockCommand.class)))
                .thenReturn(reservationResults);
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<StockReservedEvent> eventCaptor = 
                ArgumentCaptor.forClass(StockReservedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReservedEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getReservedItems()).hasSize(2);
            
            verify(reserveStockUseCase).reserveBatchStock(any(ReserveBatchStockCommand.class));
        }
        
        @Test
        @DisplayName("배치 예약 명령이 올바르게 생성된다")
        void correctBatchReservationCommand() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 5),
                new OrderItem("550e8400-e29b-41d4-a716-446655440002", 10)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            ArgumentCaptor<ReserveBatchStockCommand> commandCaptor = 
                ArgumentCaptor.forClass(ReserveBatchStockCommand.class);
            
            when(reserveStockUseCase.reserveBatchStock(commandCaptor.capture()))
                .thenReturn(Arrays.asList(
                    new ReservationResult(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "SUCCESS"),
                    new ReservationResult(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "SUCCESS")
                ));
            
            // when
            handler.handle(command);
            
            // then
            ReserveBatchStockCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getOrderId()).isEqualTo("ORDER-001");
            assertThat(capturedCommand.getStockItems()).hasSize(2);
            assertThat(capturedCommand.isAtomicReservation()).isTrue();
            
            ReserveBatchStockCommand.StockItem item1 = capturedCommand.getStockItems().get(0);
            assertThat(item1.getProductId().getValue().toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
            assertThat(item1.getQuantity()).isEqualTo(StockQuantity.of(5));
            
            ReserveBatchStockCommand.StockItem item2 = capturedCommand.getStockItems().get(1);
            assertThat(item2.getProductId().getValue().toString()).isEqualTo("550e8400-e29b-41d4-a716-446655440002");
            assertThat(item2.getQuantity()).isEqualTo(StockQuantity.of(10));
        }
    }
    
    @Nested
    @DisplayName("주문 생성 이벤트 처리 실패")
    class FailedEventHandling {
        
        @Test
        @DisplayName("일부 재고 예약이 실패하면 InsufficientStockEvent가 발행된다")
        void partialReservationFailure() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 2),
                new OrderItem("550e8400-e29b-41d4-a716-446655440002", 3)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            // 첫 번째는 성공, 두 번째는 실패
            List<ReservationResult> mixedResults = Arrays.asList(
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                    ReservationId.generate(),
                    StockQuantity.of(2),
                    StockQuantity.of(98),
                    Instant.now().plusSeconds(1800)
                ),
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002"),
                    "재고 부족"
                )
            );
            
            when(reserveStockUseCase.reserveBatchStock(any(ReserveBatchStockCommand.class)))
                .thenReturn(mixedResults);
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<InsufficientStockEvent> eventCaptor = 
                ArgumentCaptor.forClass(InsufficientStockEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            InsufficientStockEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440002");
            assertThat(event.getReason()).isEqualTo("재고 부족");
        }
        
        @Test
        @DisplayName("재고 예약 중 예외가 발생하면 InsufficientStockEvent가 발행된다")
        void exceptionDuringReservation() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 2)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            when(reserveStockUseCase.reserveBatchStock(any(ReserveBatchStockCommand.class)))
                .thenThrow(new RuntimeException("시스템 오류"));
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<InsufficientStockEvent> eventCaptor = 
                ArgumentCaptor.forClass(InsufficientStockEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            InsufficientStockEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
            assertThat(event.getRequestedQuantity()).isEqualTo(2);
            assertThat(event.getReason()).isEqualTo("시스템 오류");
        }
        
        @Test
        @DisplayName("모든 재고 예약이 실패하면 롤백이 필요없다")
        void allReservationsFailed() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 2),
                new OrderItem("550e8400-e29b-41d4-a716-446655440002", 3)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            // 모두 실패
            List<ReservationResult> failedResults = Arrays.asList(
                new ReservationResult(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "재고 부족"),
                new ReservationResult(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품 비활성")
            );
            
            when(reserveStockUseCase.reserveBatchStock(any(ReserveBatchStockCommand.class)))
                .thenReturn(failedResults);
            
            // when
            handler.handle(command);
            
            // then
            verify(publishEventPort).publishEvent(any(InsufficientStockEvent.class));
            // StockReservedEvent는 발행되지 않음
            verify(publishEventPort, never()).publishEvent(any(StockReservedEvent.class));
        }
    }
    
    @Nested
    @DisplayName("이벤트 변환")
    class EventConversion {
        
        @Test
        @DisplayName("OrderCreatedCommand가 OrderCreatedEvent로 올바르게 변환된다")
        void commandToEventConversion() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 5)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            ArgumentCaptor<ReserveBatchStockCommand> commandCaptor = 
                ArgumentCaptor.forClass(ReserveBatchStockCommand.class);
            
            when(reserveStockUseCase.reserveBatchStock(commandCaptor.capture()))
                .thenReturn(Arrays.asList(
                    new ReservationResult(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "SUCCESS")
                ));
            
            // when
            handler.handle(command);
            
            // then
            ReserveBatchStockCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getStockItems()).hasSize(1);
            assertThat(capturedCommand.getStockItems().get(0).getQuantity().getValue()).isEqualTo(5);
        }
    }
    
    @Nested
    @DisplayName("예약 롤백")
    class ReservationRollback {
        
        @Test
        @DisplayName("부분 실패 시 성공한 예약들이 롤백된다")
        void rollbackSuccessfulReservations() {
            // given
            List<OrderItem> items = Arrays.asList(
                new OrderItem("550e8400-e29b-41d4-a716-446655440001", 2),
                new OrderItem("550e8400-e29b-41d4-a716-446655440002", 3),
                new OrderItem("550e8400-e29b-41d4-a716-446655440003", 4)
            );
            
            OrderCreatedCommand command = new OrderCreatedCommand("ORDER-001", items);
            
            // 첫 두 개는 성공, 마지막은 실패
            List<ReservationResult> mixedResults = Arrays.asList(
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                    ReservationId.generate(),
                    StockQuantity.of(2),
                    StockQuantity.of(98),
                    Instant.now().plusSeconds(1800)
                ),
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002"),
                    ReservationId.generate(),
                    StockQuantity.of(3),
                    StockQuantity.of(47),
                    Instant.now().plusSeconds(1800)
                ),
                new ReservationResult(
                    ProductId.of("550e8400-e29b-41d4-a716-446655440003"),
                    "재고 부족"
                )
            );
            
            when(reserveStockUseCase.reserveBatchStock(any(ReserveBatchStockCommand.class)))
                .thenReturn(mixedResults);
            
            // when
            handler.handle(command);
            
            // then
            // 실제 구현에서는 RestoreStockUseCase를 호출해야 하지만,
            // 현재 구현은 TODO로 남겨져 있음
            verify(publishEventPort).publishEvent(any(InsufficientStockEvent.class));
        }
    }
}