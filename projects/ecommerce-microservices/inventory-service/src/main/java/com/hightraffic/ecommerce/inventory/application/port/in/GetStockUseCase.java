package com.hightraffic.ecommerce.inventory.application.port.in;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고 조회 Use Case
 * 
 * 책임:
 * - 단일 상품 재고 조회
 * - 다수 상품 재고 일괄 조회
 * - 예약 정보 조회
 * - 재고 상태 분석
 * 
 * Query 전용 Use Case로 도메인 상태를 변경하지 않음
 */
public interface GetStockUseCase {
    
    /**
     * 상품 재고 정보 조회
     * 
     * @param query 재고 조회 쿼리
     * @return 재고 정보
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     */
    StockResponse getStock(@Valid GetStockQuery query);
    
    /**
     * 다수 상품 재고 일괄 조회
     * 
     * @param query 일괄 조회 쿼리
     * @return 재고 정보 목록
     */
    List<StockResponse> getBatchStock(@Valid GetBatchStockQuery query);
    
    /**
     * 예약 정보 조회
     * 
     * @param query 예약 조회 쿼리
     * @return 예약 정보
     * @throws ReservationNotFoundException 예약을 찾을 수 없는 경우
     */
    ReservationResponse getReservation(@Valid GetReservationQuery query);
    
    /**
     * 재고 부족 상품 조회
     * 
     * @param query 재고 부족 조회 쿼리
     * @return 재고 부족 상품 목록
     */
    List<LowStockResponse> getLowStockProducts(@Valid GetLowStockQuery query);
    
    /**
     * 재고 조회 쿼리
     */
    class GetStockQuery {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        private final boolean includeReservations;
        
        public GetStockQuery(ProductId productId, boolean includeReservations) {
            this.productId = productId;
            this.includeReservations = includeReservations;
        }
        
        public ProductId getProductId() {
            return productId;
        }
        
        public boolean isIncludeReservations() {
            return includeReservations;
        }
    }
    
    /**
     * 일괄 재고 조회 쿼리
     */
    class GetBatchStockQuery {
        
        @NotNull(message = "Product IDs are required")
        private final List<ProductId> productIds;
        
        private final boolean includeInactive;
        
        public GetBatchStockQuery(List<ProductId> productIds, boolean includeInactive) {
            this.productIds = List.copyOf(productIds);
            this.includeInactive = includeInactive;
        }
        
        public List<ProductId> getProductIds() {
            return productIds;
        }
        
        public boolean isIncludeInactive() {
            return includeInactive;
        }
    }
    
    /**
     * 예약 조회 쿼리
     */
    class GetReservationQuery {
        
        @NotNull(message = "Reservation ID is required")
        private final ReservationId reservationId;
        
        public GetReservationQuery(ReservationId reservationId) {
            this.reservationId = reservationId;
        }
        
        public ReservationId getReservationId() {
            return reservationId;
        }
    }
    
    /**
     * 재고 부족 조회 쿼리
     */
    class GetLowStockQuery {
        
        private final StockQuantity thresholdOverride;
        private final boolean includeInactive;
        private final int limit;
        
        public GetLowStockQuery(StockQuantity thresholdOverride, boolean includeInactive, int limit) {
            this.thresholdOverride = thresholdOverride;
            this.includeInactive = includeInactive;
            this.limit = Math.min(Math.max(1, limit), 1000); // 최대 1000개
        }
        
        public StockQuantity getThresholdOverride() {
            return thresholdOverride;
        }
        
        public boolean isIncludeInactive() {
            return includeInactive;
        }
        
        public int getLimit() {
            return limit;
        }
    }
    
    /**
     * 재고 정보 응답
     */
    class StockResponse {
        private final ProductId productId;
        private final String productName;
        private final StockQuantity availableQuantity;
        private final StockQuantity reservedQuantity;
        private final StockQuantity totalQuantity;
        private final StockQuantity lowStockThreshold;
        private final boolean isActive;
        private final boolean isLowStock;
        private final boolean isOutOfStock;
        private final int reservationCount;
        private final LocalDateTime lastModifiedAt;
        
