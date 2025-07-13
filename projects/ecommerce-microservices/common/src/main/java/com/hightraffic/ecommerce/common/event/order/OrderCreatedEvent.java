package com.hightraffic.ecommerce.common.event.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주문 생성 이벤트
 * 고객이 새로운 주문을 생성했을 때 발행되는 이벤트
 */
public class OrderCreatedEvent extends DomainEvent {
    
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    private final String customerId;
    
    @JsonProperty("orderItems")
    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private final List<OrderItemData> orderItems;
    
    @JsonProperty("totalAmount")
    @NotNull(message = "총 주문 금액은 필수입니다")
    @Positive(message = "총 주문 금액은 0보다 커야 합니다")
    private final BigDecimal totalAmount;
    
    @JsonProperty("currency")
    @NotBlank(message = "통화 코드는 필수입니다")
    private final String currency;
    
    public OrderCreatedEvent(String orderId, String customerId, 
                           List<OrderItemData> orderItems, 
                           BigDecimal totalAmount, String currency) {
        super(orderId);
        this.customerId = customerId;
        this.orderItems = List.copyOf(orderItems); // 불변 리스트 생성
        this.totalAmount = totalAmount;
        this.currency = currency;
    }
    
    @JsonCreator
    public OrderCreatedEvent(@JsonProperty("eventId") String eventId,
                           @JsonProperty("eventType") String eventType,
                           @JsonProperty("timestamp") Instant timestamp,
                           @JsonProperty("version") int version,
                           @JsonProperty("aggregateId") String aggregateId,
                           @JsonProperty("customerId") String customerId,
                           @JsonProperty("orderItems") List<OrderItemData> orderItems,
                           @JsonProperty("totalAmount") BigDecimal totalAmount,
                           @JsonProperty("currency") String currency) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.customerId = customerId;
        this.orderItems = orderItems != null ? List.copyOf(orderItems) : List.of();
        this.totalAmount = totalAmount;
        this.currency = currency;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public List<OrderItemData> getOrderItems() {
        return orderItems;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getOrderId() {
        return getAggregateId();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        OrderCreatedEvent that = (OrderCreatedEvent) obj;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(orderItems, that.orderItems) &&
               Objects.equals(totalAmount, that.totalAmount) &&
               Objects.equals(currency, that.currency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customerId, orderItems, totalAmount, currency);
    }
    
    /**
     * 주문 항목 데이터 클래스
     */
    public static class OrderItemData {
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("quantity")
        @Positive(message = "수량은 0보다 커야 합니다")
        private final int quantity;
        
        @JsonProperty("unitPrice")
        @NotNull(message = "단가는 필수입니다")
        @Positive(message = "단가는 0보다 커야 합니다")
        private final BigDecimal unitPrice;
        
        @JsonProperty("totalPrice")
        @NotNull(message = "총 가격은 필수입니다")
        @Positive(message = "총 가격은 0보다 커야 합니다")
        private final BigDecimal totalPrice;
        
        @JsonCreator
        public OrderItemData(@JsonProperty("productId") String productId,
                           @JsonProperty("productName") String productName,
                           @JsonProperty("quantity") int quantity,
                           @JsonProperty("unitPrice") BigDecimal unitPrice,
                           @JsonProperty("totalPrice") BigDecimal totalPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
            this.totalPrice = totalPrice;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public BigDecimal getUnitPrice() {
            return unitPrice;
        }
        
        public BigDecimal getTotalPrice() {
            return totalPrice;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            OrderItemData that = (OrderItemData) obj;
            return quantity == that.quantity &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(unitPrice, that.unitPrice) &&
                   Objects.equals(totalPrice, that.totalPrice);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productId, productName, quantity, unitPrice, totalPrice);
        }
        
        @Override
        public String toString() {
            return String.format("OrderItemData{productId='%s', productName='%s', quantity=%d, unitPrice=%s, totalPrice=%s}", 
                    productId, productName, quantity, unitPrice, totalPrice);
        }
    }
}