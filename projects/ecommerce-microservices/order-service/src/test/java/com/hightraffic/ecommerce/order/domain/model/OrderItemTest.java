package com.hightraffic.ecommerce.order.domain.model;

import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderItem 도메인 모델 테스트")
class OrderItemTest {
    
    @Nested
    @DisplayName("OrderItem 생성")
    class OrderItemCreation {
        
        @Test
        @DisplayName("유효한 값으로 OrderItem을 생성할 수 있다")
        void createOrderItem() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            String productName = "테스트 상품";
            int quantity = 3;
            Money unitPrice = Money.of(10000, "KRW");
            
            OrderItem item = OrderItem.create(productId, productName, quantity, unitPrice);
            
            assertThat(item).isNotNull();
            assertThat(item.getProductId()).isEqualTo(productId);
            assertThat(item.getProductName()).isEqualTo(productName);
            assertThat(item.getQuantity()).isEqualTo(quantity);
            assertThat(item.getUnitPrice()).isEqualTo(unitPrice);
            assertThat(item.getTotalPrice()).isEqualTo(Money.of(30000, "KRW"));
        }
        
        @Test
        @DisplayName("null 값으로 OrderItem을 생성하면 예외가 발생한다")
        void createOrderItemWithNull() {
            assertThatThrownBy(() -> 
                OrderItem.create(null, "상품명", 1, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품 ID는 필수입니다");
            
            assertThatThrownBy(() -> 
                OrderItem.create(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), null, 1, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품명은 필수입니다");
            
            assertThatThrownBy(() -> 
                OrderItem.create(ProductId.of("550e8400-e29b-41d4-a716-446655440001"), "상품명", 1, null)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("단가는 필수입니다");
        }
        
        @Test
        @DisplayName("유효하지 않은 수량으로 OrderItem을 생성하면 예외가 발생한다")
        void createOrderItemWithInvalidQuantity() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "상품명", 0, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0보다 커야 합니다");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "상품명", -1, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 0보다 커야 합니다");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "상품명", 1001, Money.of(1000, "KRW"))
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수량은 1000개를 초과할 수 없습니다");
        }
        
        @Test
        @DisplayName("음수 단가로 OrderItem을 생성하면 예외가 발생한다")
        void createOrderItemWithNegativePrice() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            Money negativePrice = Money.of(-1000, "KRW");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "상품명", 1, negativePrice)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("단가는 음수일 수 없습니다");
        }
        
        @Test
        @DisplayName("빈 상품명으로 OrderItem을 생성하면 예외가 발생한다")
        void createOrderItemWithEmptyProductName() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            Money unitPrice = Money.of(1000, "KRW");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "", 1, unitPrice)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품명은 필수입니다");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, "   ", 1, unitPrice)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품명은 필수입니다");
        }
        
        @Test
        @DisplayName("255자를 초과하는 상품명으로 OrderItem을 생성하면 예외가 발생한다")
        void createOrderItemWithTooLongProductName() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            String longName = "A".repeat(256);
            Money unitPrice = Money.of(1000, "KRW");
            
            assertThatThrownBy(() -> 
                OrderItem.create(productId, longName, 1, unitPrice)
            )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("상품명은 255자를 초과할 수 없습니다");
        }
    }
    
    @Nested
    @DisplayName("OrderItem 수정")
    class OrderItemModification {
        
        @Test
        @DisplayName("수량을 변경할 수 있다")
        void changeQuantity() {
            OrderItem item = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "테스트 상품", 
                2, 
                Money.of(10000, "KRW")
            );
            
            item.changeQuantity(5);
            
            assertThat(item.getQuantity()).isEqualTo(5);
            assertThat(item.getTotalPrice()).isEqualTo(Money.of(50000, "KRW"));
        }
        
        @Test
        @DisplayName("유효하지 않은 수량으로 변경하면 예외가 발생한다")
        void changeToInvalidQuantity() {
            OrderItem item = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "테스트 상품", 
                2, 
                Money.of(10000, "KRW")
            );
            
            assertThatThrownBy(() -> item.changeQuantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 0보다 커야 합니다");
            
            assertThatThrownBy(() -> item.changeQuantity(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 0보다 커야 합니다");
            
            assertThatThrownBy(() -> item.changeQuantity(1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수량은 1000개를 초과할 수 없습니다");
        }
        
        @Test
        @DisplayName("단가를 변경할 수 있다")
        void changeUnitPrice() {
            OrderItem item = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "테스트 상품", 
                3, 
                Money.of(10000, "KRW")
            );
            
            Money newPrice = Money.of(15000, "KRW");
            item.changeUnitPrice(newPrice);
            
            assertThat(item.getUnitPrice()).isEqualTo(newPrice);
            assertThat(item.getTotalPrice()).isEqualTo(Money.of(45000, "KRW"));
        }
        
        @Test
        @DisplayName("null 또는 음수 단가로 변경하면 예외가 발생한다")
        void changeToInvalidUnitPrice() {
            OrderItem item = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "테스트 상품", 
                2, 
                Money.of(10000, "KRW")
            );
            
            assertThatThrownBy(() -> item.changeUnitPrice(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("단가는 필수입니다");
            
            assertThatThrownBy(() -> item.changeUnitPrice(Money.of(-1000, "KRW")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("단가는 음수일 수 없습니다");
        }
    }
    
    @Nested
    @DisplayName("OrderItem 동등성")
    class OrderItemEquality {
        
        @Test
        @DisplayName("같은 속성을 가진 OrderItem은 동등하다")
        void equalOrderItems() {
            ProductId productId = ProductId.of("550e8400-e29b-41d4-a716-446655440001");
            String productName = "테스트 상품";
            int quantity = 2;
            Money unitPrice = Money.of(10000, "KRW");
            
            OrderItem item1 = OrderItem.create(productId, productName, quantity, unitPrice);
            OrderItem item2 = OrderItem.create(productId, productName, quantity, unitPrice);
            
            assertThat(item1).isEqualTo(item2);
            assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
        }
        
        @Test
        @DisplayName("다른 속성을 가진 OrderItem은 동등하지 않다")
        void notEqualOrderItems() {
            OrderItem item1 = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "상품1", 
                2, 
                Money.of(10000, "KRW")
            );
            
            OrderItem item2 = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440002"), 
                "상품1", 
                2, 
                Money.of(10000, "KRW")
            );
            
            OrderItem item3 = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "상품2", 
                2, 
                Money.of(10000, "KRW")
            );
            
            OrderItem item4 = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "상품1", 
                3, 
                Money.of(10000, "KRW")
            );
            
            OrderItem item5 = OrderItem.create(
                ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
                "상품1", 
                2, 
                Money.of(20000, "KRW")
            );
            
            assertThat(item1).isNotEqualTo(item2);
            assertThat(item1).isNotEqualTo(item3);
            assertThat(item1).isNotEqualTo(item4);
            assertThat(item1).isNotEqualTo(item5);
        }
    }
    
    @Test
    @DisplayName("OrderItem toString은 주요 정보를 포함한다")
    void toStringContainsKeyInfo() {
        OrderItem item = OrderItem.create(
            ProductId.of("550e8400-e29b-41d4-a716-446655440001"), 
            "테스트 상품", 
            3, 
            Money.of(10000, "KRW")
        );
        
        String str = item.toString();
        
        assertThat(str).contains("550e8400-e29b-41d4-a716-446655440001");
        assertThat(str).contains("테스트 상품");
        assertThat(str).contains("3");
        assertThat(str).contains("10000");
        assertThat(str).contains("30000");
    }
}