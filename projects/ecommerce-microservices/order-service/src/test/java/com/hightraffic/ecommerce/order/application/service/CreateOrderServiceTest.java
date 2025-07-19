package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.order.application.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.hightraffic.ecommerce.order.application.port.in.CreateOrderUseCase.CreateOrderResult;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import com.hightraffic.ecommerce.order.domain.service.OrderValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateOrderService 테스트")
class CreateOrderServiceTest {
    
    @Mock
    private SaveOrderPort saveOrderPort;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private OrderValidationService validationService;
    
    @Mock
    private OrderBusinessRulesConfig businessRulesConfig;
    
    @Mock
    private OrderBusinessRulesConfig.TimePolicy timePolicy;
    
    private CreateOrderService createOrderService;
    private CreateOrderCommand validCommand;
    
    @BeforeEach
    void setUp() {
        when(businessRulesConfig.getTime()).thenReturn(timePolicy);
        when(timePolicy.getDuplicateOrderPreventionMinutes()).thenReturn(5);
        
        createOrderService = new CreateOrderService(
            saveOrderPort,
            publishEventPort,
            orderRepository,
            validationService,
            businessRulesConfig
        );
        
        // 유효한 기본 command 생성
        List<CreateOrderCommand.OrderItem> items = Arrays.asList(
            new CreateOrderCommand.OrderItem(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                "상품1",
                2,
                Money.of(10000, "KRW")
            ),
            new CreateOrderCommand.OrderItem(
                ProductId.of("550e8400-e29b-41d4-a716-446655440002"),
                "상품2",
                1,
                Money.of(20000, "KRW")
            )
        );
        
        validCommand = new CreateOrderCommand(
            CustomerId.of("550e8400-e29b-41d4-a716-446655440000"),
            items,
            null
        );
    }
    
    @Nested
    @DisplayName("주문 생성 성공")
    class SuccessfulOrderCreation {
        
        @Test
        @DisplayName("유효한 주문을 생성할 수 있다")
        void createValidOrder() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            validCommand.getOrderItems().forEach(item ->
                createdOrder.addItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
                )
            );
            
            when(saveOrderPort.saveOrder(any(Order.class))).thenReturn(createdOrder);
            
            // when
            CreateOrderResult result = createOrderService.createOrder(validCommand);
            
            // then
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(createdOrder.getOrderId());
            assertThat(result.getMessage()).contains("successfully");
            
