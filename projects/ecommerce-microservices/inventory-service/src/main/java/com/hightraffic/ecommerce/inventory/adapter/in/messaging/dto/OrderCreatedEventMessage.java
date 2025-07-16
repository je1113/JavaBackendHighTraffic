package com.hightraffic.ecommerce.inventory.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 주문 생성 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 주문 생성 이벤트
 */
public record OrderCreatedEventMessage(
    @JsonProperty("eventId")
    String eventId,
    
    @JsonProperty("eventType")
    String eventType,
    
    @JsonProperty("timestamp")
    Instant timestamp,
    
    @JsonProperty("version")
    int version,
    
    @JsonProperty("aggregateId")
    String aggregateId,
    
    @JsonProperty("orderId")
    String orderId,
    
    @JsonProperty("customerId")
    String customerId,
    
    @JsonProperty("orderItems")
    List<OrderItemMessage> orderItems,
    
    @JsonProperty("totalAmount")
    BigDecimal totalAmount,
    
    @JsonProperty("currency")
    String currency,
    
    @JsonProperty("createdAt")
    Instant createdAt
) {
    
    /**
     * 주문 아이템 메시지 DTO
     */
    public record OrderItemMessage(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("productName")
        String productName,
        
        @JsonProperty("quantity")
        int quantity,
        
        @JsonProperty("unitPrice")
        BigDecimal unitPrice,
        
        @JsonProperty("totalPrice")
        BigDecimal totalPrice
    ) {
        
        /**
         * 유효한 주문 아이템인지 검증
         */
        public boolean isValid() {
            return productId != null && !productId.isBlank() &&
                   quantity > 0 &&
                   unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0 &&
                   totalPrice != null && totalPrice.compareTo(BigDecimal.ZERO) > 0;
        }
    }
    
    /**
     * 전체 주문 아이템 수량 계산
     */
    public int getTotalQuantity() {
        return orderItems.stream()
                .mapToInt(OrderItemMessage::quantity)
                .sum();
    }
    
    /**
     * 주문 아이템 개수
     */
    public int getItemCount() {
        return orderItems != null ? orderItems.size() : 0;
    }
    
    /**
     * 특정 상품의 주문 수량 조회
     */
    public int getQuantityForProduct(String productId) {
        return orderItems.stream()
                .filter(item -> productId.equals(item.productId()))
                .mapToInt(OrderItemMessage::quantity)
                .sum();
    }
    
    /**
     * 모든 주문 아이템이 유효한지 검증
     */
    public boolean areAllItemsValid() {
        return orderItems != null && 
               !orderItems.isEmpty() &&
               orderItems.stream().allMatch(OrderItemMessage::isValid);
    }
}