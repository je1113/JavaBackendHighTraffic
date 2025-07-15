package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청 DTO
 */
public record CancelOrderRequest(
    @JsonProperty("cancelReason")
    @NotBlank(message = "취소 사유는 필수입니다")
    @Size(max = 500, message = "취소 사유는 500자를 초과할 수 없습니다")
    String cancelReason,
    
    @JsonProperty("cancelReasonCode")
    @NotBlank(message = "취소 사유 코드는 필수입니다")
    String cancelReasonCode,
    
    @JsonProperty("refundAccount")
    RefundAccountRequest refundAccount
) {
    
    /**
     * 환불 계좌 정보 DTO
     */
    public record RefundAccountRequest(
        @JsonProperty("bankCode")
        @NotBlank(message = "은행 코드는 필수입니다")
        String bankCode,
        
        @JsonProperty("accountNumber")
        @NotBlank(message = "계좌번호는 필수입니다")
        @Size(max = 20, message = "계좌번호는 20자를 초과할 수 없습니다")
        String accountNumber,
        
        @JsonProperty("accountHolder")
        @NotBlank(message = "예금주명은 필수입니다")
        @Size(max = 50, message = "예금주명은 50자를 초과할 수 없습니다")
        String accountHolder
    ) {}
}