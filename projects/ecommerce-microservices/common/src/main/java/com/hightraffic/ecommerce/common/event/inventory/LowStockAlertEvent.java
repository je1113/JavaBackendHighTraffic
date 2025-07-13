package com.hightraffic.ecommerce.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 재고 부족 경고 이벤트
 * 재고가 설정된 임계값 이하로 떨어졌을 때 발행되는 이벤트
 */
public class LowStockAlertEvent extends DomainEvent {
    
    @JsonProperty("alertId")
    @NotBlank(message = "경고 ID는 필수입니다")
    private final String alertId;
    
    @JsonProperty("alertLevel")
    @NotBlank(message = "경고 레벨은 필수입니다")
    private final String alertLevel; // CRITICAL, WARNING, INFO
    
    @JsonProperty("alertType")
    @NotBlank(message = "경고 타입은 필수입니다")
    private final String alertType; // BELOW_MINIMUM, BELOW_REORDER_POINT, STOCKOUT, PREDICTED_STOCKOUT
    
    @JsonProperty("lowStockItems")
    @NotEmpty(message = "재고 부족 항목은 최소 1개 이상이어야 합니다")
    @Valid
    private final List<LowStockItem> lowStockItems;
    
    @JsonProperty("totalAffectedProducts")
    @Positive(message = "영향받은 상품 수는 0보다 커야 합니다")
    private final int totalAffectedProducts;
    
    @JsonProperty("totalValueAtRisk")
    private final Double totalValueAtRisk;
    
    @JsonProperty("recommendedActions")
    private final List<String> recommendedActions;
    
    @JsonProperty("notificationSent")
    private final boolean notificationSent;
    
    @JsonProperty("notifiedParties")
    private final List<String> notifiedParties;
    
    @JsonProperty("previousAlertTime")
    private final Instant previousAlertTime;
    
    @JsonProperty("alertFrequency")
    private final String alertFrequency; // FIRST_TIME, RECURRING, ESCALATED
    
    public LowStockAlertEvent(String inventoryId, String alertId, String alertLevel,
                             String alertType, List<LowStockItem> lowStockItems,
                             int totalAffectedProducts, Double totalValueAtRisk,
                             List<String> recommendedActions, boolean notificationSent,
                             List<String> notifiedParties, Instant previousAlertTime,
                             String alertFrequency) {
        super(inventoryId);
        this.alertId = alertId;
        this.alertLevel = alertLevel;
        this.alertType = alertType;
        this.lowStockItems = lowStockItems != null ? List.copyOf(lowStockItems) : List.of();
        this.totalAffectedProducts = totalAffectedProducts;
        this.totalValueAtRisk = totalValueAtRisk;
        this.recommendedActions = recommendedActions != null ? List.copyOf(recommendedActions) : List.of();
        this.notificationSent = notificationSent;
        this.notifiedParties = notifiedParties != null ? List.copyOf(notifiedParties) : List.of();
        this.previousAlertTime = previousAlertTime;
        this.alertFrequency = alertFrequency;
    }
    