        public StockResponse(Product product) {
            this.productId = product.getProductId();
            this.productName = product.getProductName();
            this.availableQuantity = product.getAvailableQuantity();
            this.reservedQuantity = product.getReservedQuantity();
            this.totalQuantity = product.getTotalQuantity();
            this.lowStockThreshold = product.getLowStockThreshold();
            this.isActive = product.isActive();
            this.isLowStock = product.isLowStock();
            this.isOutOfStock = product.isOutOfStock();
            this.reservationCount = product.getStock().getReservationCount();
            this.lastModifiedAt = product.getLastModifiedAt();
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public String getProductName() { return productName; }
        public StockQuantity getAvailableQuantity() { return availableQuantity; }
        public StockQuantity getReservedQuantity() { return reservedQuantity; }
        public StockQuantity getTotalQuantity() { return totalQuantity; }
        public StockQuantity getLowStockThreshold() { return lowStockThreshold; }
        public boolean isActive() { return isActive; }
        public boolean isLowStock() { return isLowStock; }
        public boolean isOutOfStock() { return isOutOfStock; }
        public int getReservationCount() { return reservationCount; }
        public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
    }
    
    /**
     * 예약 정보 응답
     */
    class ReservationResponse {
        private final ReservationId reservationId;
        private final ProductId productId;
        private final StockQuantity quantity;
        private final LocalDateTime reservedAt;
        private final LocalDateTime expiresAt;
        private final boolean isExpired;
        private final long remainingMinutes;
        
        public ReservationResponse(StockReservation reservation, ProductId productId) {
            this.reservationId = reservation.getReservationId();
            this.productId = productId;
            this.quantity = reservation.getQuantity();
            this.reservedAt = reservation.getReservedAt();
            this.expiresAt = reservation.getExpiresAt();
            this.isExpired = reservation.isExpired();
            this.remainingMinutes = reservation.getRemainingMinutes();
        }
        
        // Getters
        public ReservationId getReservationId() { return reservationId; }
        public ProductId getProductId() { return productId; }
        public StockQuantity getQuantity() { return quantity; }
        public LocalDateTime getReservedAt() { return reservedAt; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public boolean isExpired() { return isExpired; }
        public long getRemainingMinutes() { return remainingMinutes; }
    }
    
    /**
     * 재고 부족 상품 응답
     */
    class LowStockResponse {
        private final ProductId productId;
        private final String productName;
        private final StockQuantity availableQuantity;
        private final StockQuantity lowStockThreshold;
        private final int stockSeverity; // 0: 충분, 1: 부족, 2: 매우부족, 3: 품절
        private final String recommendedAction;
        
        public LowStockResponse(Product product, int stockSeverity, String recommendedAction) {
            this.productId = product.getProductId();
            this.productName = product.getProductName();
            this.availableQuantity = product.getAvailableQuantity();
            this.lowStockThreshold = product.getLowStockThreshold();
            this.stockSeverity = stockSeverity;
            this.recommendedAction = recommendedAction;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public String getProductName() { return productName; }
        public StockQuantity getAvailableQuantity() { return availableQuantity; }
        public StockQuantity getLowStockThreshold() { return lowStockThreshold; }
        public int getStockSeverity() { return stockSeverity; }
        public String getRecommendedAction() { return recommendedAction; }
    }
    
    /**
     * 상품을 찾을 수 없는 경우
     */
    class ProductNotFoundException extends RuntimeException {
        private final ProductId productId;
        
        public ProductNotFoundException(ProductId productId) {
            super("Product not found: " + productId);
            this.productId = productId;
        }
        
        public ProductId getProductId() {
            return productId;
        }
    }
    
    /**
     * 예약을 찾을 수 없는 경우
     */
    class ReservationNotFoundException extends RuntimeException {
        private final ReservationId reservationId;
        
        public ReservationNotFoundException(ReservationId reservationId) {
            super("Reservation not found: " + reservationId);
            this.reservationId = reservationId;
        }
        
        public ReservationId getReservationId() {
            return reservationId;
        }
    }
}