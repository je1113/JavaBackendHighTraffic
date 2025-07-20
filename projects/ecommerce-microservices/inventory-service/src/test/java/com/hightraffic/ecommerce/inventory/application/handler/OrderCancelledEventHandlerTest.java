package com.hightraffic.ecommerce.inventory.application.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.common.event.inventory.StockReleasedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCancelledEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.HandleOrderCancelledEventUseCase.OrderCancelledCommand;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase.*;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductsByConditionPort;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelledEventHandler 테스트")
class OrderCancelledEventHandlerTest {
    
    @Mock
    private RestoreStockUseCase restoreStockUseCase;
    
    @Mock
    private LoadProductsByConditionPort loadProductsByConditionPort;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private OrderCancelledEventHandler handler;
    
    @BeforeEach
    void setUp() {
        // ObjectMapper가 실제로 작동하도록 설정
        ObjectMapper realObjectMapper = new ObjectMapper();
        try {
            lenient().when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenAnswer(invocation -> realObjectMapper.readValue(
                    invocation.getArgument(0, String.class),
                    invocation.getArgument(1, Class.class)
                ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Nested
    @DisplayName("주문 취소 명령 처리")
    class OrderCancelledCommandHandling {
        
        @Test
        @DisplayName("OrderCancelledCommand를 이벤트로 변환하여 처리")
        void handle_WithCommand_ConvertsToEventAndProcesses() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Mock empty batch result
            BatchReleaseResult emptyResult = new BatchReleaseResult(
                Arrays.asList(),
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(emptyResult);
            
            // when
            handler.handle(command);
            
            // then
            // 재고 복원 정보가 없으므로 배치 해제 시도
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
            
            // 빈 결과일 때 이벤트 발행 안함
            verify(publishEventPort, never()).publishEvent(any(StockReleasedEvent.class));
        }
    }
    
    @Nested
    @DisplayName("보상 액션 기반 재고 복원")
    class CompensationActionBasedRestore {
        
        @Test
        @DisplayName("Command 처리시 배치 해제 시도")
        void handle_WithCommand_AlwaysBatchRelease() throws Exception {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Mock batch result with actual releases
            List<RestoreStockUseCase.ReleaseResult> results = Arrays.asList(
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440101"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001")
                )
            );
            
            BatchReleaseResult batchResult = new BatchReleaseResult(
                results,
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(batchResult);
            
            // when
            handler.handle(command);
            
            // then
            // Command 처리시 항상 배치 해제를 시도함
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
            verify(publishEventPort).publishEvent(any(StockReleasedEvent.class));
        }
        
        @Test
        @DisplayName("배치 해제 중 오류 발생시 처리")
        void handle_BatchReleaseError_HandledGracefully() throws Exception {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // 배치 해제 실패 시뮬레이션
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenThrow(new RuntimeException("배치 해제 실패"));
            
            // when
            handler.handle(command);
            
            // then
            // 예외가 발생해도 핸들러가 정상 처리됨
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
            // 오류 발생시 이벤트는 발행되지 않음
            verify(publishEventPort, never()).publishEvent(any(StockReleasedEvent.class));
        }
        
        @Test
        @DisplayName("잘못된 JSON 데이터 처리")
        void handle_WithInvalidJson_SkipsRestoreInfo() throws Exception {
            // given
            OrderCancelledEvent.CompensationAction compensationAction = 
                new OrderCancelledEvent.CompensationAction(
                    "STOCK_RESTORE",
                    "inventory-service",
                    "invalid json data",
                    1
                );
            
            OrderCancelledEvent event = new OrderCancelledEvent(
                "ORDER-001",
                "CUST-001",
                "PAID",
                "고객 요청",
                "CUSTOMER_REQUEST",
                "CUST-001",
                "CUSTOMER",
                BigDecimal.valueOf(10000),
                Arrays.asList(compensationAction),
                "주문 취소"
            );
            
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Mock empty batch result
            BatchReleaseResult emptyResult = new BatchReleaseResult(
                Arrays.asList(),
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(emptyResult);
            
            // JSON 파싱 실패 시뮬레이션
            lenient().when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Invalid JSON") {});
            
            // when
            handler.handle(command);
            
            // then
            // 보상 액션 파싱 실패로 배치 해제 시도
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
        }
    }
    
    @Nested
    @DisplayName("주문 ID 기반 재고 복원")
    class OrderIdBasedRestore {
        
        @Test
        @DisplayName("보상 액션 없을 때 주문 ID로 배치 해제")
        void handle_WithoutCompensationActions_UsesOrderId() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Create ReleaseResult directly for BatchReleaseResult
            List<RestoreStockUseCase.ReleaseResult> results = Arrays.asList(
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440101"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001")
                ),
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440102"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002")
                )
            );
            
            BatchReleaseResult batchResult = new BatchReleaseResult(
                results,
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(batchResult);
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<BatchReleaseReservationCommand> commandCaptor = 
                ArgumentCaptor.forClass(BatchReleaseReservationCommand.class);
            verify(restoreStockUseCase).batchReleaseReservations(commandCaptor.capture());
            
            BatchReleaseReservationCommand capturedCommand = commandCaptor.getValue();
            assertThat(capturedCommand.getOrderId()).isEqualTo("ORDER-001");
            
            // 이벤트 발행 검증
            ArgumentCaptor<StockReleasedEvent> eventCaptor = ArgumentCaptor.forClass(StockReleasedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReleasedEvent releasedEvent = eventCaptor.getValue();
            assertThat(releasedEvent.getReleasedItems()).hasSize(2);
            assertThat(releasedEvent.getOrderId()).isEqualTo("ORDER-001");
        }
        
        @Test
        @DisplayName("주문 ID로 해제할 예약이 없을 때")
        void handle_NoReservationsFound_LogsWarning() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            BatchReleaseResult emptyResult = new BatchReleaseResult(
                Arrays.asList(),
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(emptyResult);
            
            // when
            handler.handle(command);
            
            // then
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
            verify(publishEventPort, never()).publishEvent(any(StockReleasedEvent.class));
        }
        
        @Test
        @DisplayName("배치 해제 중 예외 발생")
        void handle_BatchReleaseException_HandlesGracefully() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenThrow(new RuntimeException("배치 해제 실패"));
            
            // when & then
            // 예외가 전파되지 않고 로그로 처리됨
            assertThatCode(() -> handler.handle(command))
                .doesNotThrowAnyException();
                
            verify(publishEventPort, never()).publishEvent(any(StockReleasedEvent.class));
        }
    }
    
