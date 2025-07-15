package com.hightraffic.ecommerce.inventory.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고 예약 응답 DTO
 */
public record ReserveStockResponse(
    @JsonProperty("reservationId")
    String reservationId,
    
    @JsonProperty("productId")
    String productId,
    
    @JsonProperty("reservedQuantity")
    BigDecimal reservedQuantity,
    
    @JsonProperty("availableQuantity")
    BigDecimal availableQuantity,
    
    @JsonProperty("expiresAt")
    LocalDateTime expiresAt,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("message")
    String message
) {
    
    /**
     * 성공 응답 생성
     */
    public static ReserveStockResponse success(String reservationId, String productId,
                                             BigDecimal reservedQuantity, 
                                             BigDecimal availableQuantity,
                                             LocalDateTime expiresAt) {
        return new ReserveStockResponse(
            reservationId,
            productId,
            reservedQuantity,
            availableQuantity,
            expiresAt,
            "SUCCESS",
            "재고 예약이 완료되었습니다"
        );
    }
    
    /**
     * 실패 응답 생성
     */
    public static ReserveStockResponse failed(String productId, String message) {
        return new ReserveStockResponse(
            null,
            productId,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            null,
            "FAILED",
            message
        );
    }
    
    /**
     * 배치 재고 예약 응답 DTO
     */
    public record BatchReserveStockResponse(
        @JsonProperty("reservationId")
        String reservationId,
        
        @JsonProperty("successCount")
        int successCount,
        
        @JsonProperty("failureCount")
        int failureCount,
        
        @JsonProperty("results")
        List<ReservationResult> results,
        
        @JsonProperty("status")
        String status,
        
        @JsonProperty("message")
        String message
    ) {
        
        /**
         * 예약 결과 DTO
         */
        public record ReservationResult(
            @JsonProperty("productId")
            String productId,
            
            @JsonProperty("success")
            boolean success,
            
            @JsonProperty("reservedQuantity")
            BigDecimal reservedQuantity,
            
            @JsonProperty("availableQuantity")
            BigDecimal availableQuantity,
            
            @JsonProperty("message")
            String message
        ) {
            public static ReservationResult success(String productId, BigDecimal reservedQuantity,
                                                  BigDecimal availableQuantity) {
                return new ReservationResult(
                    productId,
                    true,
                    reservedQuantity,
                    availableQuantity,
                    "예약 성공"
                );
            }
            
            public static ReservationResult failed(String productId, String message) {
                return new ReservationResult(
                    productId,
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    message
                );
            }
        }
        
        /**
         * 전체 성공 여부 확인
         */
        public boolean isFullySuccessful() {
            return failureCount == 0;
        }
        
        /**
         * 부분 성공 여부 확인
         */
        public boolean isPartiallySuccessful() {
            return successCount > 0 && failureCount > 0;
        }
    }
}