            verify(saveOrderPort).saveOrder(any(Order.class));
            verify(publishEventPort).publishEvent(any(OrderCreatedEvent.class));
            verify(validationService).validateOrderItems(any(Order.class));
        }
        
        @Test
        @DisplayName("주문 생성 시 OrderCreatedEvent가 발행된다")
        void publishOrderCreatedEvent() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            validCommand.getOrderItems().forEach(item ->
                createdOrder.addItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
                )
            );
            
            when(saveOrderPort.saveOrder(any(Order.class))).thenReturn(createdOrder);
            
            ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderCreatedEvent.class);
            
            // when
            createOrderService.createOrder(validCommand);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderCreatedEvent publishedEvent = eventCaptor.getValue();
            assertThat(publishedEvent).isNotNull();
            assertThat(publishedEvent.getOrderId()).isEqualTo(createdOrder.getOrderId().getValue().toString());
            assertThat(publishedEvent.getCustomerId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
            assertThat(publishedEvent.getOrderItems()).hasSize(2);
            assertThat(publishedEvent.getTotalAmount()).isEqualTo(createdOrder.getTotalAmount().getAmount());
            assertThat(publishedEvent.getCurrency()).isEqualTo("KRW");
        }
        
        @Test
        @DisplayName("주문 아이템 정보가 정확하게 저장된다")
        void saveOrderWithCorrectItems() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            validCommand.getOrderItems().forEach(item ->
                createdOrder.addItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
                )
            );
            
            when(saveOrderPort.saveOrder(orderCaptor.capture())).thenReturn(createdOrder);
            
            // when
            createOrderService.createOrder(validCommand);
            
            // then
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getItems()).hasSize(2);
            assertThat(savedOrder.getTotalAmount()).isEqualTo(Money.of(40000, "KRW"));
            assertThat(savedOrder.getItemCount()).isEqualTo(2);
            assertThat(savedOrder.getTotalQuantity()).isEqualTo(3);
        }
    }
    
    @Nested
    @DisplayName("주문 생성 실패")
    class FailedOrderCreation {
        
        @Test
        @DisplayName("중복 주문이 감지되면 예외가 발생한다")
        void duplicateOrderDetected() {
            // given
            when(validationService.isDuplicateOrder(
                eq(validCommand.getCustomerId()),
                anyList(),
                any(LocalDateTime.class)
            )).thenReturn(true);
            
            // when & then
            assertThatThrownBy(() -> createOrderService.createOrder(validCommand))
                .isInstanceOf(CreateOrderService.DuplicateOrderException.class)
                .hasMessageContaining("Duplicate order detected within 5 minutes");
            
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
        
        @Test
        @DisplayName("주문 검증에 실패하면 예외가 발생한다")
        void orderValidationFailed() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            doThrow(new IllegalArgumentException("Invalid order items"))
                .when(validationService).validateOrderItems(any(Order.class));
            
            // when & then
            assertThatThrownBy(() -> createOrderService.createOrder(validCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid order items");
            
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
    }
    
    @Nested
    @DisplayName("중복 주문 검증")
    class DuplicateOrderValidation {
        
        @Test
        @DisplayName("설정된 시간 내의 중복 주문을 검사한다")
        void checkDuplicateWithinTimeWindow() {
            // given
            when(timePolicy.getDuplicateOrderPreventionMinutes()).thenReturn(10);
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            when(saveOrderPort.saveOrder(any(Order.class))).thenReturn(createdOrder);
            
            // when
            createOrderService.createOrder(validCommand);
            
            // then
            verify(validationService).isDuplicateOrder(
                eq(validCommand.getCustomerId()),
                anyList(),
                timeCaptor.capture()
            );
            
            LocalDateTime capturedTime = timeCaptor.getValue();
            LocalDateTime expectedTime = LocalDateTime.now().minusMinutes(10);
            
            // 시간 차이가 1초 이내인지 확인 (테스트 실행 시간 고려)
            assertThat(capturedTime).isBetween(
                expectedTime.minusSeconds(1),
                expectedTime.plusSeconds(1)
            );
        }
        
        @Test
        @DisplayName("중복 검사 시 상품 ID 목록을 전달한다")
        void passProductIdsForDuplicateCheck() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            ArgumentCaptor<List<ProductId>> productIdsCaptor = 
                ArgumentCaptor.forClass(List.class);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            when(saveOrderPort.saveOrder(any(Order.class))).thenReturn(createdOrder);
            
            // when
            createOrderService.createOrder(validCommand);
            
            // then
            verify(validationService).isDuplicateOrder(
                eq(validCommand.getCustomerId()),
                productIdsCaptor.capture(),
                any(LocalDateTime.class)
            );
            
            List<ProductId> capturedProductIds = productIdsCaptor.getValue();
            assertThat(capturedProductIds).hasSize(2);
            assertThat(capturedProductIds).extracting(ProductId::getValue)
                .containsExactly("550e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440002");
        }
    }
    
    @Nested
    @DisplayName("이벤트 생성")
    class EventCreation {
        
        @Test
        @DisplayName("OrderCreatedEvent에 올바른 아이템 정보가 포함된다")
        void eventContainsCorrectItemInfo() {
            // given
            when(validationService.isDuplicateOrder(any(), anyList(), any(LocalDateTime.class)))
                .thenReturn(false);
            
            Order createdOrder = Order.create(validCommand.getCustomerId());
            validCommand.getOrderItems().forEach(item ->
                createdOrder.addItem(
                    item.getProductId(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice()
                )
            );
            
            when(saveOrderPort.saveOrder(any(Order.class))).thenReturn(createdOrder);
            
            ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderCreatedEvent.class);
            
            // when
            createOrderService.createOrder(validCommand);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderCreatedEvent event = eventCaptor.getValue();
            assertThat(event.getOrderItems()).hasSize(2);
            
            OrderCreatedEvent.OrderItemData firstItem = event.getOrderItems().get(0);
            assertThat(firstItem.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
            assertThat(firstItem.getProductName()).isEqualTo("상품1");
            assertThat(firstItem.getQuantity()).isEqualTo(2);
            assertThat(firstItem.getUnitPrice()).isEqualByComparingTo("10000");
            assertThat(firstItem.getTotalPrice()).isEqualByComparingTo("20000");
            
            OrderCreatedEvent.OrderItemData secondItem = event.getOrderItems().get(1);
            assertThat(secondItem.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440002");
            assertThat(secondItem.getProductName()).isEqualTo("상품2");
            assertThat(secondItem.getQuantity()).isEqualTo(1);
            assertThat(secondItem.getUnitPrice()).isEqualByComparingTo("20000");
            assertThat(secondItem.getTotalPrice()).isEqualByComparingTo("20000");
        }
    }
}