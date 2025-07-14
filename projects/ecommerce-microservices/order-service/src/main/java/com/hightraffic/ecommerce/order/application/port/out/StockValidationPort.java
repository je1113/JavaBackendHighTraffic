package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 재고 검증 Outbound Port
 * 
 * Inventory Service와의 통신을 담당하는 인터페이스
 * 재고 확인, 예약, 해제 등의 기능 제공
 */
public interface StockValidationPort {
    
    /**
     * 단일 상품 재고 확인
     * 
     * @param productId 상품 ID
     * @param quantity 요청 수량
     * @return 재고 가능 여부
     */
    boolean isStockAvailable(ProductId productId, Integer quantity);
    
    /**
     * 다수 상품 재고 일괄 확인
     * 
     * @param stockRequests 상품별 요청 수량 맵
     * @return 상품별 재고 가능 여부 맵
     */
    Map<ProductId, Boolean> checkBatchStockAvailability(Map<ProductId, Integer> stockRequests);
    
    /**
     * 재고 예약
     * 
     * @param productId 상품 ID
     * @param quantity 예약 수량
     * @param orderId 주문 ID (예약 추적용)
     * @return 예약 ID (성공 시), null (실패 시)
     */
    String reserveStock(ProductId productId, Integer quantity, String orderId);
    
    /**
     * 다수 상품 재고 일괄 예약
     * 
     * @param reservations 예약 요청 목록
     * @return 예약 결과 목록
     */
    List<StockReservationResult> reserveBatchStock(List<StockReservationRequest> reservations);
    
    /**
     * 재고 예약 해제
     * 
     * @param reservationId 예약 ID
     * @param orderId 주문 ID
     * @return 해제 성공 여부
     */
    boolean releaseStock(String reservationId, String orderId);
    
    /**
     * 재고 정보 조회
     * 
     * @param productId 상품 ID
     * @return 재고 정보
     */
    StockInfo getStockInfo(ProductId productId);
    
    /**
     * 비동기 재고 확인
     * 
     * @param productId 상품 ID
     * @param quantity 요청 수량
     * @return 재고 가능 여부 Future
     */
    CompletableFuture<Boolean> isStockAvailableAsync(ProductId productId, Integer quantity);
    
    /**
     * 재고 예약 요청
     */
    class StockReservationRequest {
        private final ProductId productId;
        private final Integer quantity;
        private final String orderId;
        
        public StockReservationRequest(ProductId productId, Integer quantity, String orderId) {
            this.productId = productId;
            this.quantity = quantity;
            this.orderId = orderId;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public Integer getQuantity() { return quantity; }
        public String getOrderId() { return orderId; }
    }
    
    /**
     * 재고 예약 결과
     */
    class StockReservationResult {
        private final ProductId productId;
        private final boolean success;
        private final String reservationId;
        private final String failureReason;
        
        // 성공 생성자
        public StockReservationResult(ProductId productId, String reservationId) {
            this.productId = productId;
            this.success = true;
            this.reservationId = reservationId;
            this.failureReason = null;
        }
        
        // 실패 생성자
        public StockReservationResult(ProductId productId, String failureReason) {
            this.productId = productId;
            this.success = false;
            this.reservationId = null;
            this.failureReason = failureReason;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public boolean isSuccess() { return success; }
        public String getReservationId() { return reservationId; }
        public String getFailureReason() { return failureReason; }
    }
    
    /**
     * 재고 정보
     */
    class StockInfo {
        private final ProductId productId;
        private final String productName;
        private final Integer availableQuantity;
        private final Integer reservedQuantity;
        private final Integer totalQuantity;
        private final Money price;
        private final boolean isActive;
        private final boolean isLowStock;
        
        public StockInfo(ProductId productId, String productName, 
                        Integer availableQuantity, Integer reservedQuantity, 
                        Integer totalQuantity, Money price, 
                        boolean isActive, boolean isLowStock) {
            this.productId = productId;
            this.productName = productName;
            this.availableQuantity = availableQuantity;
            this.reservedQuantity = reservedQuantity;
            this.totalQuantity = totalQuantity;
            this.price = price;
            this.isActive = isActive;
            this.isLowStock = isLowStock;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public String getProductName() { return productName; }
        public Integer getAvailableQuantity() { return availableQuantity; }
        public Integer getReservedQuantity() { return reservedQuantity; }
        public Integer getTotalQuantity() { return totalQuantity; }
        public Money getPrice() { return price; }
        public boolean isActive() { return isActive; }
        public boolean isLowStock() { return isLowStock; }
    }
}