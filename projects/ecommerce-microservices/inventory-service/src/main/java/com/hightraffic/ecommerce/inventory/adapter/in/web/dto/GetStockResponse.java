package com.hightraffic.ecommerce.inventory.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고 조회 응답 DTO
 */
public record GetStockResponse(
    @JsonProperty("productId")
    String productId,
    
    @JsonProperty("productName")
    String productName,
    
    @JsonProperty("totalStock")
    BigDecimal totalStock,
    
    @JsonProperty("availableStock")
    BigDecimal availableStock,
    
    @JsonProperty("reservedStock")
    BigDecimal reservedStock,
    
    @JsonProperty("lowStockThreshold")
    BigDecimal lowStockThreshold,
    
    @JsonProperty("isLowStock")
    boolean isLowStock,
    
    @JsonProperty("isOutOfStock")
    boolean isOutOfStock,
    
    @JsonProperty("activeReservations")
    List<ReservationInfo> activeReservations,
    
    @JsonProperty("lastUpdated")
    LocalDateTime lastUpdated
) {
    
    /**
     * 예약 정보 DTO
     */
    public record ReservationInfo(
        @JsonProperty("reservationId")
        String reservationId,
        
        @JsonProperty("orderId")
        String orderId,
        
        @JsonProperty("quantity")
        BigDecimal quantity,
        
        @JsonProperty("reservedAt")
        LocalDateTime reservedAt,
        
        @JsonProperty("expiresAt")
        LocalDateTime expiresAt,
        
        @JsonProperty("status")
        String status
    ) {}
    
    /**
     * 간단한 재고 정보 생성
     */
    public static GetStockResponse simple(String productId, String productName,
                                        BigDecimal totalStock, BigDecimal availableStock,
                                        BigDecimal reservedStock) {
        BigDecimal lowStockThreshold = BigDecimal.valueOf(10);
        boolean isLowStock = availableStock.compareTo(lowStockThreshold) <= 0;
        boolean isOutOfStock = availableStock.compareTo(BigDecimal.ZERO) <= 0;
        
        return new GetStockResponse(
            productId,
            productName,
            totalStock,
            availableStock,
            reservedStock,
            lowStockThreshold,
            isLowStock,
            isOutOfStock,
            List.of(),
            LocalDateTime.now()
        );
    }
    
    /**
     * 배치 재고 조회 응답 DTO
     */
    public record BatchGetStockResponse(
        @JsonProperty("products")
        List<ProductStock> products,
        
        @JsonProperty("totalCount")
        int totalCount,
        
        @JsonProperty("timestamp")
        LocalDateTime timestamp
    ) {
        
        /**
         * 상품별 재고 정보 DTO
         */
        public record ProductStock(
            @JsonProperty("productId")
            String productId,
            
            @JsonProperty("productName")
            String productName,
            
            @JsonProperty("availableStock")
            BigDecimal availableStock,
            
            @JsonProperty("reservedStock")
            BigDecimal reservedStock,
            
            @JsonProperty("isAvailable")
            boolean isAvailable
        ) {
            public static ProductStock of(String productId, String productName,
                                        BigDecimal availableStock, BigDecimal reservedStock) {
                return new ProductStock(
                    productId,
                    productName,
                    availableStock,
                    reservedStock,
                    availableStock.compareTo(BigDecimal.ZERO) > 0
                );
            }
        }
    }
}