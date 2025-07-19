package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.common.event.order.OrderCancelledEvent;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase.CancelOrderCommand;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase.OrderAlreadyCancelledException;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase.OrderNotCancellableException;
import com.hightraffic.ecommerce.order.application.port.in.CancelOrderUseCase.OrderNotFoundException;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelOrderService 테스트")
class CancelOrderServiceTest {
    
    @Mock
    private LoadOrderPort loadOrderPort;
    
    @Mock
    private SaveOrderPort saveOrderPort;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    @Mock
    private OrderBusinessRulesConfig businessRulesConfig;
    
    @Mock
    private OrderBusinessRulesConfig.TimePolicy timePolicy;
    
    private CancelOrderService cancelOrderService;
    private OrderId orderId;
    private Order testOrder;
    
    @BeforeEach
    void setUp() {
        cancelOrderService = new CancelOrderService(
            loadOrderPort,
            saveOrderPort,
            publishEventPort,
            businessRulesConfig
        );
        
        orderId = OrderId.of(UUID.randomUUID().toString());
        testOrder = createTestOrder();
    }
    
    private Order createTestOrder() {
        Order order = Order.create(CustomerId.of("550e8400-e29b-41d4-a716-446655440000"));
        order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", 1, Money.of(10000, "KRW"));
        
        // 리플렉션을 사용하여 createdAt 설정 (테스트용)
        try {
            Field createdAtField = Order.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(order, LocalDateTime.now().minusHours(1)); // 1시간 전 생성
            
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return order;
    }
    
    @Nested
    @DisplayName("주문 취소 성공")
    class SuccessfulCancellation {
        
        @Test
        @DisplayName("고객이 주문을 취소할 수 있다")
        void customerCancelOrder() {
            // given
            when(businessRulesConfig.getTime()).thenReturn(timePolicy);
            when(timePolicy.getOrderCancellationHours()).thenReturn(24); // 24시간 이내 취소 가능
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "고객 변심",
                true // 고객 주도
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(saveOrderPort).saveOrder(orderCaptor.capture());
            
            Order cancelledOrder = orderCaptor.getValue();
            assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(cancelledOrder.getCancellationReason()).isEqualTo("고객 변심");
            
            verify(publishEventPort).publishEvent(any(OrderCancelledEvent.class));
        }
        
        @Test
        @DisplayName("시스템이 주문을 취소할 수 있다")
        void systemCancelOrder() {
            // given
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "재고 부족",
                false // 시스템 주도
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            verify(saveOrderPort).saveOrder(any(Order.class));
            verify(publishEventPort).publishEvent(any(OrderCancelledEvent.class));
        }
        
        @Test
        @DisplayName("취소 이벤트가 올바른 정보를 포함한다")
        void cancelEventContainsCorrectInfo() {
            // given
            when(businessRulesConfig.getTime()).thenReturn(timePolicy);
            when(timePolicy.getOrderCancellationHours()).thenReturn(24); // 24시간 이내 취소 가능
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "테스트 취소",
                true
            );
            
            testOrder.confirm(); // CONFIRMED 상태로 변경
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            ArgumentCaptor<OrderCancelledEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderCancelledEvent.class);
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderCancelledEvent event = eventCaptor.getValue();
            assertThat(event.getOrderId()).isEqualTo(orderId.getValue().toString());
            assertThat(event.getCustomerId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
            assertThat(event.getPreviousStatus()).isEqualTo("CONFIRMED");
            assertThat(event.getCancelReason()).isEqualTo("테스트 취소");
            assertThat(event.getCancelledByType()).isEqualTo("CUSTOMER");
            assertThat(event.getRefundAmount()).isEqualTo(testOrder.getTotalAmount().getAmount());
            assertThat(event.getCompensationActions()).hasSize(1);
            assertThat(event.getCompensationActions().get(0).getActionType()).isEqualTo("STOCK_RELEASE");
        }
    }
    
    @Nested
    @DisplayName("주문 취소 실패")
    class FailedCancellation {
        