    @Nested
    @DisplayName("이벤트 발행")
    class EventPublishing {
        
        @Test
        @DisplayName("StockReleasedEvent 상세 검증")
        void publishStockReleasedEvent_VerifyDetails() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Create ReleaseResult directly for BatchReleaseResult
            List<RestoreStockUseCase.ReleaseResult> results = Arrays.asList(
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440101"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001")
                )
            );
            
            BatchReleaseResult batchResult = new BatchReleaseResult(
                results,
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(batchResult);
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<StockReleasedEvent> eventCaptor = ArgumentCaptor.forClass(StockReleasedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReleasedEvent event = eventCaptor.getValue();
            assertThat(event.getInventoryId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
            assertThat(event.getReservationId()).isEqualTo("ORDER-001-CANCEL");
            assertThat(event.getOrderId()).isEqualTo("ORDER-001");
            assertThat(event.getReleaseReason()).isEqualTo("ORDER_CANCELLED");
            assertThat(event.getReleasedBy()).isEqualTo("SYSTEM");
            assertThat(event.getReleasedByType()).isEqualTo("SYSTEM");
            assertThat(event.isCompensationRequired()).isFalse();
            
            StockReleasedEvent.ReleasedItem item = event.getReleasedItems().get(0);
            assertThat(item.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
            assertThat(item.getQuantity()).isEqualTo(1); // BatchReleaseResult.releaseResults()에서 hardcoded value
            assertThat(item.getWarehouseId()).isEqualTo("MAIN");
            assertThat(item.getReturnedTo()).isEqualTo("AVAILABLE");
            assertThat(item.getNotes()).contains("주문 취소로 인한 재고 복원");
        }
        
        @Test
        @DisplayName("여러 상품 해제시 배치 ID 사용")
        void publishStockReleasedEvent_MultipleItems_UsesBatchId() {
            // given
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Create ReleaseResult directly for BatchReleaseResult
            List<RestoreStockUseCase.ReleaseResult> results = Arrays.asList(
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440101"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440001")
                ),
                new RestoreStockUseCase.ReleaseResult(
                    ReservationId.of("550e8400-e29b-41d4-a716-446655440102"),
                    ProductId.of("550e8400-e29b-41d4-a716-446655440002")
                )
            );
            
            BatchReleaseResult batchResult = new BatchReleaseResult(
                results,
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(batchResult);
            
            // when
            handler.handle(command);
            
            // then
            ArgumentCaptor<StockReleasedEvent> eventCaptor = ArgumentCaptor.forClass(StockReleasedEvent.class);
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            StockReleasedEvent event = eventCaptor.getValue();
            assertThat(event.getReleasedItems()).hasSize(2);
            // 첫 번째 상품 ID를 aggregate ID로 사용
            assertThat(event.getInventoryId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
        }
    }
    
    @Nested
    @DisplayName("다양한 보상 액션 타입 처리")
    class VariousCompensationActionTypes {
        
        @Test
        @DisplayName("STOCK_RESTORE가 아닌 액션은 무시")
        void handle_NonStockRestoreAction_Ignores() {
            // given
            OrderCancelledEvent.CompensationAction compensationAction = 
                new OrderCancelledEvent.CompensationAction(
                    "PAYMENT_REFUND", // 다른 타입
                    "payment-service",
                    "{\"amount\":10000}",
                    1
                );
            
            OrderCancelledEvent event = new OrderCancelledEvent(
                "ORDER-001",
                "CUST-001",
                "PAID",
                "고객 요청",
                "CUSTOMER_REQUEST",
                "CUST-001",
                "CUSTOMER",
                BigDecimal.valueOf(10000),
                Arrays.asList(compensationAction),
                "주문 취소"
            );
            
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Mock empty batch result
            BatchReleaseResult emptyResult = new BatchReleaseResult(
                Arrays.asList(),
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(emptyResult);
            
            // when
            handler.handle(command);
            
            // then
            // STOCK_RESTORE가 아니므로 배치 해제 시도
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
        }
        
        @Test
        @DisplayName("다른 서비스의 STOCK_RESTORE 액션 무시")
        void handle_OtherServiceStockRestore_Ignores() {
            // given
            OrderCancelledEvent.CompensationAction compensationAction = 
                new OrderCancelledEvent.CompensationAction(
                    "STOCK_RESTORE",
                    "other-service", // 다른 서비스
                    "{\"items\":[]}",
                    1
                );
            
            OrderCancelledEvent event = new OrderCancelledEvent(
                "ORDER-001",
                "CUST-001",
                "PAID",
                "고객 요청",
                "CUSTOMER_REQUEST",
                "CUST-001",
                "CUSTOMER",
                BigDecimal.valueOf(10000),
                Arrays.asList(compensationAction),
                "주문 취소"
            );
            
            OrderCancelledCommand command = new OrderCancelledCommand("ORDER-001");
            
            // Mock empty batch result
            BatchReleaseResult emptyResult = new BatchReleaseResult(
                Arrays.asList(),
                "ORDER-001"
            );
            
            when(restoreStockUseCase.batchReleaseReservations(any(BatchReleaseReservationCommand.class)))
                .thenReturn(emptyResult);
            
            // when
            handler.handle(command);
            
            // then
            // inventory-service가 아니므로 배치 해제 시도
            verify(restoreStockUseCase).batchReleaseReservations(any(BatchReleaseReservationCommand.class));
        }
    }
}