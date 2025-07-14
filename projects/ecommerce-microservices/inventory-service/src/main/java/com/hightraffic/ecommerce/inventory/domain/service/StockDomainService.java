package com.hightraffic.ecommerce.inventory.domain.service;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 재고 도메인 서비스
 * 
 * 책임:
 * - 재고 부족 검증 로직
 * - 예약 만료 처리
 * - 복잡한 재고 비즈니스 규칙 처리
 * - 여러 상품 간 재고 관련 협력 로직
 */
public class StockDomainService {
    
    /**
     * 재고 예약 가능 여부 검증
     * 
     * @param product 상품
     * @param requestedQuantity 요청 수량
     * @throws InsufficientStockException 재고 부족 시
     * @throws InvalidStockOperationException 비활성 상품일 때
     */
    public void validateStockAvailability(Product product, StockQuantity requestedQuantity) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(requestedQuantity, "Requested quantity cannot be null");
        
        // 상품 활성화 상태 확인
        if (!product.isActive()) {
            throw new InvalidStockOperationException(
                String.format("Cannot reserve stock for inactive product: %s", product.getProductId())
            );
        }
        
        // 재고 가용성 확인
        if (!product.isStockAvailable(requestedQuantity)) {
            throw new InsufficientStockException(
                String.format("Insufficient stock for product %s. Available: %d, Required: %d",
                    product.getProductId().getValue(),
                    product.getAvailableQuantity().getValue(),
                    requestedQuantity.getValue())
            );
        }
    }
    
    /**
     * 여러 상품의 재고 예약 가능 여부 검증 (배치 처리)
     * 
     * @param products 상품 목록
     * @param quantities 각 상품별 요청 수량
     * @throws InsufficientStockException 재고 부족 시
     * @throws InvalidStockOperationException 비활성 상품일 때
     * @throws IllegalArgumentException 상품과 수량 목록 크기가 다를 때
     */
    public void validateBatchStockAvailability(List<Product> products, List<StockQuantity> quantities) {
        Objects.requireNonNull(products, "Products cannot be null");
        Objects.requireNonNull(quantities, "Quantities cannot be null");
        
        if (products.size() != quantities.size()) {
            throw new IllegalArgumentException("Products and quantities lists must have the same size");
        }
        
        for (int i = 0; i < products.size(); i++) {
            validateStockAvailability(products.get(i), quantities.get(i));
        }
    }
    
    /**
     * 만료된 예약 처리
     * 
     * @param product 상품
     * @return 정리된 예약 수
     */
    public int processExpiredReservations(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        
        int beforeCount = product.getStock().getReservationCount();
        product.cleanupExpiredReservations();
        int afterCount = product.getStock().getReservationCount();
        
        return beforeCount - afterCount;
    }
    
    /**
     * 예약 만료 임박 확인
     * 
     * @param product 상품
     * @param reservationId 예약 ID
     * @param warningMinutes 경고 시간 (분)
     * @return 만료 임박 여부
     */
    public boolean isReservationExpiringSoon(Product product, ReservationId reservationId, int warningMinutes) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        
        StockReservation reservation = product.getReservation(reservationId);
        if (reservation == null) {
            return false;
        }
        
        return reservation.getRemainingMinutes() <= warningMinutes;
    }
    
    /**
     * 재고 부족 심각도 평가
     * 
     * @param product 상품
     * @return 재고 부족 심각도 (0: 충분, 1: 부족, 2: 매우 부족, 3: 품절)
     */
    public int evaluateStockSeverity(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        
        if (product.isOutOfStock()) {
            return 3; // 품절
        }
        
        StockQuantity available = product.getAvailableQuantity();
        StockQuantity threshold = product.getLowStockThreshold();
        
        if (available.isLessThanOrEqual(threshold.divide(2))) {
            return 2; // 매우 부족 (임계값의 절반 이하)
        }
        
        if (available.isLessThanOrEqual(threshold)) {
            return 1; // 부족 (임계값 이하)
        }
        
        return 0; // 충분
    }
    
    /**
     * 재고 회전율 기반 적정 재고 수량 계산
     * 
     * @param averageDailySales 일평균 판매량
     * @param leadTimeDays 리드타임 (일)
     * @param safetyStockMultiplier 안전 재고 배수
     * @return 적정 재고 수량
     */
    public StockQuantity calculateOptimalStockLevel(
            StockQuantity averageDailySales, 
            int leadTimeDays, 
            double safetyStockMultiplier) {
        
        Objects.requireNonNull(averageDailySales, "Average daily sales cannot be null");
        
        if (leadTimeDays < 0) {
            throw new IllegalArgumentException("Lead time days cannot be negative");
        }
        
        if (safetyStockMultiplier < 0) {
            throw new IllegalArgumentException("Safety stock multiplier cannot be negative");
        }
        
        // 기본 재고 수량 = 일평균 판매량 × 리드타임
        int basicStock = averageDailySales.getValue() * leadTimeDays;
        
        // 안전 재고 = 기본 재고 × 안전 재고 배수
        int safetyStock = (int) (basicStock * safetyStockMultiplier);
        
        return StockQuantity.of(basicStock + safetyStock);
    }
    
    /**
     * 재고 예약 효율성 검증
     * 
     * @param product 상품
     * @return 예약 효율성 점수 (0.0 ~ 1.0)
     */
    public double evaluateReservationEfficiency(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        
        StockQuantity total = product.getTotalQuantity();
        StockQuantity reserved = product.getReservedQuantity();
        
        if (total.isZero()) {
            return 0.0;
        }
        
        // 예약율 계산
        double reservationRate = (double) reserved.getValue() / total.getValue();
        
        // 예약 건수 대비 효율성 (예약이 많을수록 비효율적)
        int reservationCount = product.getStock().getReservationCount();
        double countPenalty = Math.min(reservationCount * 0.1, 0.5); // 최대 50% 감점
        
        return Math.max(0.0, reservationRate - countPenalty);
    }
    
    /**
     * 재고 조정 안전성 검증
     * 
     * @param product 상품
     * @param newTotalQuantity 새로운 총 재고 수량
     * @throws InvalidStockOperationException 안전하지 않은 조정일 때
     */
    public void validateStockAdjustmentSafety(Product product, StockQuantity newTotalQuantity) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(newTotalQuantity, "New total quantity cannot be null");
        
        StockQuantity reserved = product.getReservedQuantity();
        
        // 예약된 수량보다 적게 조정할 수 없음
        if (newTotalQuantity.isLessThan(reserved)) {
            throw new InvalidStockOperationException(
                String.format("Cannot adjust stock below reserved quantity. Reserved: %d, New Total: %d",
                    reserved.getValue(), newTotalQuantity.getValue())
            );
        }
        
        // 현재 재고보다 크게 줄이는 경우 경고
        StockQuantity currentTotal = product.getTotalQuantity();
        if (newTotalQuantity.isLessThan(currentTotal.divide(2))) {
            // 50% 이상 감소하는 경우 추가 검증이 필요할 수 있음
            // 여기서는 예외를 발생시키지 않고 로그나 이벤트로 처리 가능
        }
    }
    
    /**
     * 재고 예약 시간 연장 가능 여부 확인
     * 
     * @param product 상품
     * @param reservationId 예약 ID
     * @param additionalMinutes 추가 연장 시간 (분)
     * @return 연장 가능 여부
     */
    public boolean canExtendReservation(Product product, ReservationId reservationId, int additionalMinutes) {
        Objects.requireNonNull(product, "Product cannot be null");
        Objects.requireNonNull(reservationId, "Reservation ID cannot be null");
        
        StockReservation reservation = product.getReservation(reservationId);
        if (reservation == null || reservation.isExpired()) {
            return false;
        }
        
        // 최대 연장 시간 제한 (예: 2시간)
        long currentRemainingMinutes = reservation.getRemainingMinutes();
        return (currentRemainingMinutes + additionalMinutes) <= 120;
    }
    
    /**
     * 재고 상태 요약 정보 생성
     * 
     * @param product 상품
     * @return 재고 상태 정보
     */
    public StockStatusSummary generateStockStatusSummary(Product product) {
        Objects.requireNonNull(product, "Product cannot be null");
        
        return new StockStatusSummary(
            product.getProductId(),
            product.getAvailableQuantity(),
            product.getReservedQuantity(),
            product.getTotalQuantity(),
            product.getStock().getReservationCount(),
            evaluateStockSeverity(product),
            evaluateReservationEfficiency(product),
            product.isLowStock(),
            product.isOutOfStock(),
            LocalDateTime.now()
        );
    }
    
    /**
     * 재고 상태 요약 정보 클래스
     */
    public static class StockStatusSummary {
        private final ProductId productId;
        private final StockQuantity availableQuantity;
        private final StockQuantity reservedQuantity;
        private final StockQuantity totalQuantity;
        private final int reservationCount;
        private final int stockSeverity;
        private final double reservationEfficiency;
        private final boolean isLowStock;
        private final boolean isOutOfStock;
        private final LocalDateTime generatedAt;
        
        public StockStatusSummary(ProductId productId, StockQuantity availableQuantity, 
                                StockQuantity reservedQuantity, StockQuantity totalQuantity,
                                int reservationCount, int stockSeverity, double reservationEfficiency,
                                boolean isLowStock, boolean isOutOfStock, LocalDateTime generatedAt) {
            this.productId = productId;
            this.availableQuantity = availableQuantity;
            this.reservedQuantity = reservedQuantity;
            this.totalQuantity = totalQuantity;
            this.reservationCount = reservationCount;
            this.stockSeverity = stockSeverity;
            this.reservationEfficiency = reservationEfficiency;
            this.isLowStock = isLowStock;
            this.isOutOfStock = isOutOfStock;
            this.generatedAt = generatedAt;
        }
        
        // Getters
        public ProductId getProductId() { return productId; }
        public StockQuantity getAvailableQuantity() { return availableQuantity; }
        public StockQuantity getReservedQuantity() { return reservedQuantity; }
        public StockQuantity getTotalQuantity() { return totalQuantity; }
        public int getReservationCount() { return reservationCount; }
        public int getStockSeverity() { return stockSeverity; }
        public double getReservationEfficiency() { return reservationEfficiency; }
        public boolean isLowStock() { return isLowStock; }
        public boolean isOutOfStock() { return isOutOfStock; }
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        
        @Override
        public String toString() {
            return String.format("StockStatusSummary{productId=%s, available=%d, reserved=%d, total=%d, " +
                    "reservationCount=%d, severity=%d, efficiency=%.2f, lowStock=%s, outOfStock=%s}",
                productId, availableQuantity.getValue(), reservedQuantity.getValue(), 
                totalQuantity.getValue(), reservationCount, stockSeverity, reservationEfficiency,
                isLowStock, isOutOfStock);
        }
    }
}