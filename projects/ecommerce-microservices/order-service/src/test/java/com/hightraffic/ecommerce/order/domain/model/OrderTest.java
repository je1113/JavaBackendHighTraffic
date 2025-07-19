package com.hightraffic.ecommerce.order.domain.model;

import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Order 도메인 모델 테스트")
class OrderTest {

    private CustomerId customerId;
    private ProductId productId1;
    private ProductId productId2;
    
    @BeforeEach
    void setUp() {
        customerId = CustomerId.of("550e8400-e29b-41d4-a716-446655440000");
        productId1 = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
        productId2 = ProductId.of("550e8400-e29b-41d4-a716-446655440002");
    }
    
    
    @Nested
    @DisplayName("주문 생성")
    class OrderCreation {
        
        @Test
        @DisplayName("새로운 주문을 생성할 수 있다")
        void createOrder() {
            Order order = Order.create(customerId);
            
            assertThat(order).isNotNull();
            assertThat(order.getId()).isNotNull();
            assertThat(order.getCustomerId()).isEqualTo(customerId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getTotalAmount()).isEqualTo(Money.ZERO("KRW"));
            assertThat(order.getItems()).isEmpty();
            assertThat(order.getNotes()).isEmpty();
        }
    }
    
    @Nested
    @DisplayName("주문 아이템 관리")
    class OrderItemManagement {
        
        private Order order;
        
        @BeforeEach
        void setUp() {
            order = Order.create(customerId);
        }
        
        @Test
        @DisplayName("주문에 아이템을 추가할 수 있다")
        void addItem() {
            Money unitPrice = Money.of(10000, "KRW");
            
            order.addItem(productId1, "상품1", 2, unitPrice);
            
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.getItemCount()).isEqualTo(1);
            assertThat(order.getTotalQuantity()).isEqualTo(2);
            assertThat(order.getTotalAmount()).isEqualTo(Money.of(20000, "KRW"));
            assertThat(order.hasItem(productId1)).isTrue();
        }
        
