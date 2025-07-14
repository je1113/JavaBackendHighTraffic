package com.hightraffic.ecommerce.common.event.order;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 주문 결제 완료 이벤트
 * 
 * Order Service에서 발행하고 Shipping Service에서 소비하는 이벤트
 * 주문이 결제되어 배송 준비가 필요함을 알림
 */
public class OrderPaidEvent extends DomainEvent {
    
    private final String orderId;
    private final String customerId;
    private final List<OrderItem> orderItems;
    private final BigDecimal totalAmount;
    private final String transactionId;
    private final LocalDateTime paidAt;
    
    public OrderPaidEvent(String orderId, String customerId, List<OrderItem> orderItems,
                         BigDecimal totalAmount, String transactionId, LocalDateTime paidAt) {
        super();
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID cannot be null");
        this.orderItems = List.copyOf(Objects.requireNonNull(orderItems, "Order items cannot be null"));
        this.totalAmount = Objects.requireNonNull(totalAmount, "Total amount cannot be null");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.paidAt = Objects.requireNonNull(paidAt, "Paid at cannot be null");
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public List<OrderItem> getOrderItems() {
        return orderItems;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public LocalDateTime getPaidAt() {
        return paidAt;
    }
    
    /**
     * 주문 아이템
     */
    public static class OrderItem {
        private final String productId;
        private final String productName;
        private final Integer quantity;
        
        public OrderItem(String productId, String productName, Integer quantity) {
            this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
            this.productName = Objects.requireNonNull(productName, "Product name cannot be null");
            this.quantity = Objects.requireNonNull(quantity, "Quantity cannot be null");
            
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        }
        
        public String getProductId() {
            return productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
    }
    
    @Override
    public String toString() {
        return String.format("OrderPaidEvent{orderId='%s', customerId='%s', itemCount=%d, amount=%s}",
            orderId, customerId, orderItems.size(), totalAmount);
    }
}