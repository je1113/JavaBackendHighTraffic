package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 생성 요청 DTO
 */
public record CreateOrderRequest(
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    @Size(max = 50, message = "고객 ID는 50자를 초과할 수 없습니다")
    String customerId,
    
    @JsonProperty("orderItems")
    @NotEmpty(message = "주문 항목은 최소 1개 이상이어야 합니다")
    @Size(max = 100, message = "주문 항목은 100개를 초과할 수 없습니다")
    @Valid
    List<OrderItemRequest> orderItems,
    
    @JsonProperty("shippingAddress")
    @NotNull(message = "배송 주소는 필수입니다")
    @Valid
    ShippingAddressRequest shippingAddress,
    
    @JsonProperty("paymentMethod")
    @NotNull(message = "결제 방법은 필수입니다")
    @Valid
    PaymentMethodRequest paymentMethod,
    
    @JsonProperty("couponCode")
    @Size(max = 20, message = "쿠폰 코드는 20자를 초과할 수 없습니다")
    String couponCode,
    
    @JsonProperty("orderNotes")
    @Size(max = 500, message = "주문 메모는 500자를 초과할 수 없습니다")
    String orderNotes
) {
    
    /**
     * 총 주문 금액 계산 (클라이언트 검증용)
     */
    public BigDecimal calculateTotalAmount() {
        return orderItems.stream()
            .map(OrderItemRequest::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * 총 주문 수량 계산
     */
    public int getTotalQuantity() {
        return orderItems.stream()
            .mapToInt(OrderItemRequest::quantity)
            .sum();
    }
    
    /**
     * 주문 항목 요청 DTO
     */
    public record OrderItemRequest(
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        @Size(max = 50, message = "상품 ID는 50자를 초과할 수 없습니다")
        String productId,
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 200, message = "상품명은 200자를 초과할 수 없습니다")
        String productName,
        
        @JsonProperty("quantity")
        @Min(value = 1, message = "수량은 1개 이상이어야 합니다")
        @Max(value = 999, message = "수량은 999개를 초과할 수 없습니다")
        int quantity,
        
        @JsonProperty("unitPrice")
        @NotNull(message = "단가는 필수입니다")
        @DecimalMin(value = "0.01", message = "단가는 0.01 이상이어야 합니다")
        @DecimalMax(value = "9999999.99", message = "단가는 9,999,999.99를 초과할 수 없습니다")
        BigDecimal unitPrice
    ) {
        public BigDecimal getTotalPrice() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
    
    /**
     * 배송 주소 요청 DTO
     */
    public record ShippingAddressRequest(
        @JsonProperty("recipientName")
        @NotBlank(message = "수령인 이름은 필수입니다")
        @Size(max = 50, message = "수령인 이름은 50자를 초과할 수 없습니다")
        String recipientName,
        
        @JsonProperty("recipientPhone")
        @NotBlank(message = "수령인 전화번호는 필수입니다")
        @Pattern(regexp = "^[0-9-+()\\s]+$", message = "올바른 전화번호 형식이 아닙니다")
        @Size(max = 20, message = "전화번호는 20자를 초과할 수 없습니다")
        String recipientPhone,
        
        @JsonProperty("zipCode")
        @NotBlank(message = "우편번호는 필수입니다")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다")
        String zipCode,
        
        @JsonProperty("address")
        @NotBlank(message = "주소는 필수입니다")
        @Size(max = 200, message = "주소는 200자를 초과할 수 없습니다")
        String address,
        
        @JsonProperty("addressDetail")
        @Size(max = 100, message = "상세주소는 100자를 초과할 수 없습니다")
        String addressDetail,
        
        @JsonProperty("deliveryNotes")
        @Size(max = 200, message = "배송 메모는 200자를 초과할 수 없습니다")
        String deliveryNotes
    ) {}
    
    /**
     * 결제 방법 요청 DTO
     */
    public record PaymentMethodRequest(
        @JsonProperty("paymentType")
        @NotBlank(message = "결제 타입은 필수입니다")
        @Pattern(regexp = "^(CARD|BANK_TRANSFER|MOBILE|KAKAO_PAY|NAVER_PAY)$", 
                message = "지원하지 않는 결제 타입입니다")
        String paymentType,
        
        @JsonProperty("cardNumber")
        @Size(max = 20, message = "카드번호는 20자를 초과할 수 없습니다")
        String cardNumber,
        
        @JsonProperty("cardHolderName")
        @Size(max = 50, message = "카드 소유자명은 50자를 초과할 수 없습니다")
        String cardHolderName,
        
        @JsonProperty("expiryMonth")
        @Min(value = 1, message = "만료 월은 1 이상이어야 합니다")
        @Max(value = 12, message = "만료 월은 12 이하여야 합니다")
        Integer expiryMonth,
        
        @JsonProperty("expiryYear")
        @Min(value = 2024, message = "만료 년도는 2024 이상이어야 합니다")
        @Max(value = 2050, message = "만료 년도는 2050 이하여야 합니다")
        Integer expiryYear,
        
        @JsonProperty("bankCode")
        @Size(max = 10, message = "은행 코드는 10자를 초과할 수 없습니다")
        String bankCode,
        
        @JsonProperty("accountNumber")
        @Size(max = 20, message = "계좌번호는 20자를 초과할 수 없습니다")
        String accountNumber
    ) {}
}