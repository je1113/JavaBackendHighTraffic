package com.hightraffic.ecommerce.inventory.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * 주문 취소 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 주문 취소 이벤트
 */
public record OrderCancelledEventMessage(
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
    
    @JsonProperty("cancellationReason")
    String cancellationReason,
    
    @JsonProperty("cancelledItems")
    List<CancelledItemMessage> cancelledItems,
    
    @JsonProperty("cancelledAt")
    Instant cancelledAt,
    
    @JsonProperty("cancelledBy")
    String cancelledBy,
    
    @JsonProperty("cancelReason")
    String cancelReason,
    
    @JsonProperty("cancelReasonCode")
    String cancelReasonCode,
    
    @JsonProperty("cancelledByType")
    String cancelledByType,
    
    @JsonProperty("compensationActions")
    List<CompensationActionMessage> compensationActions
) {
    
    /**
     * 보상 액션 메시지 DTO
     */
    public record CompensationActionMessage(
        @JsonProperty("actionType")
        String actionType,
        
        @JsonProperty("targetService")
        String targetService,
        
        @JsonProperty("actionData")
        String actionData,
        
        @JsonProperty("priority")
        int priority
    ) {
        
        /**
         * 유효한 보상 액션인지 검증
         */
        public boolean isValid() {
            return actionType != null && !actionType.isBlank() &&
                   targetService != null && !targetService.isBlank();
        }
    }
    
    /**
     * 취소된 아이템 메시지 DTO
     */
    public record CancelledItemMessage(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("quantity")
        int quantity,
        
        @JsonProperty("reservationId")
        String reservationId
    ) {
        
        /**
         * 유효한 취소 아이템인지 검증
         */
        public boolean isValid() {
            return productId != null && !productId.isBlank() &&
                   quantity > 0 &&
                   reservationId != null && !reservationId.isBlank();
        }
    }
    
    /**
     * 취소 사유 타입 enum
     */
    public enum CancellationReasonType {
        CUSTOMER_REQUEST("고객 요청"),
        INSUFFICIENT_STOCK("재고 부족"),
        PAYMENT_FAILED("결제 실패"),
        SYSTEM_ERROR("시스템 오류"),
        FRAUD_DETECTION("부정거래 탐지");
        
        private final String description;
        
        CancellationReasonType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 전체 취소된 아이템 수량 계산
     */
    public int getTotalCancelledQuantity() {
        return cancelledItems.stream()
                .mapToInt(CancelledItemMessage::quantity)
                .sum();
    }
    
    /**
     * 취소된 아이템 개수
     */
    public int getCancelledItemCount() {
        return cancelledItems != null ? cancelledItems.size() : 0;
    }
    
    /**
     * 특정 상품의 취소 수량 조회
     */
    public int getCancelledQuantityForProduct(String productId) {
        return cancelledItems.stream()
                .filter(item -> productId.equals(item.productId()))
                .mapToInt(CancelledItemMessage::quantity)
                .sum();
    }
    
    /**
     * 고객 요청에 의한 취소인지 확인
     */
    public boolean isCustomerCancellation() {
        return CancellationReasonType.CUSTOMER_REQUEST.name().equals(cancellationReason);
    }
    
    /**
     * 재고 부족으로 인한 취소인지 확인
     */
    public boolean isStockInsufficientCancellation() {
        return CancellationReasonType.INSUFFICIENT_STOCK.name().equals(cancellationReason);
    }
    
    /**
     * 결제 실패로 인한 취소인지 확인
     */
    public boolean isPaymentFailedCancellation() {
        return CancellationReasonType.PAYMENT_FAILED.name().equals(cancellationReason);
    }
    
    /**
     * 모든 취소 아이템이 유효한지 검증
     */
    public boolean areAllCancelledItemsValid() {
        return cancelledItems != null && 
               !cancelledItems.isEmpty() &&
               cancelledItems.stream().allMatch(CancelledItemMessage::isValid);
    }
}