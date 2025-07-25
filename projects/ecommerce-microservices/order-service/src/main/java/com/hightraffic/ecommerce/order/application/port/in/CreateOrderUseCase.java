package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;

import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 주문 생성 Use Case
 * 
 * 책임:
 * - 신규 주문 생성 요청 처리
 * - 주문 생성 규칙 적용
 * - 주문 생성 결과 반환
 * 
 * 헥사고날 아키텍처의 Inbound Port로서
 * Application Core의 진입점 역할
 */
public interface CreateOrderUseCase {
    
    /**
     * 주문 생성
     * 
     * @param command 주문 생성 명령
     * @return 생성된 주문 정보
     */
    CreateOrderResult createOrder(@Valid CreateOrderCommand command);
    
    /**
     * 주문 생성 명령
     * 
     * 불변 객체로 구현하여 명령의 무결성 보장
     */
    class CreateOrderCommand {
        
        @NotNull(message = "Customer ID is required")
        private final CustomerId customerId;
        
        @NotNull(message = "Order items are required")
        @Size(min = 1, message = "At least one order item is required")
        @Valid
        private final List<OrderItem> orderItems;
        
        private final String orderNote;
        
        public CreateOrderCommand(CustomerId customerId, List<OrderItem> orderItems, String orderNote) {
            this.customerId = customerId;
            this.orderItems = List.copyOf(orderItems); // 방어적 복사
            this.orderNote = orderNote;
        }
        
        public CustomerId getCustomerId() {
            return customerId;
        }
        
        public List<OrderItem> getOrderItems() {
            return orderItems; // 이미 불변 리스트
        }
        
        public String getOrderNote() {
            return orderNote;
        }
        
        /**
         * 주문 항목
         */
        public static class OrderItem {
            
            @NotNull(message = "Product ID is required")
            private final ProductId productId;
            
            @NotNull(message = "Product name is required")
            private final String productName;
            
            @NotNull(message = "Quantity is required")
            @Min(value = 1, message = "Quantity must be at least 1")
            private final Integer quantity;
            
            @NotNull(message = "Unit price is required")
            private final Money unitPrice;
            
            public OrderItem(ProductId productId, String productName, Integer quantity, Money unitPrice) {
                this.productId = productId;
                this.productName = productName;
                this.quantity = quantity;
                this.unitPrice = unitPrice;
            }
            
            public ProductId getProductId() {
                return productId;
            }
            
            public String getProductName() {
                return productName;
            }
            
            public Integer getQuantity() {
                return quantity;
            }
            
            public Money getUnitPrice() {
                return unitPrice;
            }
        }
    }
    
    /**
     * 주문 생성 결과
     */
    class CreateOrderResult {
        private final OrderId orderId;
        private final String message;
        
        public CreateOrderResult(OrderId orderId, String message) {
            this.orderId = orderId;
            this.message = message;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
        
        public String getMessage() {
            return message;
        }
        
        // 호환성을 위한 메서드들
        public OrderId orderId() {
            return orderId;
        }
        
        public String orderNumber() {
            return orderId.getValue();
        }
        
        public CustomerId customerId() {
            return null; // TODO: 실제 customerId 저장 필요
        }
        
        public String status() {
            return "PENDING";
        }
        
        public Money totalAmount() {
            return Money.ZERO("KRW"); // TODO: 실제 totalAmount 저장 필요
        }
        
        public LocalDateTime createdAt() {
            return LocalDateTime.now(); // TODO: 실제 createdAt 저장 필요
        }
    }
}