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
 * 재고 예약 이벤트
 * 주문 생성 시 재고가 예약되었을 때 발행되는 이벤트
 */
public class StockReservedEvent extends DomainEvent {
    
    @JsonProperty("reservationId")
    @NotBlank(message = "예약 ID는 필수입니다")
    private final String reservationId;
    
    @JsonProperty("orderId")
    @NotBlank(message = "주문 ID는 필수입니다")
    private final String orderId;
    
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    private final String customerId;
    
    @JsonProperty("reservedItems")
    @NotEmpty(message = "예약 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private final List<ReservedItem> reservedItems;
    
    @JsonProperty("expiresAt")
    @NotNull(message = "예약 만료 시간은 필수입니다")
    private final Instant expiresAt;
    
    @JsonProperty("reservationType")
    @NotBlank(message = "예약 타입은 필수입니다")
    private final String reservationType; // IMMEDIATE, PRE_ORDER, BACKORDER
    
    @JsonProperty("priority")
    private final Integer priority;
    
    public StockReservedEvent(String inventoryId, String reservationId, String orderId,
                             String customerId, List<ReservedItem> reservedItems,
                             Instant expiresAt, String reservationType, Integer priority) {
        super(inventoryId);
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.reservedItems = reservedItems != null ? List.copyOf(reservedItems) : List.of();
        this.expiresAt = expiresAt;
        this.reservationType = reservationType;
        this.priority = priority;
    }
    
    @JsonCreator
    public StockReservedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("reservationId") String reservationId,
                             @JsonProperty("orderId") String orderId,
                             @JsonProperty("customerId") String customerId,
                             @JsonProperty("reservedItems") List<ReservedItem> reservedItems,
                             @JsonProperty("expiresAt") Instant expiresAt,
                             @JsonProperty("reservationType") String reservationType,
                             @JsonProperty("priority") Integer priority) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.reservedItems = reservedItems != null ? List.copyOf(reservedItems) : List.of();
        this.expiresAt = expiresAt;
        this.reservationType = reservationType;
        this.priority = priority;
    }
    
    public String getReservationId() {
        return reservationId;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public List<ReservedItem> getReservedItems() {
        return reservedItems;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public String getReservationType() {
        return reservationType;
    }
    
    public Integer getPriority() {
        return priority;
    }
    
    public String getInventoryId() {
        return getAggregateId();
    }
    
    /**
     * 예약이 만료되었는지 확인
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    /**
     * 즉시 예약인지 확인
     */
    public boolean isImmediateReservation() {
        return "IMMEDIATE".equals(reservationType);
    }
    
    /**
     * 예약의 총 수량 계산
     */
    public int getTotalReservedQuantity() {
        return reservedItems.stream()
                .mapToInt(ReservedItem::getQuantity)
                .sum();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        StockReservedEvent that = (StockReservedEvent) obj;
        return Objects.equals(reservationId, that.reservationId) &&
               Objects.equals(orderId, that.orderId) &&
               Objects.equals(customerId, that.customerId) &&
               Objects.equals(reservedItems, that.reservedItems) &&
               Objects.equals(expiresAt, that.expiresAt) &&
               Objects.equals(reservationType, that.reservationType) &&
               Objects.equals(priority, that.priority);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reservationId, orderId, customerId, 
                          reservedItems, expiresAt, reservationType, priority);
    }
    
    @Override
    public String toString() {
        return String.format("StockReservedEvent{reservationId='%s', orderId='%s', customerId='%s', items=%d, type='%s', expires=%s}", 
                reservationId, orderId, customerId, reservedItems.size(), reservationType, expiresAt);
    }
    
    /**
     * 예약된 재고 항목 데이터 클래스
     */
    public static class ReservedItem {
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("quantity")
        @Positive(message = "예약 수량은 0보다 커야 합니다")
        private final int quantity;
        
        @JsonProperty("warehouseId")
        @NotBlank(message = "창고 ID는 필수입니다")
        private final String warehouseId;
        
        @JsonProperty("reservedFrom")
        @NotBlank(message = "예약 출처는 필수입니다")
        private final String reservedFrom; // AVAILABLE, SAFETY_STOCK, INCOMING
        
        @JsonProperty("unitPrice")
        private final Double unitPrice;
        
        @JsonCreator
        public ReservedItem(@JsonProperty("productId") String productId,
                          @JsonProperty("productName") String productName,
                          @JsonProperty("quantity") int quantity,
                          @JsonProperty("warehouseId") String warehouseId,
                          @JsonProperty("reservedFrom") String reservedFrom,
                          @JsonProperty("unitPrice") Double unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.warehouseId = warehouseId;
            this.reservedFrom = reservedFrom;
            this.unitPrice = unitPrice;
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
        
        public String getReservedFrom() {
            return reservedFrom;
        }
        
        public Double getUnitPrice() {
            return unitPrice;
        }
        
        /**
         * 가용 재고에서 예약되었는지 확인
         */
        public boolean isReservedFromAvailable() {
            return "AVAILABLE".equals(reservedFrom);
        }
        
        /**
         * 안전 재고에서 예약되었는지 확인
         */
        public boolean isReservedFromSafetyStock() {
            return "SAFETY_STOCK".equals(reservedFrom);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ReservedItem that = (ReservedItem) obj;
            return quantity == that.quantity &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(warehouseId, that.warehouseId) &&
                   Objects.equals(reservedFrom, that.reservedFrom) &&
                   Objects.equals(unitPrice, that.unitPrice);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productId, productName, quantity, warehouseId, reservedFrom, unitPrice);
        }
        
        @Override
        public String toString() {
            return String.format("ReservedItem{productId='%s', quantity=%d, warehouse='%s', from='%s'}", 
                    productId, quantity, warehouseId, reservedFrom);
        }
    }
}