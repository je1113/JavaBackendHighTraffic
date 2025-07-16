package com.hightraffic.ecommerce.inventory.domain.model;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import com.hightraffic.ecommerce.common.event.inventory.LowStockAlertEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockAdjustedEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReleasedEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 상품 Aggregate Root
 * 
 * 책임:
 * - 재고 예약/차감/복원 오케스트레이션
 * - 동시성 제어 로직
 * - 도메인 이벤트 발행
 * - 비즈니스 규칙 적용
 */
public class Product {
    
    private final ProductId productId;
    private String productName;
    private Stock stock;
    private StockQuantity lowStockThreshold;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
    private Long version;
    
    // 도메인 이벤트 저장
    private final List<DomainEvent> domainEvents = new ArrayList<>();
    
    public Product(ProductId productId, String productName, StockQuantity initialStock) {
        this.productId = Objects.requireNonNull(productId, "Product ID cannot be null");
        this.productName = Objects.requireNonNull(productName, "Product name cannot be null");
        this.stock = new Stock(Objects.requireNonNull(initialStock, "Initial stock cannot be null"));
        this.lowStockThreshold = StockQuantity.of(10); // 기본 임계값
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
        this.version = 0L;
        
        validateProductName(productName);
    }
    
    /**
     * 재고 예약
     * 
     * @param quantity 예약할 수량
     * @param orderId 주문 ID (예약 추적용)
     * @return 예약 ID
     * @throws InsufficientStockException 재고 부족 시
     * @throws InvalidStockOperationException 비활성 상품일 때
     */
    public ReservationId reserveStock(StockQuantity quantity, String orderId) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        
        validateActiveProduct();
        
        ReservationId reservationId = ReservationId.generate();
        
