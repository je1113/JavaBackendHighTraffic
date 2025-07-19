package com.hightraffic.ecommerce.order.application.handler;

import com.hightraffic.ecommerce.common.event.order.OrderPaidEvent;
import com.hightraffic.ecommerce.common.event.payment.PaymentCompletedEvent;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCompletedEventHandler 테스트")
class PaymentCompletedEventHandlerTest {
    
    @Mock
    private LoadOrderPort loadOrderPort;
    
    @Mock
    private SaveOrderPort saveOrderPort;
    
    @Mock
    private PublishEventPort publishEventPort;
    
    private PaymentCompletedEventHandler handler;
    private OrderId orderId;
    private Order testOrder;
    private PaymentCompletedEvent paymentEvent;
    
    @BeforeEach
    void setUp() {
        handler = new PaymentCompletedEventHandler(
            loadOrderPort,
            saveOrderPort,
            publishEventPort
        );
        
        orderId = OrderId.of(UUID.randomUUID().toString());
        testOrder = createTestOrder();
        paymentEvent = createPaymentCompletedEvent();
    }
    
    private Order createTestOrder() {
        Order order = Order.create(CustomerId.of("550e8400-e29b-41d4-a716-446655440000"));
        order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", 2, Money.of(10000, "KRW"));
        order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", 1, Money.of(20000, "KRW"));
        
        // 리플렉션을 사용하여 id와 status 설정
        try {
            Field idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
            
            Field statusField = Order.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(order, OrderStatus.PAYMENT_PENDING);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return order;
    }
    
    private PaymentCompletedEvent createPaymentCompletedEvent() {
        return new PaymentCompletedEvent(
            "PAY-12345",  // paymentId
            orderId.getValue().toString(),  // orderId
            "550e8400-e29b-41d4-a716-446655440000",  // customerId
            new BigDecimal("40000"),  // amount
            "KRW",  // currency
            "CREDIT_CARD",  // paymentMethod
            "TXN-12345",  // transactionId
            LocalDateTime.now()  // paidAt
        );
    }
    
    @Nested
    @DisplayName("결제 완료 이벤트 처리 성공")
    class SuccessfulPaymentHandling {
        
        @Test
        @DisplayName("PAYMENT_PENDING 상태의 주문을 PAID로 변경한다")
        void markOrderAsPaidFromPending() {
            // given
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // when
            handler.handle(paymentEvent);
            
            // then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(saveOrderPort).saveOrder(orderCaptor.capture());
            
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(savedOrder.getNotes()).contains("Payment completed");
            assertThat(savedOrder.getNotes()).contains(paymentEvent.getTransactionId());
        }
        
        @Test
        @DisplayName("PAYMENT_PROCESSING 상태의 주문도 처리할 수 있다")
        void markOrderAsPaidFromProcessing() {
            // given
            // 상태를 PAYMENT_PROCESSING으로 변경
            try {
                Field statusField = Order.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(testOrder, OrderStatus.PAYMENT_PROCESSING);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(saveOrderPort).saveOrder(any(Order.class));
            verify(publishEventPort).publishEvent(any(OrderPaidEvent.class));
        }
        
        @Test
        @DisplayName("OrderPaidEvent가 올바른 정보와 함께 발행된다")
        void publishOrderPaidEvent() {
            // given
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            ArgumentCaptor<OrderPaidEvent> eventCaptor = 
                ArgumentCaptor.forClass(OrderPaidEvent.class);
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(publishEventPort).publishEvent(eventCaptor.capture());
            
            OrderPaidEvent paidEvent = eventCaptor.getValue();
            assertThat(paidEvent.getOrderId()).isEqualTo(orderId.getValue().toString());
            assertThat(paidEvent.getCustomerId()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
            assertThat(paidEvent.getOrderItems()).hasSize(2);
            assertThat(paidEvent.getTotalAmount()).isEqualByComparingTo("40000");
            assertThat(paidEvent.getTransactionId()).isEqualTo("TXN-12345");
        }
        
        @Test
        @DisplayName("결제 정보가 주문 노트에 저장된다")
        void savePaymentInfoInNotes() {
            // given
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(saveOrderPort).saveOrder(orderCaptor.capture());
            
            Order savedOrder = orderCaptor.getValue();
            String notes = savedOrder.getNotes();
            assertThat(notes).contains("Payment completed");
            assertThat(notes).contains("Transaction ID: TXN-12345");
            assertThat(notes).contains("Amount: 40000");
            assertThat(notes).contains("Method: CREDIT_CARD");
        }
    }
    
    @Nested
    @DisplayName("결제 완료 이벤트 처리 실패")
    class FailedPaymentHandling {
        
        @Test
        @DisplayName("주문을 찾을 수 없으면 예외 처리한다")
        void orderNotFound() {
            // given
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.empty());
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
        
        @Test
        @DisplayName("잘못된 주문 상태면 예외 처리한다")
        void invalidOrderStatus() {
            // given
            // 이미 PAID 상태인 주문
            try {
                Field statusField = Order.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(testOrder, OrderStatus.PAID);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(saveOrderPort, never()).saveOrder(any());
            verify(publishEventPort, never()).publishEvent(any());
        }
        
        @Test
        @DisplayName("취소된 주문은 결제 완료 처리하지 않는다")
        void cancelledOrderNotProcessed() {
            // given
            // 먼저 PAYMENT_PROCESSING 상태로 변경 후 취소 (PAYMENT_PENDING는 취소 불가)
            try {
                Field statusField = Order.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(testOrder, OrderStatus.PAYMENT_PROCESSING);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            
            testOrder.cancel("고객 취소");
            
            when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
            
            // when
            handler.handle(paymentEvent);
            
            // then
            verify(saveOrderPort, never()).saveOrder(argThat(order -> 
                order.getStatus() == OrderStatus.PAID
            ));
            verify(publishEventPort, never()).publishEvent(any());
        }
    }
    
    @Nested
    @DisplayName("실패 처리")
    class FailureHandling {
        
        @Test
        @DisplayName("처리 실패 시 재시도 가능한 상태로 유지한다")
        void maintainRetryableState() {
            // given
            when(loadOrderPort.loadOrder(any(OrderId.class)))
                .thenReturn(Optional.of(testOrder))  // 첫 번째 호출
                .thenReturn(Optional.of(testOrder)); // 실패 처리 시 호출
                
            // saveOrder에서 예외 발생 시뮬레이션
            when(saveOrderPort.saveOrder(any(Order.class)))
                .thenThrow(new RuntimeException("Database error"))
                .thenAnswer(inv -> inv.getArgument(0)); // 실패 처리 시
            
            // when
            handler.handle(paymentEvent);
            
            // then
            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            verify(saveOrderPort, times(2)).saveOrder(orderCaptor.capture());
            
            Order failureHandledOrder = orderCaptor.getAllValues().get(1);
            assertThat(failureHandledOrder.getNotes()).contains("Payment completion handling failed");
            // 상태는 PAYMENT_PENDING로 유지됨
            assertThat(failureHandledOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
        }
    }
    
    @Test
    @DisplayName("OrderPaidEvent의 아이템 정보가 정확하다")
    void orderPaidEventItemsAreCorrect() {
        // given
        when(loadOrderPort.loadOrder(any(OrderId.class))).thenReturn(Optional.of(testOrder));
        when(saveOrderPort.saveOrder(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        
        ArgumentCaptor<OrderPaidEvent> eventCaptor = 
            ArgumentCaptor.forClass(OrderPaidEvent.class);
        
        // when
        handler.handle(paymentEvent);
        
        // then
        verify(publishEventPort).publishEvent(eventCaptor.capture());
        
        OrderPaidEvent event = eventCaptor.getValue();
        assertThat(event.getOrderItems()).hasSize(2);
        
        OrderPaidEvent.OrderItem firstItem = event.getOrderItems().get(0);
        assertThat(firstItem.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
        assertThat(firstItem.getProductName()).isEqualTo("상품1");
        assertThat(firstItem.getQuantity()).isEqualTo(2);
        
        OrderPaidEvent.OrderItem secondItem = event.getOrderItems().get(1);
        assertThat(secondItem.getProductId()).isEqualTo("550e8400-e29b-41d4-a716-446655440002");
        assertThat(secondItem.getProductName()).isEqualTo("상품2");
        assertThat(secondItem.getQuantity()).isEqualTo(1);
    }
}