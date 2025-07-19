package com.hightraffic.ecommerce.order.domain.service;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderDomainService 테스트")
class OrderDomainServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private OrderPricingPolicy pricingPolicy;
    
    private OrderDomainService orderDomainService;
    private CustomerId customerId;
    
    @BeforeEach
    void setUp() {
        orderDomainService = new OrderDomainService(orderRepository, pricingPolicy);
        customerId = CustomerId.of("550e8400-e29b-41d4-a716-446655440000");
    }
    
    @Nested
    @DisplayName("고가 주문 판별")
    class HighValueOrderCheck {
        
        @Test
        @DisplayName("VIP 임계값 이상의 주문은 고가 주문이다")
        void highValueOrder() {
            when(pricingPolicy.getVipThreshold()).thenReturn(new BigDecimal("1000000"));
            
            Order order = Order.create(customerId);
            order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "고가 상품", 1, Money.of(1500000, "KRW"));
            
            boolean isHighValue = orderDomainService.isHighValueOrder(order);
            
            assertThat(isHighValue).isTrue();
        }
        
        @Test
        @DisplayName("VIP 임계값 미만의 주문은 고가 주문이 아니다")
        void notHighValueOrder() {
            when(pricingPolicy.getVipThreshold()).thenReturn(new BigDecimal("1000000"));
            
            Order order = Order.create(customerId);
            order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "일반 상품", 1, Money.of(500000, "KRW"));
            
            boolean isHighValue = orderDomainService.isHighValueOrder(order);
            
            assertThat(isHighValue).isFalse();
        }
        
        @Test
        @DisplayName("VIP 임계값과 동일한 금액도 고가 주문이다")
        void exactThresholdIsHighValue() {
            when(pricingPolicy.getVipThreshold()).thenReturn(new BigDecimal("1000000"));
            
            Order order = Order.create(customerId);
            order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품", 1, Money.of(1000000, "KRW"));
            
            boolean isHighValue = orderDomainService.isHighValueOrder(order);
            
            assertThat(isHighValue).isTrue();
        }
    }
    
    @Nested
    @DisplayName("주문 병합")
    class OrderMerging {
        
        @Test
        @DisplayName("동일 고객의 PENDING 주문들을 병합할 수 있다")
        void canMergeSameCustomerPendingOrders() {
            Order order1 = Order.create(customerId);
            Order order2 = Order.create(customerId);
            
            boolean canMerge = orderDomainService.canMergeOrders(order1, order2);
            
            assertThat(canMerge).isTrue();
        }
        
        @Test
        @DisplayName("다른 고객의 주문은 병합할 수 없다")
        void cannotMergeDifferentCustomerOrders() {
            Order order1 = Order.create(customerId);
            Order order2 = Order.create(CustomerId.of("550e8400-e29b-41d4-a716-446655440001"));
            
            boolean canMerge = orderDomainService.canMergeOrders(order1, order2);
            
            assertThat(canMerge).isFalse();
        }
        
        @Test
        @DisplayName("PENDING이 아닌 주문은 병합할 수 없다")
        void cannotMergeNonPendingOrders() {
            Order order1 = Order.create(customerId);
            Order order2 = Order.create(customerId);
            order2.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품", 1, Money.of(10000, "KRW"));
            order2.confirm();
            
            boolean canMerge = orderDomainService.canMergeOrders(order1, order2);
            
            assertThat(canMerge).isFalse();
        }
        
        @Test
        @DisplayName("주문을 병합하면 모든 아이템이 타겟 주문으로 이동한다")
        void mergeOrderItems() {
            Order targetOrder = Order.create(customerId);
            targetOrder.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", 1, Money.of(10000, "KRW"));
            
            Order sourceOrder = Order.create(customerId);
            sourceOrder.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", 2, Money.of(20000, "KRW"));
            sourceOrder.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440003"), "상품3", 3, Money.of(30000, "KRW"));
            
            Order mergedOrder = orderDomainService.mergeOrders(targetOrder, sourceOrder);
            
            assertThat(mergedOrder.getItems()).hasSize(3);
            assertThat(mergedOrder.getTotalAmount()).isEqualTo(Money.of(140000, "KRW"));
            assertThat(sourceOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(sourceOrder.getCancellationReason()).contains("병합");
        }
        
        @Test
        @DisplayName("병합할 수 없는 주문을 병합하려고 하면 예외가 발생한다")
        void mergeInvalidOrders() {
            Order order1 = Order.create(customerId);
            Order order2 = Order.create(CustomerId.of("550e8400-e29b-41d4-a716-446655440001"));
            
            assertThatThrownBy(() -> orderDomainService.mergeOrders(order1, order2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("주문을 병합할 수 없습니다");
        }
    }
    
    @Nested
    @DisplayName("주문 우선순위 계산")
    class OrderPriorityCalculation {
        
        @Test
        @DisplayName("고가 주문은 100점의 추가 우선순위를 받는다")
        void highValueOrderPriority() {
            when(pricingPolicy.getVipThreshold()).thenReturn(new BigDecimal("1000000"));
            
            Order order = Order.create(customerId);
            order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "고가 상품", 1, Money.of(2000000, "KRW"));
            
            int priority = orderDomainService.calculateOrderPriority(order);
            
            // 100(고가 주문) + 200(2백만원/만원) = 300
            assertThat(priority).isGreaterThanOrEqualTo(300);
        }
        
        @Test
        @DisplayName("주문 금액에 따라 우선순위가 증가한다")
        void priorityByAmount() {
            when(pricingPolicy.getVipThreshold()).thenReturn(new BigDecimal("10000000")); // 천만원
            
            Order order1 = Order.create(customerId);
            order1.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", 1, Money.of(100000, "KRW"));
            
            Order order2 = Order.create(customerId);
            order2.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", 1, Money.of(500000, "KRW"));
            
            int priority1 = orderDomainService.calculateOrderPriority(order1);
            int priority2 = orderDomainService.calculateOrderPriority(order2);
            
            assertThat(priority2).isGreaterThan(priority1);
        }
    }
    
    @Nested
    @DisplayName("반복 주문 패턴 감지")
    class RepeatOrderDetection {
        
        @Test
        @DisplayName("동일한 상품 조합으로 완료된 주문이 있으면 반복 주문이다")
        void detectRepeatOrder() {
            List<ProductId> productIds = Arrays.asList(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                ProductId.of("550e8400-e29b-41d4-a716-446655440002")
            );
            
            Order previousOrder = Order.create(customerId);
            previousOrder.addItem(productIds.get(0), "상품1", 1, Money.of(10000, "KRW"));
            previousOrder.addItem(productIds.get(1), "상품2", 1, Money.of(20000, "KRW"));
            // 주문 완료 상태로 만들기
            previousOrder.confirm();
            previousOrder.markAsPaymentPending();
            previousOrder.markAsPaymentProcessing();
            previousOrder.markAsPaid();
            previousOrder.markAsPreparing();
            previousOrder.markAsShipped();
            previousOrder.markAsDelivered();
            previousOrder.complete();
            
            when(orderRepository.findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(customerId), any(LocalDateTime.class)
            )).thenReturn(Collections.singletonList(previousOrder));
            
            boolean isRepeat = orderDomainService.isRepeatOrder(customerId, productIds);
            
            assertThat(isRepeat).isTrue();
        }
        
        @Test
        @DisplayName("다른 상품 조합이면 반복 주문이 아니다")
        void notRepeatOrderDifferentProducts() {
            List<ProductId> newProductIds = Arrays.asList(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                ProductId.of("550e8400-e29b-41d4-a716-446655440003") // 다른 상품
            );
            
            Order previousOrder = Order.create(customerId);
            previousOrder.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품1", 1, Money.of(10000, "KRW"));
            previousOrder.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440002"), "상품2", 1, Money.of(20000, "KRW"));
            previousOrder.confirm();
            previousOrder.markAsPaymentPending();
            previousOrder.markAsPaymentProcessing();
            previousOrder.markAsPaid();
            previousOrder.markAsPreparing();
            previousOrder.markAsShipped();
            previousOrder.markAsDelivered();
            previousOrder.complete();
            
            when(orderRepository.findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(customerId), any(LocalDateTime.class)
            )).thenReturn(Collections.singletonList(previousOrder));
            
            boolean isRepeat = orderDomainService.isRepeatOrder(customerId, newProductIds);
            
            assertThat(isRepeat).isFalse();
        }
        
        @Test
        @DisplayName("완료되지 않은 주문은 반복 주문 판단에서 제외된다")
        void excludeNonCompletedOrders() {
            List<ProductId> productIds = Arrays.asList(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"),
                ProductId.of("550e8400-e29b-41d4-a716-446655440002")
            );
            
            Order cancelledOrder = Order.create(customerId);
            cancelledOrder.addItem(productIds.get(0), "상품1", 1, Money.of(10000, "KRW"));
            cancelledOrder.addItem(productIds.get(1), "상품2", 1, Money.of(20000, "KRW"));
            cancelledOrder.cancel("고객 취소");
            
            when(orderRepository.findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(customerId), any(LocalDateTime.class)
            )).thenReturn(Collections.singletonList(cancelledOrder));
            
            boolean isRepeat = orderDomainService.isRepeatOrder(customerId, productIds);
            
            assertThat(isRepeat).isFalse();
        }
    }
    
    @Nested
    @DisplayName("주문 통계 계산")
    class OrderStatisticsCalculation {
        
        @Test
        @DisplayName("완료된 주문들의 통계를 계산할 수 있다")
        void calculateStatistics() {
            Order order1 = createCompletedOrder(customerId, Money.of(100000, "KRW"));
            Order order2 = createCompletedOrder(customerId, Money.of(200000, "KRW"));
            Order order3 = createCompletedOrder(customerId, Money.of(300000, "KRW"));
            
            when(orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED))
                .thenReturn(Arrays.asList(order1, order2, order3));
            
            OrderDomainService.OrderStatistics stats = 
                orderDomainService.calculateCustomerOrderStatistics(customerId);
            
            assertThat(stats.getTotalOrders()).isEqualTo(3);
            assertThat(stats.getTotalAmount()).isEqualTo(Money.of(600000, "KRW"));
            assertThat(stats.getAverageAmount()).isEqualTo(Money.of(200000, "KRW"));
            assertThat(stats.getMaxAmount()).isEqualTo(Money.of(300000, "KRW"));
        }
        
        @Test
        @DisplayName("완료된 주문이 없으면 빈 통계를 반환한다")
        void emptyStatistics() {
            when(orderRepository.findByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED))
                .thenReturn(Collections.emptyList());
            
            OrderDomainService.OrderStatistics stats = 
                orderDomainService.calculateCustomerOrderStatistics(customerId);
            
            assertThat(stats.getTotalOrders()).isEqualTo(0);
            assertThat(stats.getTotalAmount()).isEqualTo(Money.ZERO("KRW"));
            assertThat(stats.getAverageAmount()).isEqualTo(Money.ZERO("KRW"));
            assertThat(stats.getMaxAmount()).isEqualTo(Money.ZERO("KRW"));
        }
        
        private Order createCompletedOrder(CustomerId customerId, Money totalAmount) {
            Order order = Order.create(customerId);
            order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품", 1, totalAmount);
            // 완료 상태로 만들기
            order.confirm();
            order.markAsPaymentPending();
            order.markAsPaymentProcessing();
            order.markAsPaid();
            order.markAsPreparing();
            order.markAsShipped();
            order.markAsDelivered();
            order.complete();
            return order;
        }
    }
}