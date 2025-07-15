package com.hightraffic.ecommerce.inventory.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * 재고 조정 요청 DTO
 */
public record AdjustStockRequest(
    @JsonProperty("adjustmentType")
    @NotBlank(message = "조정 타입은 필수입니다")
    @Pattern(regexp = "^(ADD|SUBTRACT|SET)$", message = "조정 타입은 ADD, SUBTRACT, SET 중 하나여야 합니다")
    String adjustmentType,
    
    @JsonProperty("quantity")
    @NotNull(message = "수량은 필수입니다")
    @DecimalMin(value = "0", message = "수량은 0 이상이어야 합니다")
    @DecimalMax(value = "99999.99", message = "수량은 99999.99를 초과할 수 없습니다")
    BigDecimal quantity,
    
    @JsonProperty("reason")
    @NotBlank(message = "조정 사유는 필수입니다")
    @Size(max = 500, message = "조정 사유는 500자를 초과할 수 없습니다")
    String reason,
    
    @JsonProperty("reasonCode")
    @NotBlank(message = "조정 사유 코드는 필수입니다")
    @Pattern(regexp = "^(INCOMING|DAMAGE|LOSS|RETURN|CORRECTION|OTHER)$", 
            message = "올바른 조정 사유 코드가 아닙니다")
    String reasonCode,
    
    @JsonProperty("reference")
    @Size(max = 100, message = "참조 번호는 100자를 초과할 수 없습니다")
    String reference,
    
    @JsonProperty("operator")
    @NotBlank(message = "작업자 정보는 필수입니다")
    @Size(max = 50, message = "작업자 정보는 50자를 초과할 수 없습니다")
    String operator
) {
    
    /**
     * 입고 조정 생성
     */
    public static AdjustStockRequest incoming(BigDecimal quantity, String reference, String operator) {
        return new AdjustStockRequest(
            "ADD",
            quantity,
            "상품 입고",
            "INCOMING",
            reference,
            operator
        );
    }
    
    /**
     * 반품 조정 생성
     */
    public static AdjustStockRequest returnStock(BigDecimal quantity, String orderId, String operator) {
        return new AdjustStockRequest(
            "ADD",
            quantity,
            "고객 반품",
            "RETURN",
            orderId,
            operator
        );
    }
    
    /**
     * 손실 조정 생성
     */
    public static AdjustStockRequest loss(BigDecimal quantity, String reason, String operator) {
        return new AdjustStockRequest(
            "SUBTRACT",
            quantity,
            reason,
            "LOSS",
            null,
            operator
        );
    }
}