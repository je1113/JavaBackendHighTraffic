package com.hightraffic.ecommerce.order.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 재고 예약 완료 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 재고 예약 완료 이벤트
 */
public record StockReservedEventMessage(
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
    
    @JsonProperty("reservedItems")
    List<ReservedItemMessage> reservedItems,
    
    @JsonProperty("reservationTimeout")
    Duration reservationTimeout
) {
    
    /**
     * 예약된 재고 항목 메시지
     */
    public record ReservedItemMessage(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("reservedQuantity")
        BigDecimal reservedQuantity,
        
        @JsonProperty("reservationId")
        String reservationId
    ) {}
    
    /**
     * 전체 예약이 성공했는지 확인
     */
    public boolean isFullyReserved() {
        return reservedItems != null && !reservedItems.isEmpty();
    }
    
    /**
     * 특정 상품의 예약 정보 조회
     */
    public ReservedItemMessage getReservedItem(String productId) {
        return reservedItems.stream()
            .filter(item -> productId.equals(item.productId()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * 총 예약된 아이템 수
     */
    public int getTotalReservedItemCount() {
        return reservedItems != null ? reservedItems.size() : 0;
    }
}