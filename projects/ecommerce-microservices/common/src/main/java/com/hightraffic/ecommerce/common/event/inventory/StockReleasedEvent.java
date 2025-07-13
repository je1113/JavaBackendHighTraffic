package com.hightraffic.ecommerce.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 재고 해제 이벤트
 * 예약된 재고가 해제되었을 때 발행되는 이벤트 (취소, 만료, 오류 등)
 */
public class StockReleasedEvent extends DomainEvent {
    
    @JsonProperty("reservationId")
    @NotBlank(message = "예약 ID는 필수입니다")
    private final String reservationId;
    
    @JsonProperty("orderId")
    private final String orderId; // nullable - 만료된 경우 null일 수 있음
    
    @JsonProperty("releaseReason")
    @NotBlank(message = "해제 사유는 필수입니다")
    private final String releaseReason; // ORDER_CANCELLED, EXPIRED, PAYMENT_FAILED, SYSTEM_ERROR
    
    @JsonProperty("releaseReasonCode")
    @NotBlank(message = "해제 사유 코드는 필수입니다")
    private final String releaseReasonCode;
    
    @JsonProperty("releasedItems")
    @NotEmpty(message = "해제 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private final List<ReleasedItem> releasedItems;
    
    @JsonProperty("releasedBy")
    @NotBlank(message = "해제 주체는 필수입니다")
    private final String releasedBy;
    
    @JsonProperty("releasedByType")
    @NotBlank(message = "해제 주체 타입은 필수입니다")
    private final String releasedByType; // CUSTOMER, SYSTEM, ADMIN, SCHEDULER
    
    @JsonProperty("compensationRequired")
    private final boolean compensationRequired;
    
    @JsonProperty("originalReservationTime")
    @NotNull(message = "원래 예약 시간은 필수입니다")
    private final Instant originalReservationTime;
    
    public StockReleasedEvent(String inventoryId, String reservationId, String orderId,
                             String releaseReason, String releaseReasonCode,
                             List<ReleasedItem> releasedItems, String releasedBy,
                             String releasedByType, boolean compensationRequired,
                             Instant originalReservationTime) {
        super(inventoryId);
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.releaseReason = releaseReason;
        this.releaseReasonCode = releaseReasonCode;
        this.releasedItems = releasedItems != null ? List.copyOf(releasedItems) : List.of();
        this.releasedBy = releasedBy;
        this.releasedByType = releasedByType;
        this.compensationRequired = compensationRequired;
        this.originalReservationTime = originalReservationTime;
    }
    
    @JsonCreator
    public StockReleasedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("reservationId") String reservationId,
                             @JsonProperty("orderId") String orderId,
                             @JsonProperty("releaseReason") String releaseReason,
                             @JsonProperty("releaseReasonCode") String releaseReasonCode,
                             @JsonProperty("releasedItems") List<ReleasedItem> releasedItems,
                             @JsonProperty("releasedBy") String releasedBy,
                             @JsonProperty("releasedByType") String releasedByType,
                             @JsonProperty("compensationRequired") boolean compensationRequired,
                             @JsonProperty("originalReservationTime") Instant originalReservationTime) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.releaseReason = releaseReason;
        this.releaseReasonCode = releaseReasonCode;
        this.releasedItems = releasedItems != null ? List.copyOf(releasedItems) : List.of();
        this.releasedBy = releasedBy;
        this.releasedByType = releasedByType;
        this.compensationRequired = compensationRequired;
        this.originalReservationTime = originalReservationTime;
    }
    
    public String getReservationId() {
        return reservationId;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getReleaseReason() {
        return releaseReason;
    }
    
    public String getReleaseReasonCode() {
        return releaseReasonCode;
    }
    
    public List<ReleasedItem> getReleasedItems() {
        return releasedItems;
    }
    
    public String getReleasedBy() {
        return releasedBy;
    }
    
    public String getReleasedByType() {
        return releasedByType;
    }
    
    public boolean isCompensationRequired() {
        return compensationRequired;
    }
    
    public Instant getOriginalReservationTime() {
        return originalReservationTime;
    }
    
    public String getInventoryId() {
        return getAggregateId();
    }
    
    /**
     * 주문 취소로 인한 해제인지 확인
     */
    public boolean isOrderCancellation() {
        return "ORDER_CANCELLED".equals(releaseReason);
    }
    
    /**
     * 예약 만료로 인한 해제인지 확인
     */
    public boolean isExpired() {
        return "EXPIRED".equals(releaseReason);
    }
    
    /**
     * 시스템에 의한 자동 해제인지 확인
     */
    public boolean isSystemRelease() {
        return "SYSTEM".equals(releasedByType) || "SCHEDULER".equals(releasedByType);
    }
    
    /**
     * 예약 유지 시간 계산 (밀리초)
     */
    public long getReservationDuration() {
        return getTimestamp().toEpochMilli() - originalReservationTime.toEpochMilli();
    }
    
    /**
     * 해제된 총 수량 계산
     */
    public int getTotalReleasedQuantity() {
        return releasedItems.stream()
                .mapToInt(ReleasedItem::getQuantity)
                .sum();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        StockReleasedEvent that = (StockReleasedEvent) obj;
        return compensationRequired == that.compensationRequired &&
               Objects.equals(reservationId, that.reservationId) &&
               Objects.equals(orderId, that.orderId) &&
               Objects.equals(releaseReason, that.releaseReason) &&
               Objects.equals(releaseReasonCode, that.releaseReasonCode) &&
               Objects.equals(releasedItems, that.releasedItems) &&
               Objects.equals(releasedBy, that.releasedBy) &&
               Objects.equals(releasedByType, that.releasedByType) &&
               Objects.equals(originalReservationTime, that.originalReservationTime);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reservationId, orderId, releaseReason, 
                          releaseReasonCode, releasedItems, releasedBy, releasedByType, 
                          compensationRequired, originalReservationTime);
    }
    
    @Override
    public String toString() {
        return String.format("StockReleasedEvent{reservationId='%s', orderId='%s', reason='%s', releasedBy='%s(%s)', items=%d}", 
                reservationId, orderId, releaseReason, releasedBy, releasedByType, releasedItems.size());
    }
    
    /**
     * 해제된 재고 항목 데이터 클래스
     */
    public static class ReleasedItem {
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("quantity")
        @Positive(message = "해제 수량은 0보다 커야 합니다")
        private final int quantity;
        
        @JsonProperty("warehouseId")
        @NotBlank(message = "창고 ID는 필수입니다")
        private final String warehouseId;
        
        @JsonProperty("returnedTo")
        @NotBlank(message = "반환 위치는 필수입니다")
        private final String returnedTo; // AVAILABLE, SAFETY_STOCK, QUARANTINE
        
        @JsonProperty("availableAfterRelease")
        private final int availableAfterRelease;
        
        @JsonProperty("notes")
        private final String notes;
        
        @JsonCreator
        public ReleasedItem(@JsonProperty("productId") String productId,
                          @JsonProperty("productName") String productName,
                          @JsonProperty("quantity") int quantity,
                          @JsonProperty("warehouseId") String warehouseId,
                          @JsonProperty("returnedTo") String returnedTo,
                          @JsonProperty("availableAfterRelease") int availableAfterRelease,
                          @JsonProperty("notes") String notes) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.warehouseId = warehouseId;
            this.returnedTo = returnedTo;
            this.availableAfterRelease = availableAfterRelease;
            this.notes = notes;
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
        
        public String getWarehouseId() {
            return warehouseId;
        }
        
        public String getReturnedTo() {
            return returnedTo;
        }
        
        public int getAvailableAfterRelease() {
            return availableAfterRelease;
        }
        
        public String getNotes() {
            return notes;
        }
        
        /**
         * 가용 재고로 반환되었는지 확인
         */
        public boolean isReturnedToAvailable() {
            return "AVAILABLE".equals(returnedTo);
        }
        
        /**
         * 격리 재고로 이동했는지 확인
         */
        public boolean isQuarantined() {
            return "QUARANTINE".equals(returnedTo);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ReleasedItem that = (ReleasedItem) obj;
            return quantity == that.quantity &&
                   availableAfterRelease == that.availableAfterRelease &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(warehouseId, that.warehouseId) &&
                   Objects.equals(returnedTo, that.returnedTo) &&
                   Objects.equals(notes, that.notes);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productId, productName, quantity, warehouseId, 
                              returnedTo, availableAfterRelease, notes);
        }
        
        @Override
        public String toString() {
            return String.format("ReleasedItem{productId='%s', quantity=%d, warehouse='%s', returnedTo='%s'}", 
                    productId, quantity, warehouseId, returnedTo);
        }
    }
}