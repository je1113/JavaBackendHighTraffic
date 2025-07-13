package com.hightraffic.ecommerce.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 재고 조정 이벤트
 * 재고 실사, 손실, 입고, 반품 등으로 재고가 조정되었을 때 발행되는 이벤트
 */
public class StockAdjustedEvent extends DomainEvent {
    
    @JsonProperty("adjustmentId")
    @NotBlank(message = "조정 ID는 필수입니다")
    private final String adjustmentId;
    
    @JsonProperty("adjustmentType")
    @NotBlank(message = "조정 타입은 필수입니다")
    private final String adjustmentType; // INBOUND, RETURN, DAMAGE, LOSS, FOUND, CORRECTION, TRANSFER
    
    @JsonProperty("adjustmentReason")
    @NotBlank(message = "조정 사유는 필수입니다")
    private final String adjustmentReason;
    
    @JsonProperty("adjustmentReasonCode")
    @NotBlank(message = "조정 사유 코드는 필수입니다")
    private final String adjustmentReasonCode;
    
    @JsonProperty("adjustedItems")
    @NotEmpty(message = "조정 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private final List<AdjustedItem> adjustedItems;
    
    @JsonProperty("adjustedBy")
    @NotBlank(message = "조정자는 필수입니다")
    private final String adjustedBy;
    
    @JsonProperty("adjustedByType")
    @NotBlank(message = "조정자 타입은 필수입니다")
    private final String adjustedByType; // SYSTEM, EMPLOYEE, SUPPLIER, CUSTOMER
    
    @JsonProperty("sourceDocumentId")
    private final String sourceDocumentId; // 입고 문서 ID, 반품 ID, 실사 ID 등
    
    @JsonProperty("sourceDocumentType")
    private final String sourceDocumentType; // PURCHASE_ORDER, RETURN_ORDER, INVENTORY_COUNT
    
    @JsonProperty("approvedBy")
    private final String approvedBy;
    
    @JsonProperty("approvalRequired")
    private final boolean approvalRequired;
    
    @JsonProperty("notes")
    private final String notes;
    
    public StockAdjustedEvent(String inventoryId, String adjustmentId, String adjustmentType,
                             String adjustmentReason, String adjustmentReasonCode,
                             List<AdjustedItem> adjustedItems, String adjustedBy,
                             String adjustedByType, String sourceDocumentId,
                             String sourceDocumentType, String approvedBy,
                             boolean approvalRequired, String notes) {
        super(inventoryId);
        this.adjustmentId = adjustmentId;
        this.adjustmentType = adjustmentType;
        this.adjustmentReason = adjustmentReason;
        this.adjustmentReasonCode = adjustmentReasonCode;
        this.adjustedItems = adjustedItems != null ? List.copyOf(adjustedItems) : List.of();
        this.adjustedBy = adjustedBy;
        this.adjustedByType = adjustedByType;
        this.sourceDocumentId = sourceDocumentId;
        this.sourceDocumentType = sourceDocumentType;
        this.approvedBy = approvedBy;
        this.approvalRequired = approvalRequired;
        this.notes = notes;
    }
    
    @JsonCreator
    public StockAdjustedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("adjustmentId") String adjustmentId,
                             @JsonProperty("adjustmentType") String adjustmentType,
                             @JsonProperty("adjustmentReason") String adjustmentReason,
                             @JsonProperty("adjustmentReasonCode") String adjustmentReasonCode,
                             @JsonProperty("adjustedItems") List<AdjustedItem> adjustedItems,
                             @JsonProperty("adjustedBy") String adjustedBy,
                             @JsonProperty("adjustedByType") String adjustedByType,
                             @JsonProperty("sourceDocumentId") String sourceDocumentId,
                             @JsonProperty("sourceDocumentType") String sourceDocumentType,
                             @JsonProperty("approvedBy") String approvedBy,
                             @JsonProperty("approvalRequired") boolean approvalRequired,
                             @JsonProperty("notes") String notes) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.adjustmentId = adjustmentId;
        this.adjustmentType = adjustmentType;
        this.adjustmentReason = adjustmentReason;
        this.adjustmentReasonCode = adjustmentReasonCode;
        this.adjustedItems = adjustedItems != null ? List.copyOf(adjustedItems) : List.of();
        this.adjustedBy = adjustedBy;
        this.adjustedByType = adjustedByType;
        this.sourceDocumentId = sourceDocumentId;
        this.sourceDocumentType = sourceDocumentType;
        this.approvedBy = approvedBy;
        this.approvalRequired = approvalRequired;
        this.notes = notes;
    }
    
    public String getAdjustmentId() {
        return adjustmentId;
    }
    
    public String getAdjustmentType() {
        return adjustmentType;
    }
    
    public String getAdjustmentReason() {
        return adjustmentReason;
    }
    
    public String getAdjustmentReasonCode() {
        return adjustmentReasonCode;
    }
    
    public List<AdjustedItem> getAdjustedItems() {
        return adjustedItems;
    }
    
    public String getAdjustedBy() {
        return adjustedBy;
    }
    
    public String getAdjustedByType() {
        return adjustedByType;
    }
    
    public String getSourceDocumentId() {
        return sourceDocumentId;
    }
    
    public String getSourceDocumentType() {
        return sourceDocumentType;
    }
    
    public String getApprovedBy() {
        return approvedBy;
    }
    
    public boolean isApprovalRequired() {
        return approvalRequired;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public String getInventoryId() {
        return getAggregateId();
    }
    
    /**
     * 입고 조정인지 확인
     */
    public boolean isInboundAdjustment() {
        return "INBOUND".equals(adjustmentType);
    }
    
    /**
     * 손실 조정인지 확인
     */
    public boolean isLossAdjustment() {
        return "DAMAGE".equals(adjustmentType) || "LOSS".equals(adjustmentType);
    }
    
    /**
     * 승인되었는지 확인
     */
    public boolean isApproved() {
        return !approvalRequired || (approvedBy != null && !approvedBy.isEmpty());
    }
    
    /**
     * 순 재고 변동량 계산 (양수: 증가, 음수: 감소)
     */
    public int getNetQuantityChange() {
        return adjustedItems.stream()
                .mapToInt(item -> item.getQuantityChange())
                .sum();
    }
    
    /**
     * 영향받은 상품 수 계산
     */
    public int getAffectedProductCount() {
        return (int) adjustedItems.stream()
                .map(AdjustedItem::getProductId)
                .distinct()
                .count();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        StockAdjustedEvent that = (StockAdjustedEvent) obj;
        return approvalRequired == that.approvalRequired &&
               Objects.equals(adjustmentId, that.adjustmentId) &&
               Objects.equals(adjustmentType, that.adjustmentType) &&
               Objects.equals(adjustmentReason, that.adjustmentReason) &&
               Objects.equals(adjustmentReasonCode, that.adjustmentReasonCode) &&
               Objects.equals(adjustedItems, that.adjustedItems) &&
               Objects.equals(adjustedBy, that.adjustedBy) &&
               Objects.equals(adjustedByType, that.adjustedByType) &&
               Objects.equals(sourceDocumentId, that.sourceDocumentId) &&
               Objects.equals(sourceDocumentType, that.sourceDocumentType) &&
               Objects.equals(approvedBy, that.approvedBy) &&
               Objects.equals(notes, that.notes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), adjustmentId, adjustmentType, adjustmentReason,
                          adjustmentReasonCode, adjustedItems, adjustedBy, adjustedByType,
                          sourceDocumentId, sourceDocumentType, approvedBy, approvalRequired, notes);
    }
    
    @Override
    public String toString() {
        return String.format("StockAdjustedEvent{adjustmentId='%s', type='%s', reason='%s', adjustedBy='%s', items=%d, netChange=%d}", 
                adjustmentId, adjustmentType, adjustmentReason, adjustedBy, adjustedItems.size(), getNetQuantityChange());
    }
    
    /**
     * 조정된 재고 항목 데이터 클래스
     */
    public static class AdjustedItem {
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("warehouseId")
        @NotBlank(message = "창고 ID는 필수입니다")
        private final String warehouseId;
        
        @JsonProperty("locationId")
        private final String locationId; // 창고 내 위치
        
        @JsonProperty("quantityBefore")
        @NotNull(message = "조정 전 수량은 필수입니다")
        private final int quantityBefore;
        
        @JsonProperty("quantityAfter")
        @NotNull(message = "조정 후 수량은 필수입니다")
        private final int quantityAfter;
        
        @JsonProperty("quantityChange")
        @NotNull(message = "수량 변경값은 필수입니다")
        private final int quantityChange; // 양수: 증가, 음수: 감소
        
        @JsonProperty("lotNumber")
        private final String lotNumber;
        
        @JsonProperty("expirationDate")
        private final Instant expirationDate;
        
        @JsonProperty("unitCost")
        private final Double unitCost;
        
        @JsonProperty("totalValue")
        private final Double totalValue;
        
        @JsonCreator
        public AdjustedItem(@JsonProperty("productId") String productId,
                          @JsonProperty("productName") String productName,
                          @JsonProperty("warehouseId") String warehouseId,
                          @JsonProperty("locationId") String locationId,
                          @JsonProperty("quantityBefore") int quantityBefore,
                          @JsonProperty("quantityAfter") int quantityAfter,
                          @JsonProperty("quantityChange") int quantityChange,
                          @JsonProperty("lotNumber") String lotNumber,
                          @JsonProperty("expirationDate") Instant expirationDate,
                          @JsonProperty("unitCost") Double unitCost,
                          @JsonProperty("totalValue") Double totalValue) {
            this.productId = productId;
            this.productName = productName;
            this.warehouseId = warehouseId;
            this.locationId = locationId;
            this.quantityBefore = quantityBefore;
            this.quantityAfter = quantityAfter;
            this.quantityChange = quantityChange;
            this.lotNumber = lotNumber;
            this.expirationDate = expirationDate;
            this.unitCost = unitCost;
            this.totalValue = totalValue;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public String getWarehouseId() {
            return warehouseId;
        }
        
        public String getLocationId() {
            return locationId;
        }
        
        public int getQuantityBefore() {
            return quantityBefore;
        }
        
        public int getQuantityAfter() {
            return quantityAfter;
        }
        
        public int getQuantityChange() {
            return quantityChange;
        }
        
        public String getLotNumber() {
            return lotNumber;
        }
        
        public Instant getExpirationDate() {
            return expirationDate;
        }
        
        public Double getUnitCost() {
            return unitCost;
        }
        
        public Double getTotalValue() {
            return totalValue;
        }
        
        /**
         * 재고가 증가했는지 확인
         */
        public boolean isIncrease() {
            return quantityChange > 0;
        }
        
        /**
         * 재고가 감소했는지 확인
         */
        public boolean isDecrease() {
            return quantityChange < 0;
        }
        
        /**
         * 유효기간이 있는지 확인
         */
        public boolean hasExpiration() {
            return expirationDate != null;
        }
        
        /**
         * 유효기간이 지났는지 확인
         */
        public boolean isExpired() {
            return hasExpiration() && Instant.now().isAfter(expirationDate);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            AdjustedItem that = (AdjustedItem) obj;
            return quantityBefore == that.quantityBefore &&
                   quantityAfter == that.quantityAfter &&
                   quantityChange == that.quantityChange &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(warehouseId, that.warehouseId) &&
                   Objects.equals(locationId, that.locationId) &&
                   Objects.equals(lotNumber, that.lotNumber) &&
                   Objects.equals(expirationDate, that.expirationDate) &&
                   Objects.equals(unitCost, that.unitCost) &&
                   Objects.equals(totalValue, that.totalValue);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productId, productName, warehouseId, locationId,
                              quantityBefore, quantityAfter, quantityChange, lotNumber,
                              expirationDate, unitCost, totalValue);
        }
        
        @Override
        public String toString() {
            return String.format("AdjustedItem{productId='%s', warehouse='%s', %d->%d (change=%+d)}", 
                    productId, warehouseId, quantityBefore, quantityAfter, quantityChange);
        }
    }
}