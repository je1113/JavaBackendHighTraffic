package com.hightraffic.ecommerce.inventory.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 재고 예약 요청 DTO
 */
public record ReserveStockRequest(
    @JsonProperty("reservationId")
    @NotBlank(message = "예약 ID는 필수입니다")
    @Size(max = 50, message = "예약 ID는 50자를 초과할 수 없습니다")
    String reservationId,
    
    @JsonProperty("productId")
    @NotBlank(message = "상품 ID는 필수입니다")
    @Size(max = 50, message = "상품 ID는 50자를 초과할 수 없습니다")
    String productId,
    
    @JsonProperty("quantity")
    @NotNull(message = "수량은 필수입니다")
    @DecimalMin(value = "0.01", message = "수량은 0.01 이상이어야 합니다")
    @DecimalMax(value = "99999.99", message = "수량은 99999.99를 초과할 수 없습니다")
    BigDecimal quantity,
    
    @JsonProperty("timeoutMinutes")
    @Min(value = 1, message = "타임아웃은 1분 이상이어야 합니다")
    @Max(value = 60, message = "타임아웃은 60분을 초과할 수 없습니다")
    Integer timeoutMinutes
) {
    
    /**
     * 기본 타임아웃(30분)으로 생성
     */
    public static ReserveStockRequest of(String reservationId, String productId, BigDecimal quantity) {
        return new ReserveStockRequest(reservationId, productId, quantity, 30);
    }
    
    /**
     * 배치 재고 예약 요청 DTO
     */
    public record BatchReserveStockRequest(
        @JsonProperty("reservationId")
        @NotBlank(message = "예약 ID는 필수입니다")
        String reservationId,
        
        @JsonProperty("items")
        @NotEmpty(message = "예약 항목은 최소 1개 이상이어야 합니다")
        @Size(max = 100, message = "예약 항목은 100개를 초과할 수 없습니다")
        @Valid
        List<ReservationItem> items,
        
        @JsonProperty("timeoutMinutes")
        @Min(value = 1, message = "타임아웃은 1분 이상이어야 합니다")
        @Max(value = 60, message = "타임아웃은 60분을 초과할 수 없습니다")
        Integer timeoutMinutes
    ) {
        
        /**
         * 예약 항목 DTO
         */
        public record ReservationItem(
            @JsonProperty("productId")
            @NotBlank(message = "상품 ID는 필수입니다")
            String productId,
            
            @JsonProperty("quantity")
            @NotNull(message = "수량은 필수입니다")
            @DecimalMin(value = "0.01", message = "수량은 0.01 이상이어야 합니다")
            BigDecimal quantity
        ) {}
    }
}