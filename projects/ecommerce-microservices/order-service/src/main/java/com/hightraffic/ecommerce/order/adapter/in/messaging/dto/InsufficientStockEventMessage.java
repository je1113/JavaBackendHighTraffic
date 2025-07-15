package com.hightraffic.ecommerce.order.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 재고 부족 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 재고 부족 이벤트
 */
public record InsufficientStockEventMessage(
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
    
    @JsonProperty("insufficientItems")
    List<InsufficientItemMessage> insufficientItems
) {
    
    /**
     * 재고 부족 항목 메시지
     */
    public record InsufficientItemMessage(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("requestedQuantity")
        BigDecimal requestedQuantity,
        
        @JsonProperty("availableQuantity")
        BigDecimal availableQuantity,
        
        @JsonProperty("reason")
        String reason
    ) {
        
        /**
         * 부족한 수량 계산
         */
        public BigDecimal getShortageQuantity() {
            return requestedQuantity.subtract(availableQuantity);
        }
        
        /**
         * 재고가 완전히 없는지 확인
         */
        public boolean isOutOfStock() {
            return availableQuantity.compareTo(BigDecimal.ZERO) <= 0;
        }
    }
    
    /**
     * 모든 상품이 품절인지 확인
     */
    public boolean isCompletelyOutOfStock() {
        return insufficientItems.stream()
            .allMatch(InsufficientItemMessage::isOutOfStock);
    }
    
    /**
     * 부분 재고 부족인지 확인 (일부 상품은 가용)
     */
    public boolean isPartialStock() {
        return insufficientItems.stream()
            .anyMatch(item -> item.availableQuantity().compareTo(BigDecimal.ZERO) > 0);
    }
    
    /**
     * 특정 상품의 부족 정보 조회
     */
    public InsufficientItemMessage getInsufficientItem(String productId) {
        return insufficientItems.stream()
            .filter(item -> productId.equals(item.productId()))
            .findFirst()
            .orElse(null);
    }
}