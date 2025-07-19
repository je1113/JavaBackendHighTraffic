package com.hightraffic.ecommerce.order.domain.model.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus 열거형 테스트")
class OrderStatusTest {
    
    @Nested
    @DisplayName("OrderStatus 생성")
    class OrderStatusCreation {
        
        @Test
        @DisplayName("코드로 OrderStatus를 찾을 수 있다")
        void findByCode() {
            assertThat(OrderStatus.fromCode("PENDING")).isEqualTo(OrderStatus.PENDING);
            assertThat(OrderStatus.fromCode("CONFIRMED")).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(OrderStatus.fromCode("PAID")).isEqualTo(OrderStatus.PAID);
            assertThat(OrderStatus.fromCode("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
        }
        
        @Test
        @DisplayName("유효하지 않은 코드로 찾으면 예외가 발생한다")
        void invalidCode() {
            assertThatThrownBy(() -> OrderStatus.fromCode("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 주문 상태 코드");
        }
        
        @Test
        @DisplayName("모든 상태는 고유한 코드와 설명을 가진다")
        void uniqueCodeAndDescription() {
            for (OrderStatus status : OrderStatus.values()) {
                assertThat(status.getCode()).isNotNull();
                assertThat(status.getDescription()).isNotNull();
                assertThat(status.getOrder()).isGreaterThan(0);
            }
        }
    }
    
    @Nested
    @DisplayName("상태 전이 규칙")
    class StateTransition {
        
        @Test
        @DisplayName("PENDING에서 가능한 전이")
        void pendingTransitions() {
            OrderStatus pending = OrderStatus.PENDING;
            
            assertThat(pending.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
            assertThat(pending.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(pending.canTransitionTo(OrderStatus.FAILED)).isTrue();
            
            assertThat(pending.canTransitionTo(OrderStatus.PAID)).isFalse();
            assertThat(pending.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        }
        
        @Test
        @DisplayName("CONFIRMED에서 가능한 전이")
        void confirmedTransitions() {
            OrderStatus confirmed = OrderStatus.CONFIRMED;
            
            assertThat(confirmed.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
            assertThat(confirmed.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            
            assertThat(confirmed.canTransitionTo(OrderStatus.PAID)).isFalse();
            assertThat(confirmed.canTransitionTo(OrderStatus.PENDING)).isFalse();
        }
        
        @Test
        @DisplayName("PAYMENT_PENDING에서 가능한 전이")
        void paymentPendingTransitions() {
            OrderStatus paymentPending = OrderStatus.PAYMENT_PENDING;
            
            assertThat(paymentPending.canTransitionTo(OrderStatus.PAYMENT_PROCESSING)).isTrue();
            assertThat(paymentPending.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(paymentPending.canTransitionTo(OrderStatus.FAILED)).isTrue();
            
            assertThat(paymentPending.canTransitionTo(OrderStatus.PAID)).isFalse();
        }
        
        @Test
        @DisplayName("PAYMENT_PROCESSING에서 가능한 전이")
        void paymentProcessingTransitions() {
            OrderStatus paymentProcessing = OrderStatus.PAYMENT_PROCESSING;
            
            assertThat(paymentProcessing.canTransitionTo(OrderStatus.PAID)).isTrue();
            assertThat(paymentProcessing.canTransitionTo(OrderStatus.FAILED)).isTrue();
            assertThat(paymentProcessing.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            
            assertThat(paymentProcessing.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        }
        
        @Test
        @DisplayName("PAID에서 가능한 전이")
        void paidTransitions() {
            OrderStatus paid = OrderStatus.PAID;
            
            assertThat(paid.canTransitionTo(OrderStatus.PREPARING)).isTrue();
            assertThat(paid.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
            
            assertThat(paid.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
            assertThat(paid.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        }
        
        @Test
        @DisplayName("PREPARING에서 가능한 전이")
        void preparingTransitions() {
            OrderStatus preparing = OrderStatus.PREPARING;
            
            assertThat(preparing.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
            assertThat(preparing.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            
            assertThat(preparing.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        }
        
        @Test
        @DisplayName("SHIPPED에서 가능한 전이")
        void shippedTransitions() {
            OrderStatus shipped = OrderStatus.SHIPPED;
            
            assertThat(shipped.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
            
            assertThat(shipped.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
            assertThat(shipped.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }
        
        @Test
        @DisplayName("DELIVERED에서 가능한 전이")
        void deliveredTransitions() {
            OrderStatus delivered = OrderStatus.DELIVERED;
            
            assertThat(delivered.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
            assertThat(delivered.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
            
            assertThat(delivered.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }
        
        @Test
        @DisplayName("COMPLETED에서 가능한 전이")
        void completedTransitions() {
            OrderStatus completed = OrderStatus.COMPLETED;
            
            assertThat(completed.canTransitionTo(OrderStatus.REFUNDING)).isTrue();
            
            assertThat(completed.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
            assertThat(completed.canTransitionTo(OrderStatus.PENDING)).isFalse();
        }
        
        @Test
        @DisplayName("REFUNDING에서 가능한 전이")
        void refundingTransitions() {
            OrderStatus refunding = OrderStatus.REFUNDING;
            
            assertThat(refunding.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
            
            assertThat(refunding.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
            assertThat(refunding.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        }
        
        @Test
        @DisplayName("최종 상태에서는 전이가 불가능하다")
        void finalStatesCannotTransition() {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.PENDING)).isFalse();
            assertThat(OrderStatus.REFUNDED.canTransitionTo(OrderStatus.PENDING)).isFalse();
            assertThat(OrderStatus.FAILED.canTransitionTo(OrderStatus.PENDING)).isFalse();
        }
    }
    
    @Nested
    @DisplayName("상태 속성")
    class StateProperties {
        
        @Test
        @DisplayName("취소 가능한 상태")
        void cancellableStates() {
            assertThat(OrderStatus.PENDING.isCancellable()).isTrue();
            assertThat(OrderStatus.CONFIRMED.isCancellable()).isTrue();
            assertThat(OrderStatus.PAYMENT_PROCESSING.isCancellable()).isTrue();
            assertThat(OrderStatus.PAID.isCancellable()).isTrue();
            assertThat(OrderStatus.PREPARING.isCancellable()).isTrue();
            
            assertThat(OrderStatus.SHIPPED.isCancellable()).isFalse();
            assertThat(OrderStatus.DELIVERED.isCancellable()).isFalse();
            assertThat(OrderStatus.COMPLETED.isCancellable()).isFalse();
            assertThat(OrderStatus.CANCELLED.isCancellable()).isFalse();
        }
        
        @Test
        @DisplayName("환불 가능한 상태")
        void refundableStates() {
            assertThat(OrderStatus.PAID.isRefundable()).isTrue();
            assertThat(OrderStatus.PREPARING.isRefundable()).isTrue();
            assertThat(OrderStatus.SHIPPED.isRefundable()).isTrue();
            assertThat(OrderStatus.DELIVERED.isRefundable()).isTrue();
            assertThat(OrderStatus.COMPLETED.isRefundable()).isTrue();
            
            assertThat(OrderStatus.PENDING.isRefundable()).isFalse();
            assertThat(OrderStatus.CONFIRMED.isRefundable()).isFalse();
            assertThat(OrderStatus.CANCELLED.isRefundable()).isFalse();
        }
        
        @Test
        @DisplayName("최종 상태")
        void finalStates() {
            assertThat(OrderStatus.CANCELLED.isFinalStatus()).isTrue();
            assertThat(OrderStatus.REFUNDED.isFinalStatus()).isTrue();
            assertThat(OrderStatus.FAILED.isFinalStatus()).isTrue();
            
            assertThat(OrderStatus.COMPLETED.isFinalStatus()).isFalse();
            assertThat(OrderStatus.PENDING.isFinalStatus()).isFalse();
            assertThat(OrderStatus.PAID.isFinalStatus()).isFalse();
            assertThat(OrderStatus.SHIPPED.isFinalStatus()).isFalse();
        }
        
        @Test
        @DisplayName("결제 완료 상태")
        void paidStates() {
            assertThat(OrderStatus.PAID.isPaid()).isTrue();
            assertThat(OrderStatus.PREPARING.isPaid()).isTrue();
            assertThat(OrderStatus.SHIPPED.isPaid()).isTrue();
            assertThat(OrderStatus.DELIVERED.isPaid()).isTrue();
            assertThat(OrderStatus.COMPLETED.isPaid()).isTrue();
            assertThat(OrderStatus.REFUNDING.isPaid()).isTrue();
            assertThat(OrderStatus.REFUNDED.isPaid()).isTrue();
            
            assertThat(OrderStatus.PENDING.isPaid()).isFalse();
            assertThat(OrderStatus.CONFIRMED.isPaid()).isFalse();
            assertThat(OrderStatus.PAYMENT_PENDING.isPaid()).isFalse();
        }
        
        @Test
        @DisplayName("활성 상태")
        void activeStates() {
            assertThat(OrderStatus.PENDING.isActive()).isTrue();
            assertThat(OrderStatus.CONFIRMED.isActive()).isTrue();
            assertThat(OrderStatus.PAID.isActive()).isTrue();
            assertThat(OrderStatus.SHIPPED.isActive()).isTrue();
            assertThat(OrderStatus.COMPLETED.isActive()).isTrue();
            
            assertThat(OrderStatus.CANCELLED.isActive()).isFalse();
            assertThat(OrderStatus.FAILED.isActive()).isFalse();
            assertThat(OrderStatus.REFUNDED.isActive()).isFalse();
        }
        
        @Test
        @DisplayName("배송 이후 상태")
        void afterShippingStates() {
            assertThat(OrderStatus.DELIVERED.isAfterShipping()).isTrue();
            assertThat(OrderStatus.COMPLETED.isAfterShipping()).isTrue();
            assertThat(OrderStatus.REFUNDING.isAfterShipping()).isTrue();
            assertThat(OrderStatus.REFUNDED.isAfterShipping()).isTrue();
            
            assertThat(OrderStatus.PENDING.isAfterShipping()).isFalse();
            assertThat(OrderStatus.PAID.isAfterShipping()).isFalse();
            assertThat(OrderStatus.SHIPPED.isAfterShipping()).isFalse();
        }
    }
    
    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("모든 상태는 코드를 가지고 있다")
    void allStatusesHaveCode(OrderStatus status) {
        assertThat(status.getCode()).isNotNull();
        assertThat(status.getCode()).isNotEmpty();
    }
    
    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("모든 상태는 설명을 가지고 있다")
    void allStatusesHaveDescription(OrderStatus status) {
        assertThat(status.getDescription()).isNotNull();
        assertThat(status.getDescription()).isNotEmpty();
    }
    
    @Test
    @DisplayName("JsonValue로 직렬화되는 값은 코드다")
    void jsonValue() {
        assertThat(OrderStatus.PENDING.getCode()).isEqualTo("PENDING");
        assertThat(OrderStatus.COMPLETED.getCode()).isEqualTo("COMPLETED");
    }
}