        try {
            StockReservation reservation = stock.reserveStock(reservationId, quantity);
            updateModificationTime();
            
            // 도메인 이벤트 발행
            List<StockReservedEvent.ReservedItem> reservedItems = List.of(
                new StockReservedEvent.ReservedItem(
                    productId.getValue().toString(),
                    productName,
                    quantity.getValue(),
                    "MAIN_WAREHOUSE", // warehouseId
                    "AVAILABLE", // reservedFrom
                    0.0  // unitPrice
                )
            );
            
            addDomainEvent(new StockReservedEvent(
                productId.getValue().toString(),
                reservation.getReservationId().getValue().toString(),
                orderId,
                "", // customerId - 기본값
                reservedItems,
                java.time.Instant.now().plusSeconds(900), // 15분 후 만료
                "IMMEDIATE",
                1 // priority
            ));
            
            // 낮은 재고 알림 확인
            checkLowStockAlert();
            
            return reservationId;
            
        } catch (InsufficientStockException e) {
            // 재고 부족 시 관련 이벤트 발행 가능
            throw e;
        }
    }
    
    /**
     * 예약 해제 (재고 복원)
     * 
     * @param reservationId 해제할 예약 ID
     * @param orderId 주문 ID
     */
    public void releaseReservation(ReservationId reservationId, String orderId) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        
        StockReservation reservation = stock.getReservation(reservationId);
        if (reservation == null) {
            // 이미 해제되었거나 존재하지 않는 예약
            return;
        }
        
        StockQuantity releasedQuantity = reservation.getQuantity();
        stock.releaseReservation(reservationId);
        updateModificationTime();
        
        // 도메인 이벤트 발행  
        List<StockReleasedEvent.ReleasedItem> releasedItems = List.of(
            new StockReleasedEvent.ReleasedItem(
                productId.getValue().toString(),
                productName,
                releasedQuantity.getValue(),
                "MAIN_WAREHOUSE", // warehouseId
                "ORDER_CANCELLED", // releaseReason
                stock.getAvailableQuantity().getValue(), // availableAfterRelease
                "Release reason: Order cancelled" // notes
            )
        );
        
        addDomainEvent(new StockReleasedEvent(
            productId.getValue().toString(),
            reservationId.getValue().toString(),
            orderId,
            "", // customerId - 기본값
            "ORDER_CANCELLED",
            releasedItems,
            "Order cancellation",
            "SYSTEM",
            true, // isFullyReleased
            java.time.Instant.now()
        ));
    }
    
    /**
     * 재고 차감 (예약 확정)
     * 
     * @param reservationId 차감할 예약 ID
     * @param orderId 주문 ID
     */
    public void deductStock(ReservationId reservationId, String orderId) {
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        
        StockReservation reservation = stock.getReservation(reservationId);
        if (reservation == null) {
            throw new InvalidStockOperationException("Cannot deduct: reservation not found " + reservationId);
        }
        
        StockQuantity deductedQuantity = reservation.getQuantity();
        stock.deductStock(reservationId);
        updateModificationTime();
        
        // 재고 차감 이벤트는 별도 이벤트 타입 필요시 추가
        // 현재는 예약 해제와 구분하여 처리
    }
    
    /**
     * 재고 직접 차감 (예약 없이)
     * 
     * @param quantity 차감할 수량
     * @param reason 차감 사유
     * @throws InsufficientStockException 재고 부족 시
     */
    public void deductStockDirectly(StockQuantity quantity, String reason) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        
        validateActiveProduct();
        
        stock.deductStockDirectly(quantity);
        updateModificationTime();
        
        // 낮은 재고 알림 확인
        checkLowStockAlert();
    }
    
    /**
     * 재고 추가 (입고)
     * 
     * @param quantity 추가할 수량
     * @param reason 입고 사유
     */
    public void addStock(StockQuantity quantity, String reason) {
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        
        StockQuantity previousTotal = stock.getTotalQuantity();
        stock.addStock(quantity);
        updateModificationTime();
        
        // 도메인 이벤트 발행
        List<StockAdjustedEvent.AdjustedItem> adjustedItems = List.of(
            new StockAdjustedEvent.AdjustedItem(
                productId.getValue().toString(),
                productName,
                "MAIN_WAREHOUSE", // warehouseId
                "KG", // unit
                previousTotal.getValue(),
                stock.getTotalQuantity().getValue(),
                stock.getTotalQuantity().getValue() - previousTotal.getValue(),
                "STOCK_ADDITION",
                java.time.Instant.now(),
                0.0, // costPerUnit
                0.0  // totalCost
            )
        );
        
        addDomainEvent(new StockAdjustedEvent(
            productId.getValue().toString(),
            "", // adjustmentId - 기본값
            "INBOUND",
            reason,
            "", // referenceNumber
            adjustedItems,
            "", // adjustedBy
            "", // approvedBy
            "", // locationCode
            "", // batchNumber
            "", // supplierReference
            true, // isSystemGenerated
            "" // notes
        ));
    }
    
    /**
     * 재고 조정
     * 
     * @param newTotalQuantity 새로운 총 재고 수량
     * @param reason 조정 사유
     */
    public void adjustStock(StockQuantity newTotalQuantity, String reason) {
        Objects.requireNonNull(newTotalQuantity, "New total quantity cannot be null");
        Objects.requireNonNull(reason, "Reason cannot be null");
        
        StockQuantity previousTotal = stock.getTotalQuantity();
        stock.adjustStock(newTotalQuantity);
        updateModificationTime();
        
        // 도메인 이벤트 발행
        List<StockAdjustedEvent.AdjustedItem> adjustedItems = List.of(
            new StockAdjustedEvent.AdjustedItem(
                productId.getValue().toString(),
                productName,
                "MAIN_WAREHOUSE", // warehouseId
                "KG", // unit
                previousTotal.getValue(),
                stock.getTotalQuantity().getValue(),
                stock.getTotalQuantity().getValue() - previousTotal.getValue(),
                "STOCK_ADJUSTMENT",
                java.time.Instant.now(),
                0.0, // costPerUnit
                0.0  // totalCost
            )
        );
        
        addDomainEvent(new StockAdjustedEvent(
            productId.getValue().toString(),
            "", // adjustmentId - 기본값
            "ADJUSTMENT",
            reason,
            "", // referenceNumber
            adjustedItems,
            "", // adjustedBy
            "", // approvedBy
            "", // locationCode
            "", // batchNumber
            "", // supplierReference
            true, // isSystemGenerated
            "" // notes
        ));
        
        // 낮은 재고 알림 확인
        checkLowStockAlert();
    }
    
    /**
     * 만료된 예약 정리
     */
    public void cleanupExpiredReservations() {
        int beforeReservationCount = stock.getReservationCount();
        stock.cleanupExpiredReservations();
        int afterReservationCount = stock.getReservationCount();
        
        if (beforeReservationCount > afterReservationCount) {
            updateModificationTime();
            // 만료된 예약으로 인한 재고 복원 이벤트 발행 가능
        }
    }
    
    /**
     * 상품 활성화/비활성화
     */
    public void activate() {
        this.isActive = true;
        updateModificationTime();
    }
    
    public void deactivate() {
        this.isActive = false;
        updateModificationTime();
    }
    
    /**
     * 낮은 재고 임계값 설정
     */
    public void setLowStockThreshold(StockQuantity threshold) {
        this.lowStockThreshold = Objects.requireNonNull(threshold, "Threshold cannot be null");
        updateModificationTime();
        
        // 현재 재고가 새로운 임계값보다 낮으면 알림
        checkLowStockAlert();
    }
    
    /**
     * 낮은 재고 임계값 업데이트 (Persistence Adapter 호환용)
     */
    public void updateLowStockThreshold(StockQuantity threshold) {
        setLowStockThreshold(threshold);
    }
    
    /**
     * 버전 설정 (Persistence Adapter 호환용)
     */
    public void setVersion(Long version) {
        this.version = version;
    }
    
    /**
     * ID getter (Persistence Adapter 호환용)
     */
    public ProductId getId() {
        return productId;
    }
    
    /**
     * 상품명 변경
     */
    public void updateProductName(String newName) {
        validateProductName(newName);
        this.productName = newName;
        updateModificationTime();
    }
    
    /**
     * 재고 여부 확인
     */
    public boolean isStockAvailable(StockQuantity quantity) {
        return isActive && stock.isStockAvailable(quantity);
    }
    
    /**
     * 재고 부족 여부
     */
    public boolean isOutOfStock() {
        return stock.isOutOfStock();
    }
    
    /**
     * 낮은 재고 여부
     */
    public boolean isLowStock() {
        return stock.isLowStock(lowStockThreshold);
    }
    
    /**
     * 특정 예약 정보 조회
     */
    public StockReservation getReservation(ReservationId reservationId) {
        return stock.getReservation(reservationId);
    }
    
    /**
     * 도메인 이벤트 조회 및 정리
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return events;
    }
    
    // Private methods
    
    private void validateActiveProduct() {
        if (!isActive) {
            throw new InvalidStockOperationException("Cannot perform stock operations on inactive product: " + productId);
        }
    }
    
    private void validateProductName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("Product name cannot exceed 255 characters");
        }
    }
    
    private void updateModificationTime() {
        this.lastModifiedAt = LocalDateTime.now();
        this.version++;
    }
    
    private void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }
    
    private void checkLowStockAlert() {
        if (isLowStock()) {
            List<LowStockAlertEvent.LowStockItem> lowStockItems = List.of(
                new LowStockAlertEvent.LowStockItem(
                    productId.getValue().toString(), // productId
                    productName, // productName
                    "MAIN_WAREHOUSE", // warehouseId
                    stock.getAvailableQuantity().getValue(), // currentStock
                    lowStockThreshold.getValue(), // minimumStock
                    lowStockThreshold.getValue(), // reorderPoint
                    lowStockThreshold.getValue() * 2, // optimalStock
                    0.0, // dailyAverageUsage
                    null, // daysUntilStockout
                    0, // pendingOrders
                    0, // incomingStock
                    null, // expectedDeliveryDate
                    null, // lastSoldDate
                    "LOW" // stockStatus
                )
            );
            
            addDomainEvent(new LowStockAlertEvent(
                productId.getValue().toString(), // inventoryId
                "LOW_STOCK_ALERT", // alertId
                "WARNING", // alertLevel
                "BELOW_MINIMUM", // alertType
                lowStockItems, // lowStockItems
                1, // totalAffectedProducts
                0.0, // totalValueAtRisk
                List.of("재주문 필요"), // recommendedActions
                false, // notificationSent
                List.of("inventory-manager"), // notifiedParties
                null, // previousAlertTime
                "FIRST_TIME" // alertFrequency
            ));
        }
    }
    
    // Getters
    public ProductId getProductId() {
        return productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public Stock getStock() {
        return stock;
    }
    
    public StockQuantity getLowStockThreshold() {
        return lowStockThreshold;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastModifiedAt() {
        return lastModifiedAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    public StockQuantity getAvailableQuantity() {
        return stock.getAvailableQuantity();
    }
    
    public StockQuantity getReservedQuantity() {
        return stock.getReservedQuantity();
    }
    
    public StockQuantity getTotalQuantity() {
        return stock.getTotalQuantity();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }
    
    @Override
    public String toString() {
        return String.format("Product{id=%s, name='%s', available=%d, reserved=%d, total=%d, active=%s}", 
            productId, productName, 
            stock.getAvailableQuantity().getValue(),
            stock.getReservedQuantity().getValue(), 
            stock.getTotalQuantity().getValue(),
            isActive);
    }
}