        @Test
        @DisplayName("존재하지 않는 주문은 취소할 수 없다")
        void cannotCancelNonExistentOrder() {
            // given
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "취소 사유",
                true
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.empty());
            
            // when & then
            assertThatThrownBy(() -> cancelOrderService.cancelOrder(command))
                .isInstanceOf(OrderNotFoundException.class);
            
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
        
        @Test
        @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
        void cannotCancelAlreadyCancelledOrder() {
            // given
            testOrder.cancel("이전 취소");
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "재취소 시도",
                true
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            
            // when & then
            assertThatThrownBy(() -> cancelOrderService.cancelOrder(command))
                .isInstanceOf(OrderAlreadyCancelledException.class);
            
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
        
        @Test
        @DisplayName("취소 불가능한 상태의 주문은 취소할 수 없다")
        void cannotCancelNonCancellableOrder() {
            // given
            // 주문을 SHIPPED 상태로 만들기
            testOrder.confirm();
            testOrder.markAsPaymentPending();
            testOrder.markAsPaymentProcessing();
            testOrder.markAsPaid();
            testOrder.markAsPreparing();
            testOrder.markAsShipped();
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "취소 시도",
                true
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            
            // when & then
            assertThatThrownBy(() -> cancelOrderService.cancelOrder(command))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining("cannot be cancelled in current state");
        }
        
        @Test
        @DisplayName("고객은 취소 기한이 지난 주문을 취소할 수 없다")
        void customerCannotCancelAfterDeadline() {
            // given
            when(businessRulesConfig.getTime()).thenReturn(timePolicy);
            when(timePolicy.getOrderCancellationHours()).thenReturn(24); // 24시간 이내 취소 가능
            
            // 25시간 전 생성된 주문 설정
            try {
                Field createdAtField = Order.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(testOrder, LocalDateTime.now().minusHours(25));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "늦은 취소",
                true // 고객 주도
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            
            // when & then
            assertThatThrownBy(() -> cancelOrderService.cancelOrder(command))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining("Cancellation period of 24 hours has expired");
        }
        
        @Test
        @DisplayName("시스템은 취소 기한과 관계없이 주문을 취소할 수 있다")
        void systemCanCancelAnytime() {
            // given
            // 25시간 전 생성된 주문 설정
            try {
                Field createdAtField = Order.class.getDeclaredField("createdAt");
                createdAtField.setAccessible(true);
                createdAtField.set(testOrder, LocalDateTime.now().minusHours(25));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "시스템 취소",
                false // 시스템 주도
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            verify(saveOrderPort).saveOrder(any(Order.class));
            verify(publishEventPort).publishEvent(any(OrderCancelledEvent.class));
        }
    }
    
    @Nested
    @DisplayName("취소 이벤트 생성")
    class CancellationEventCreation {
        
        @Test
        @DisplayName("고객 취소 시 cancelledBy가 고객 ID가 된다")
        void customerCancelledByIsCustomerId() {
            // given
            when(businessRulesConfig.getTime()).thenReturn(timePolicy);
            when(timePolicy.getOrderCancellationHours()).thenReturn(24); // 24시간 이내 취소 가능
            
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "고객 취소",
                true
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            ArgumentCaptor<OrderCancelledEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderCancelledEvent.class);
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderCancelledEvent event = eventCaptor.getValue();
            assertThat(event.getCancelledBy()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
            assertThat(event.getCancelledByType()).isEqualTo("CUSTOMER");
        }
        
        @Test
        @DisplayName("시스템 취소 시 cancelledBy가 SYSTEM이 된다")
        void systemCancelledByIsSystem() {
            // given
            CancelOrderCommand command = new CancelOrderCommand(
                orderId,
                "시스템 취소",
                false
            );
            
            when(loadOrderPort.loadOrder(orderId)).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            ArgumentCaptor<OrderCancelledEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderCancelledEvent.class);
            
            // when
            cancelOrderService.cancelOrder(command);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderCancelledEvent event = eventCaptor.getValue();
            assertThat(event.getCancelledBy()).isEqualTo("SYSTEM");
            assertThat(event.getCancelledByType()).isEqualTo("SYSTEM");
        }
    }
}