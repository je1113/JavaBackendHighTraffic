package com.hightraffic.ecommerce.inventory.application.port.in;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 재고 차감 Use Case
 * 
 * 책임:
 * - 예약된 재고의 실제 차감
 * - 예약 없는 직접 차감
 * - 차감 이력 관리
 * 
 * 재고 차감은 2-Phase Commit 패턴의 두 번째 단계로,
 * 예약된 재고를 실제로 차감하거나 직접 차감하는 기능
 */
public interface DeductStockUseCase {
    
    /**
     * 예약된 재고 차감 (예약 확정)
     * 
     * @param command 예약 차감 명령
     * @throws ReservationNotFoundException 예약을 찾을 수 없는 경우
     * @throws InvalidReservationException 유효하지 않은 예약인 경우
     */
    void deductReservedStock(@Valid DeductReservedStockCommand command);
    
    /**
     * 직접 재고 차감 (예약 없이)
     * 
     * @param command 직접 차감 명령
     * @throws ProductNotFoundException 상품을 찾을 수 없는 경우
     * @throws InsufficientStockException 재고가 부족한 경우
     */
    void deductStockDirectly(@Valid DeductStockDirectlyCommand command);
    
    /**
     * 재고 차감 (Controller 호환용)
     */
    void deductStock(@Valid DeductStockCommand command);
    
    /**
     * 다수 예약 일괄 차감
     * 
     * @param command 일괄 차감 명령
     * @return 차감 결과 목록
     */
    List<DeductionResult> deductBatchReservedStock(@Valid DeductBatchReservedStockCommand command);
    
    /**
     * 예약된 재고 차감 명령
     */
    class DeductReservedStockCommand {
        
        @NotNull(message = "Reservation ID is required")
        private final ReservationId reservationId;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        private final String deductionReason;
        
        public DeductReservedStockCommand(ReservationId reservationId, String orderId, 
                                        String deductionReason) {
            this.reservationId = reservationId;
            this.orderId = orderId;
            this.deductionReason = deductionReason;
        }
        
        // Controller 호환용 생성자 (ProductId, reservationId)
        public DeductReservedStockCommand(ProductId productId, String reservationId) {
            this.reservationId = ReservationId.of(reservationId);
            this.orderId = null;
            this.deductionReason = "Payment confirmed";
        }
        
        public ReservationId getReservationId() {
            return reservationId;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public String getDeductionReason() {
            return deductionReason;
        }
    }
    
    /**
     * 직접 재고 차감 명령
     */
    class DeductStockDirectlyCommand {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        @NotNull(message = "Quantity is required")
        private final StockQuantity quantity;
        
        @NotBlank(message = "Deduction reason is required")
        private final String reason;
        
        private final String referenceId;
        
        public DeductStockDirectlyCommand(ProductId productId, StockQuantity quantity, 
                                        String reason, String referenceId) {
            this.productId = productId;
            this.quantity = quantity;
            this.reason = reason;
            this.referenceId = referenceId;
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
        
        public String getReferenceId() {
            return referenceId;
        }
    }
    
    /**
     * 재고 차감 명령 (Controller 호환용)
     */
    class DeductStockCommand {
        
        @NotNull(message = "Product ID is required")
        private final ProductId productId;
        
        @NotNull(message = "Quantity is required")
        private final StockQuantity quantity;
        
        public DeductStockCommand(ProductId productId, StockQuantity quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
        
        public ProductId getProductId() { return productId; }
        public StockQuantity getQuantity() { return quantity; }
    }
    
    /**
     * 일괄 예약 차감 명령
     */
    class DeductBatchReservedStockCommand {
        
        @NotNull(message = "Deduction items are required")
        private final List<DeductionItem> deductionItems;
        
        @NotBlank(message = "Order ID is required")
        private final String orderId;
        
        public DeductBatchReservedStockCommand(List<DeductionItem> deductionItems, String orderId) {
            this.deductionItems = List.copyOf(deductionItems);
            this.orderId = orderId;
        }
        
        public List<DeductionItem> getDeductionItems() {
            return deductionItems;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        /**
         * 차감 아이템
         */
        public static class DeductionItem {
            
            @NotNull(message = "Reservation ID is required")
            private final ReservationId reservationId;
            
            @NotNull(message = "Product ID is required")
            private final ProductId productId;
            
            public DeductionItem(ReservationId reservationId, ProductId productId) {
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
     * 차감 결과
     */
    class DeductionResult {
        private final ReservationId reservationId;
        private final ProductId productId;
        private final boolean success;
        private final String failureReason;
        
        // 성공 생성자
        public DeductionResult(ReservationId reservationId, ProductId productId) {
            this.reservationId = reservationId;
            this.productId = productId;
            this.success = true;
            this.failureReason = null;
        }
        
        // 실패 생성자
        public DeductionResult(ReservationId reservationId, ProductId productId, 
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
     * 유효하지 않은 예약
     */
    class InvalidReservationException extends RuntimeException {
        private final ReservationId reservationId;
        private final String reason;
        
        public InvalidReservationException(ReservationId reservationId, String reason) {
            super(String.format("Invalid reservation %s: %s", reservationId, reason));
            this.reservationId = reservationId;
            this.reason = reason;
        }
        
        public ReservationId getReservationId() { return reservationId; }
        public String getReason() { return reason; }
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
}