        @Test
        @DisplayName("여러 개의 아이템을 추가할 수 있다")
        void addMultipleItems() {
            order.addItem(productId1, "상품1", 2, Money.of(10000, "KRW"));
            order.addItem(productId2, "상품2", 3, Money.of(5000, "KRW"));
            
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalQuantity()).isEqualTo(5);
            assertThat(order.getTotalAmount()).isEqualTo(Money.of(35000, "KRW"));
        }
        
        @Test
        @DisplayName("이미 추가된 상품을 다시 추가하면 예외가 발생한다")
        void addDuplicateItem() {
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
            
            assertThatThrownBy(() -> 
                order.addItem(productId1, "상품1", 2, Money.of(10000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미 주문에 포함된 상품입니다");
        }
        
        @Test
        @DisplayName("최대 아이템 개수를 초과하면 예외가 발생한다")
        void exceedMaxItems() {
            for (int i = 1; i <= 100; i++) {
                // UUID 형식을 맞추기 위해 16진수로 변환하여 사용
                String productIdStr = String.format("550e8400-e29b-41d4-a716-44665544%04x", i);
                order.addItem(ProductId.of(productIdStr), "상품" + i, 1, Money.of(1000, "KRW"));
            }
            
            assertThatThrownBy(() -> 
                order.addItem(ProductId.of("550e8400-e29b-41d4-a716-446655440065"), "상품101", 1, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("주문 아이템은 100개를 초과할 수 없습니다");
        }
        
        @Test
        @DisplayName("PENDING 상태가 아닌 주문은 아이템을 추가할 수 없다")
        void cannotAddItemToNonPendingOrder() {
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
            order.confirm();
            
            assertThatThrownBy(() -> 
                order.addItem(productId2, "상품2", 1, Money.of(5000, "KRW"))
            )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("대기 상태의 주문만 수정할 수 있습니다");
        }
        
        @Test
        @DisplayName("주문에서 아이템을 제거할 수 있다")
        void removeItem() {
            order.addItem(productId1, "상품1", 2, Money.of(10000, "KRW"));
            order.addItem(productId2, "상품2", 1, Money.of(5000, "KRW"));
            
            order.removeItem(productId1);
            
            assertThat(order.getItems()).hasSize(1);
            assertThat(order.hasItem(productId1)).isFalse();
            assertThat(order.hasItem(productId2)).isTrue();
            assertThat(order.getTotalAmount()).isEqualTo(Money.of(5000, "KRW"));
        }
        
        @Test
        @DisplayName("존재하지 않는 아이템을 제거하면 예외가 발생한다")
        void removeNonExistentItem() {
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
            
            assertThatThrownBy(() -> order.removeItem(productId2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("해당 상품이 주문에 없습니다");
        }
        
        @Test
        @DisplayName("아이템의 수량을 변경할 수 있다")
        void changeItemQuantity() {
            order.addItem(productId1, "상품1", 2, Money.of(10000, "KRW"));
            
            order.changeItemQuantity(productId1, 5);
            
            assertThat(order.getTotalQuantity()).isEqualTo(5);
            assertThat(order.getTotalAmount()).isEqualTo(Money.of(50000, "KRW"));
        }
    }
    
    @Nested
    @DisplayName("주문 상태 전이")
    class OrderStateTransition {
        
        private Order order;
        
        @BeforeEach
        void setUp() {
            order = Order.create(customerId);
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
        }
        
        @Test
        @DisplayName("주문을 확정할 수 있다")
        void confirmOrder() {
            order.confirm();
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(order.isModifiable()).isFalse();
        }
        
        @Test
        @DisplayName("아이템이 없는 주문은 확정할 수 없다")
        void cannotConfirmEmptyOrder() {
            Order emptyOrder = Order.create(customerId);
            
            assertThatThrownBy(() -> emptyOrder.confirm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("주문 아이템이 없는 주문은 확정할 수 없습니다");
        }
        
        @Test
        @DisplayName("이미 확정된 주문은 다시 확정할 수 없다")
        void cannotConfirmAlreadyConfirmedOrder() {
            order.confirm();
            
            assertThatThrownBy(() -> order.confirm())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 상태에서는 주문을 확정할 수 없습니다");
        }
        
        @Test
        @DisplayName("주문을 취소할 수 있다")
        void cancelOrder() {
            String reason = "고객 변심";
            
            order.cancel(reason);
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(order.getCancellationReason()).isEqualTo(reason);
            assertThat(order.getNotes()).contains("취소 사유: " + reason);
            assertThat(order.isCancellable()).isFalse();
        }
        
        @Test
        @DisplayName("결제 완료 상태로 변경할 수 있다")
        void markAsPaid() {
            order.confirm();
            order.markAsPaymentPending();
            order.markAsPaymentProcessing();
            
            order.markAsPaid();
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.isPaid()).isTrue();
        }
        
        @Test
        @DisplayName("배송 시작 상태로 변경할 수 있다")
        void markAsShipped() {
            order.confirm();
            order.markAsPaymentPending();
            order.markAsPaymentProcessing();
            order.markAsPaid();
            
            order.markAsPreparing();
            order.markAsShipped();
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }
        
        @Test
        @DisplayName("배송 완료 상태로 변경할 수 있다")
        void markAsDelivered() {
            order.confirm();
            order.markAsPaymentPending();
            order.markAsPaymentProcessing();
            order.markAsPaid();
            order.markAsPreparing();
            order.markAsShipped();
            
            order.markAsDelivered();
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        }
        
        @Test
        @DisplayName("주문을 완료할 수 있다")
        void completeOrder() {
            order.confirm();
            order.markAsPaymentPending();
            order.markAsPaymentProcessing();
            order.markAsPaid();
            order.markAsPreparing();
            order.markAsShipped();
            order.markAsDelivered();
            
            order.complete();
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
            assertThat(order.isFinalStatus()).isFalse();
        }
        
        @Test
        @DisplayName("잘못된 상태 전이는 예외가 발생한다")
        void invalidStateTransition() {
            assertThatThrownBy(() -> order.markAsPaid())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("현재 상태에서는 결제할 수 없습니다");
        }
        
        @Test
        @DisplayName("주문 실패 처리를 할 수 있다")
        void markAsFailed() {
            String reason = "재고 부족";
            
            order.markAsFailed(reason);
            
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(order.getNotes()).contains("Order failed: " + reason);
            assertThat(order.isFinalStatus()).isTrue();
        }
    }
    
    @Nested
    @DisplayName("주문 조회 및 검증")
    class OrderQuery {
        
        private Order order;
        
        @BeforeEach
        void setUp() {
            order = Order.create(customerId);
        }
        
        @Test
        @DisplayName("주문이 비어있는지 확인할 수 있다")
        void checkEmptyOrder() {
            assertThat(order.isEmpty()).isTrue();
            
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
            
            assertThat(order.isEmpty()).isFalse();
        }
        
        @Test
        @DisplayName("주문이 수정 가능한지 확인할 수 있다")
        void checkModifiable() {
            assertThat(order.isModifiable()).isTrue();
            assertThat(order.canBeConfirmed()).isFalse(); // 아이템이 없어서
            
            order.addItem(productId1, "상품1", 1, Money.of(10000, "KRW"));
            assertThat(order.canBeConfirmed()).isTrue();
            
            order.confirm();
            assertThat(order.isModifiable()).isFalse();
        }
        
        @Test
        @DisplayName("주문이 활성 상태인지 확인할 수 있다")
        void checkActiveOrder() {
            assertThat(order.isActive()).isTrue();
            
            order.cancel("테스트 취소");
            
            assertThat(order.isActive()).isFalse();
        }
    }
    
    @Nested
    @DisplayName("재고 예약 정보 관리")
    class StockReservationInfo {
        
        private Order order;
        
        @BeforeEach
        void setUp() {
            order = Order.create(customerId);
        }
        
        @Test
        @DisplayName("재고 예약 정보를 추가할 수 있다")
        void addReservationInfo() {
            String reservationId = "RES-001";
            String productId = "550e8400-e29b-41d4-a716-446655440001";
            
            order.addReservationInfo(reservationId, productId);
            
            // 내부 상태는 직접 확인할 수 없으므로 예외가 발생하지 않음을 확인
            assertThat(order).isNotNull();
        }
        
        @Test
        @DisplayName("null 값으로 예약 정보를 추가해도 예외가 발생하지 않는다")
        void addNullReservationInfo() {
            order.addReservationInfo(null, "550e8400-e29b-41d4-a716-446655440001");
            order.addReservationInfo("RES-001", null);
            order.addReservationInfo(null, null);
            
            assertThat(order).isNotNull();
        }
    }
    
    @Nested
    @DisplayName("노트 관리")
    class NotesManagement {
        
        private Order order;
        
        @BeforeEach
        void setUp() {
            order = Order.create(customerId);
        }
        
        @Test
        @DisplayName("노트를 추가할 수 있다")
        void addNotes() {
            order.addNotes("첫 번째 노트");
            order.addNotes("두 번째 노트");
            
            assertThat(order.getNotes()).isEqualTo("첫 번째 노트\n두 번째 노트");
        }
        
        @Test
        @DisplayName("빈 노트는 추가되지 않는다")
        void addEmptyNotes() {
            order.addNotes("");
            order.addNotes("   ");
            order.addNotes(null);
            
            assertThat(order.getNotes()).isEmpty();
        }
    }
}