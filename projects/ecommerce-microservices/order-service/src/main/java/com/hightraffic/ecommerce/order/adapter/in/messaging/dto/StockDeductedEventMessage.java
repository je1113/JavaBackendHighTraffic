package com.hightraffic.ecommerce.order.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 재고 차감 완료 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 재고 차감 완료 이벤트
 */
public record StockDeductedEventMessage(
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
    
    @JsonProperty("deductedItems")
    List<DeductedItemMessage> deductedItems,
    
    @JsonProperty("deductionReason")
    String deductionReason
) {
    
    /**
     * 차감된 재고 항목 메시지
     */
    public record DeductedItemMessage(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("deductedQuantity")
        BigDecimal deductedQuantity,
        
        @JsonProperty("remainingQuantity")
        BigDecimal remainingQuantity,
        
        @JsonProperty("reservationId")
        String reservationId
    ) {
        
        /**
         * 재고가 완전히 소진되었는지 확인
         */
        public boolean isStockExhausted() {
            return remainingQuantity.compareTo(BigDecimal.ZERO) <= 0;
        }
        
        /**
         * 낮은 재고 상태인지 확인 (임계값: 10개)
         */
        public boolean isLowStock() {
            return remainingQuantity.compareTo(BigDecimal.valueOf(10)) <= 0;
        }
    }
    
    /**
     * 모든 재고 차감이 성공했는지 확인
     */
    public boolean isFullyDeducted() {
        return deductedItems != null && !deductedItems.isEmpty();
    }
    
    /**
     * 특정 상품의 차감 정보 조회
     */
    public DeductedItemMessage getDeductedItem(String productId) {
        return deductedItems.stream()
            .filter(item -> productId.equals(item.productId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 총 차감된 아이템 수
     */
    public int getTotalDeductedItemCount() {
        return deductedItems != null ? deductedItems.size() : 0;
    }
    
    /**
     * 낮은 재고 상태인 상품들 조회
     */
    public List<DeductedItemMessage> getLowStockItems() {
        return deductedItems.stream()
            .filter(DeductedItemMessage::isLowStock)
            .toList();
    }
}