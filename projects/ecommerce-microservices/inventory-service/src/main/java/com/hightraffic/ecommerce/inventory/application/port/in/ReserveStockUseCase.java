package com.hightraffic.ecommerce.inventory.application.port.in;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.List;

/**
 * 재고 예약 Use Case
 * 
 * 책임:
 * - 단일 또는 다수 상품의 재고 예약
 * - 예약 가능 여부 검증
 * - 예약 타임아웃 관리
 * 
 * 재고 예약은 2-Phase Commit 패턴의 첫 단계로,
 * 실제 차감 전에 재고를 확보하는 메커니즘
 */
public interface ReserveStockUseCase {
    
    /**
     * 단일 상품 재고 예약
     * 
     * @param command 재고 예약 명령
     * @return 예약 ID
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     * @throws InsufficientStockException 재고가 부족한 경우
     * @throws InactiveProductException 비활성 상품인 경우
     */
    ReservationId reserveStock(@Valid ReserveStockCommand command);
    
    /**
     * 다수 상품 재고 일괄 예약
     * 
     * @param command 일괄 예약 명령
     * @return 예약 결과 목록
     */
    List<ReservationResult> reserveBatchStock(@Valid ReserveBatchStockCommand command);
    
    /**
     * 재고 예약 명령
     */
    class ReserveStockCommand {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        @NotNull(message = "Quantity is required")
        private final StockQuantity quantity;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        private final Integer reservationMinutes;
        
        public ReserveStockCommand(ProductId productId, StockQuantity quantity, 
                                  String orderId, Integer reservationMinutes) {
            this.productId = productId;
            this.quantity = quantity;
            this.orderId = orderId;
            this.reservationMinutes = reservationMinutes;
        }
        
        public ProductId getProductId() {
            return productId;
        }
        
        public StockQuantity getQuantity() {
            return quantity;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public Integer getReservationMinutes() {
            return reservationMinutes;
        }
    }
    
    /**
     * 일괄 재고 예약 명령
     */
    class ReserveBatchStockCommand {
        
        @NotNull(message = "Stock items are required")
        private final List<StockItem> stockItems;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        private final boolean atomicReservation;
        
        public ReserveBatchStockCommand(List<StockItem> stockItems, String orderId, 
                                       boolean atomicReservation) {
            this.stockItems = List.copyOf(stockItems);
            this.orderId = orderId;
            this.atomicReservation = atomicReservation;
        }
        
        public List<StockItem> getStockItems() {
            return stockItems;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public boolean isAtomicReservation() {
            return atomicReservation;
        }
        
        /**
         * 재고 아이템
         */
        public static class StockItem {
            
            @NotNull(message = "Product ID is required")
            private final ProductId productId;
            
            @NotNull(message = "Quantity is required")
            private final StockQuantity quantity;
            
            public StockItem(ProductId productId, StockQuantity quantity) {
                this.productId = productId;
                this.quantity = quantity;
            }
            
            public ProductId getProductId() {
                return productId;
            }
            
            public StockQuantity getQuantity() {
                return quantity;
            }
        }
    }
    
    /**
     * 예약 결과
     */
    class ReservationResult {
        private final ProductId productId;
        private final boolean success;
        private final ReservationId reservationId;
        private final String failureReason;
        
        // 성공 생성자
        public ReservationResult(ProductId productId, ReservationId reservationId) {
            this.productId = productId;
            this.success = true;
            this.reservationId = reservationId;
            this.failureReason = null;
        }
        
        // 실패 생성자
        public ReservationResult(ProductId productId, String failureReason) {
            this.productId = productId;
            this.success = false;
            this.reservationId = null;
            this.failureReason = failureReason;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public boolean isSuccess() { return success; }
        public ReservationId getReservationId() { return reservationId; }
        public String getFailureReason() { return failureReason; }
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
     * 재고 부족
     */
    class InsufficientStockException extends RuntimeException {
        private final ProductId productId;
        private final StockQuantity requested;
        private final StockQuantity available;
        
        public InsufficientStockException(ProductId productId, StockQuantity requested, 
                                        StockQuantity available) {
            super(String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
                productId, requested.getValue(), available.getValue()));
            this.productId = productId;
            this.requested = requested;
            this.available = available;
        }
        
        public ProductId getProductId() { return productId; }
        public StockQuantity getRequested() { return requested; }
        public StockQuantity getAvailable() { return available; }
    }
    
    /**
     * 비활성 상품
     */
    class InactiveProductException extends RuntimeException {
        private final ProductId productId;
        
        public InactiveProductException(ProductId productId) {
            super("Product is inactive: " + productId);
            this.productId = productId;
        }
        
        public ProductId getProductId() {
            return productId;
        }
    }
}