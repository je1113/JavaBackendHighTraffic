package com.hightraffic.ecommerce.inventory.application.port.in;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 재고 복원 Use Case
 * 
 * 책임:
 * - 예약된 재고 해제
 * - 직접 재고 추가
 * - 만료된 예약 정리
 * 
 * 재고 복원은 주문 취소, 결제 실패 등의 경우
 * 예약된 재고를 다시 사용 가능하게 만드는 보상 트랜잭션
 */
public interface RestoreStockUseCase {
    
    /**
     * 예약 해제 (재고 복원)
     * 
     * @param command 예약 해제 명령
     * @throws ReservationNotFoundException 예약을 찾을 수 없는 경우
     */
    void releaseReservation(@Valid ReleaseReservationCommand command);
    
    /**
     * 다수 예약 일괄 해제
     * 
     * @param command 일괄 해제 명령
     * @return 해제 결과 목록
     */
    List<ReleaseResult> releaseBatchReservations(@Valid ReleaseBatchReservationsCommand command);
    
    /**
     * 재고 직접 추가 (입고 등)
     * 
     * @param command 재고 추가 명령
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     */
    void addStock(@Valid AddStockCommand command);
    
    /**
     * 재고 조정
     * 
     * @param command 재고 조정 명령
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     * @throws InvalidStockAdjustmentException 유효하지 않은 조정인 경우
     */
    void adjustStock(@Valid AdjustStockCommand command);
    
    /**
     * 만료된 예약 정리
     * 
     * @return 정리된 예약 수
     */
    int cleanupExpiredReservations();
    
    /**
     * 예약 해제 명령
     */
    class ReleaseReservationCommand {
        
        @NotNull(message = "Reservation ID is required")
        private final ReservationId reservationId;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        private final String releaseReason;
        
        public ReleaseReservationCommand(ReservationId reservationId, String orderId, 
                                       String releaseReason) {
            this.reservationId = reservationId;
            this.orderId = orderId;
            this.releaseReason = releaseReason;
        }
        
        public ReservationId getReservationId() {
            return reservationId;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public String getReleaseReason() {
            return releaseReason;
        }
    }
    
    /**
     * 일괄 예약 해제 명령
     */
    class ReleaseBatchReservationsCommand {
        
        @NotNull(message = "Release items are required")
        private final List<ReleaseItem> releaseItems;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        private final String releaseReason;
        
        public ReleaseBatchReservationsCommand(List<ReleaseItem> releaseItems, String orderId, 
                                             String releaseReason) {
            this.releaseItems = List.copyOf(releaseItems);
            this.orderId = orderId;
            this.releaseReason = releaseReason;
        }
        
        public List<ReleaseItem> getReleaseItems() {
            return releaseItems;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public String getReleaseReason() {
            return releaseReason;
        }
        
        /**
         * 해제 아이템
         */
        public static class ReleaseItem {
            
            @NotNull(message = "Reservation ID is required")
            private final ReservationId reservationId;
            
            @NotNull(message = "Product ID is required")
            private final ProductId productId;
            
            public ReleaseItem(ReservationId reservationId, ProductId productId) {
                this.reservationId = reservationId;
                this.productId = productId;
            }
            
            public ReservationId getReservationId() {
                return reservationId;
            }
            
            public ProductId getProductId() {
                return productId;
            }
        }
    }
    
    /**
     * 재고 추가 명령
     */
    class AddStockCommand {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        @NotNull(message = "Quantity is required")
        private final StockQuantity quantity;
        
        @NotBlank(message = "Reason is required")
        private final String reason;
        
        private final String referenceNumber;
        
        public AddStockCommand(ProductId productId, StockQuantity quantity, 
                             String reason, String referenceNumber) {
            this.productId = productId;
            this.quantity = quantity;
            this.reason = reason;
            this.referenceNumber = referenceNumber;
        }
        
        public ProductId getProductId() {
            return productId;
        }
        
        public StockQuantity getQuantity() {
            return quantity;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getReferenceNumber() {
            return referenceNumber;
        }
    }
    
    /**
     * 재고 조정 명령
     */
    class AdjustStockCommand {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        @NotNull(message = "New total quantity is required")
        private final StockQuantity newTotalQuantity;
        
        @NotBlank(message = "Reason is required")
        private final String reason;
        
        private final String adjustmentType;
        
        public AdjustStockCommand(ProductId productId, StockQuantity newTotalQuantity, 
                                String reason, String adjustmentType) {
            this.productId = productId;
            this.newTotalQuantity = newTotalQuantity;
            this.reason = reason;
            this.adjustmentType = adjustmentType;
        }
        
        public ProductId getProductId() {
            return productId;
        }
        
        public StockQuantity getNewTotalQuantity() {
            return newTotalQuantity;
        }
        
        public String getReason() {
            return reason;
        }
        
        public String getAdjustmentType() {
            return adjustmentType;
        }
    }
    
    /**
     * 해제 결과
     */
    class ReleaseResult {
        private final ReservationId reservationId;
        private final ProductId productId;
        private final boolean success;
        private final String failureReason;
        
        // 성공 생성자
        public ReleaseResult(ReservationId reservationId, ProductId productId) {
            this.reservationId = reservationId;
            this.productId = productId;
            this.success = true;
            this.failureReason = null;
        }
        
        // 실패 생성자
        public ReleaseResult(ReservationId reservationId, ProductId productId, 
                           String failureReason) {
            this.reservationId = reservationId;
            this.productId = productId;
            this.success = false;
            this.failureReason = failureReason;
        }
        
        // Getters
        public ReservationId getReservationId() { return reservationId; }
        public ProductId getProductId() { return productId; }
        public boolean isSuccess() { return success; }
        public String getFailureReason() { return failureReason; }
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
     * 유효하지 않은 재고 조정
     */
    class InvalidStockAdjustmentException extends RuntimeException {
        private final ProductId productId;
        private final String reason;
        
        public InvalidStockAdjustmentException(ProductId productId, String reason) {
            super(String.format("Invalid stock adjustment for product %s: %s", productId, reason));
            this.productId = productId;
            this.reason = reason;
        }
        
        public ProductId getProductId() { return productId; }
        public String getReason() { return reason; }
    }
}