    @JsonCreator
    public LowStockAlertEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("alertId") String alertId,
                             @JsonProperty("alertLevel") String alertLevel,
                             @JsonProperty("alertType") String alertType,
                             @JsonProperty("lowStockItems") List<LowStockItem> lowStockItems,
                             @JsonProperty("totalAffectedProducts") int totalAffectedProducts,
                             @JsonProperty("totalValueAtRisk") Double totalValueAtRisk,
                             @JsonProperty("recommendedActions") List<String> recommendedActions,
                             @JsonProperty("notificationSent") boolean notificationSent,
                             @JsonProperty("notifiedParties") List<String> notifiedParties,
                             @JsonProperty("previousAlertTime") Instant previousAlertTime,
                             @JsonProperty("alertFrequency") String alertFrequency) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.alertId = alertId;
        this.alertLevel = alertLevel;
        this.alertType = alertType;
        this.lowStockItems = lowStockItems != null ? List.copyOf(lowStockItems) : List.of();
        this.totalAffectedProducts = totalAffectedProducts;
        this.totalValueAtRisk = totalValueAtRisk;
        this.recommendedActions = recommendedActions != null ? List.copyOf(recommendedActions) : List.of();
        this.notificationSent = notificationSent;
        this.notifiedParties = notifiedParties != null ? List.copyOf(notifiedParties) : List.of();
        this.previousAlertTime = previousAlertTime;
        this.alertFrequency = alertFrequency;
    }
    
    public String getAlertId() {
        return alertId;
    }
    
    public String getAlertLevel() {
        return alertLevel;
    }
    
    public String getAlertType() {
        return alertType;
    }
    
    public List<LowStockItem> getLowStockItems() {
        return lowStockItems;
    }
    
    public int getTotalAffectedProducts() {
        return totalAffectedProducts;
    }
    
    public Double getTotalValueAtRisk() {
        return totalValueAtRisk;
    }
    
    public List<String> getRecommendedActions() {
        return recommendedActions;
    }
    
    public boolean isNotificationSent() {
        return notificationSent;
    }
    
    public List<String> getNotifiedParties() {
        return notifiedParties;
    }
    
    public Instant getPreviousAlertTime() {
        return previousAlertTime;
    }
    
    public String getAlertFrequency() {
        return alertFrequency;
    }
    
    public String getInventoryId() {
        return getAggregateId();
    }
    
    /**
     * 긴급 경고인지 확인
     */
    public boolean isCritical() {
        return "CRITICAL".equals(alertLevel);
    }
    
    /**
     * 재고 고갈 경고인지 확인
     */
    public boolean isStockout() {
        return "STOCKOUT".equals(alertType);
    }
    
    /**
     * 첫 번째 경고인지 확인
     */
    public boolean isFirstAlert() {
        return "FIRST_TIME".equals(alertFrequency);
    }
    
    /**
     * 에스컬레이션된 경고인지 확인
     */
    public boolean isEscalated() {
        return "ESCALATED".equals(alertFrequency);
    }
    
    /**
     * 이전 경고 이후 경과 시간 (밀리초)
     */
    public Long getTimeSincePreviousAlert() {
        return previousAlertTime != null ? 
                getTimestamp().toEpochMilli() - previousAlertTime.toEpochMilli() : null;
    }
    
    /**
     * 완전 품절된 상품 수 계산
     */
    public int getStockoutCount() {
        return (int) lowStockItems.stream()
                .filter(item -> item.getCurrentStock() == 0)
                .count();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        LowStockAlertEvent that = (LowStockAlertEvent) obj;
        return totalAffectedProducts == that.totalAffectedProducts &&
               notificationSent == that.notificationSent &&
               Objects.equals(alertId, that.alertId) &&
               Objects.equals(alertLevel, that.alertLevel) &&
               Objects.equals(alertType, that.alertType) &&
               Objects.equals(lowStockItems, that.lowStockItems) &&
               Objects.equals(totalValueAtRisk, that.totalValueAtRisk) &&
               Objects.equals(recommendedActions, that.recommendedActions) &&
               Objects.equals(notifiedParties, that.notifiedParties) &&
               Objects.equals(previousAlertTime, that.previousAlertTime) &&
               Objects.equals(alertFrequency, that.alertFrequency);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), alertId, alertLevel, alertType, lowStockItems,
                          totalAffectedProducts, totalValueAtRisk, recommendedActions,
                          notificationSent, notifiedParties, previousAlertTime, alertFrequency);
    }
    
    @Override
    public String toString() {
        return String.format("LowStockAlertEvent{alertId='%s', level='%s', type='%s', affectedProducts=%d, stockouts=%d}", 
                alertId, alertLevel, alertType, totalAffectedProducts, getStockoutCount());
    }
    
    /**
     * 재고 부족 항목 데이터 클래스
     */
    public static class LowStockItem {
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("warehouseId")
        @NotBlank(message = "창고 ID는 필수입니다")
        private final String warehouseId;
        
        @JsonProperty("currentStock")
        @PositiveOrZero(message = "현재 재고는 0 이상이어야 합니다")
        private final int currentStock;
        
        @JsonProperty("minimumStock")
        @Positive(message = "최소 재고는 0보다 커야 합니다")
        private final int minimumStock;
        
        @JsonProperty("reorderPoint")
        @Positive(message = "재주문점은 0보다 커야 합니다")
        private final int reorderPoint;
        
        @JsonProperty("optimalStock")
        @Positive(message = "최적 재고는 0보다 커야 합니다")
        private final int optimalStock;
        
        @JsonProperty("dailyAverageUsage")
        private final Double dailyAverageUsage;
        
        @JsonProperty("daysUntilStockout")
        private final Integer daysUntilStockout;
        
        @JsonProperty("pendingOrders")
        @PositiveOrZero(message = "대기 주문은 0 이상이어야 합니다")
        private final int pendingOrders;
        
        @JsonProperty("incomingStock")
        @PositiveOrZero(message = "입고 예정 재고는 0 이상이어야 합니다")
        private final int incomingStock;
        
        @JsonProperty("expectedDeliveryDate")
        private final Instant expectedDeliveryDate;
        
        @JsonProperty("lastSoldDate")
        private final Instant lastSoldDate;
        
        @JsonProperty("stockStatus")
        @NotBlank(message = "재고 상태는 필수입니다")
        private final String stockStatus; // OUT_OF_STOCK, CRITICALLY_LOW, LOW, BELOW_REORDER
        
        @JsonCreator
        public LowStockItem(@JsonProperty("productId") String productId,
                          @JsonProperty("productName") String productName,
                          @JsonProperty("warehouseId") String warehouseId,
                          @JsonProperty("currentStock") int currentStock,
                          @JsonProperty("minimumStock") int minimumStock,
                          @JsonProperty("reorderPoint") int reorderPoint,
                          @JsonProperty("optimalStock") int optimalStock,
                          @JsonProperty("dailyAverageUsage") Double dailyAverageUsage,
                          @JsonProperty("daysUntilStockout") Integer daysUntilStockout,
                          @JsonProperty("pendingOrders") int pendingOrders,
                          @JsonProperty("incomingStock") int incomingStock,
                          @JsonProperty("expectedDeliveryDate") Instant expectedDeliveryDate,
                          @JsonProperty("lastSoldDate") Instant lastSoldDate,
                          @JsonProperty("stockStatus") String stockStatus) {
            this.productId = productId;
            this.productName = productName;
            this.warehouseId = warehouseId;
            this.currentStock = currentStock;
            this.minimumStock = minimumStock;
            this.reorderPoint = reorderPoint;
            this.optimalStock = optimalStock;
            this.dailyAverageUsage = dailyAverageUsage;
            this.daysUntilStockout = daysUntilStockout;
            this.pendingOrders = pendingOrders;
            this.incomingStock = incomingStock;
            this.expectedDeliveryDate = expectedDeliveryDate;
            this.lastSoldDate = lastSoldDate;
            this.stockStatus = stockStatus;
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
        
        public int getCurrentStock() {
            return currentStock;
        }
        
        public int getMinimumStock() {
            return minimumStock;
        }
        
        public int getReorderPoint() {
            return reorderPoint;
        }
        
        public int getOptimalStock() {
            return optimalStock;
        }
        
        public Double getDailyAverageUsage() {
            return dailyAverageUsage;
        }
        
        public Integer getDaysUntilStockout() {
            return daysUntilStockout;
        }
        
        public int getPendingOrders() {
            return pendingOrders;
        }
        
        public int getIncomingStock() {
            return incomingStock;
        }
        
        public Instant getExpectedDeliveryDate() {
            return expectedDeliveryDate;
        }
        
        public Instant getLastSoldDate() {
            return lastSoldDate;
        }
        
        public String getStockStatus() {
            return stockStatus;
        }
        
        /**
         * 재고가 완전히 소진되었는지 확인
         */
        public boolean isOutOfStock() {
            return currentStock == 0;
        }
        
        /**
         * 재주문이 필요한지 확인
         */
        public boolean needsReorder() {
            return currentStock <= reorderPoint;
        }
        
        /**
         * 재고 부족률 계산 (%)
         */
        public double getStockShortagePercentage() {
            if (minimumStock == 0) return 0;
            return Math.max(0, (1 - (double) currentStock / minimumStock) * 100);
        }
        
        /**
         * 예상 재고 (현재 + 입고예정 - 대기주문)
         */
        public int getProjectedStock() {
            return currentStock + incomingStock - pendingOrders;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            LowStockItem that = (LowStockItem) obj;
            return currentStock == that.currentStock &&
                   minimumStock == that.minimumStock &&
                   reorderPoint == that.reorderPoint &&
                   optimalStock == that.optimalStock &&
                   pendingOrders == that.pendingOrders &&
                   incomingStock == that.incomingStock &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(warehouseId, that.warehouseId) &&
                   Objects.equals(dailyAverageUsage, that.dailyAverageUsage) &&
                   Objects.equals(daysUntilStockout, that.daysUntilStockout) &&
                   Objects.equals(expectedDeliveryDate, that.expectedDeliveryDate) &&
                   Objects.equals(lastSoldDate, that.lastSoldDate) &&
                   Objects.equals(stockStatus, that.stockStatus);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(productId, productName, warehouseId, currentStock, minimumStock,
                              reorderPoint, optimalStock, dailyAverageUsage, daysUntilStockout,
                              pendingOrders, incomingStock, expectedDeliveryDate, lastSoldDate, stockStatus);
        }
        
        @Override
        public String toString() {
            return String.format("LowStockItem{productId='%s', warehouse='%s', current=%d, min=%d, status='%s'}", 
                    productId, warehouseId, currentStock, minimumStock, stockStatus);
        }
    }
}