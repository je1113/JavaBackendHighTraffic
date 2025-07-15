package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 생성 응답 DTO
 */
public record CreateOrderResponse(
    @JsonProperty("orderId")
    String orderId,
    
    @JsonProperty("orderNumber")
    String orderNumber,
    
    @JsonProperty("customerId")
    String customerId,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("totalAmount")
    BigDecimal totalAmount,
    
    @JsonProperty("currency")
    String currency,
    
    @JsonProperty("createdAt")
    LocalDateTime createdAt,
    
    @JsonProperty("estimatedDeliveryDate")
    LocalDateTime estimatedDeliveryDate,
    
    @JsonProperty("paymentUrl")
    String paymentUrl,
    
    @JsonProperty("message")
    String message
) {
    
    /**
     * 성공 응답 생성
     */
    public static CreateOrderResponse success(
            String orderId,
            String orderNumber,
            String customerId,
            String status,
            BigDecimal totalAmount,
            String currency,
            LocalDateTime createdAt,
            LocalDateTime estimatedDeliveryDate,
            String paymentUrl) {
        return new CreateOrderResponse(
            orderId,
            orderNumber,
            customerId,
            status,
            totalAmount,
            currency,
            createdAt,
            estimatedDeliveryDate,
            paymentUrl,
            "주문이 성공적으로 생성되었습니다"
        );
    }
    
    /**
     * 간단한 성공 응답 생성
     */
    public static CreateOrderResponse of(String orderId, String orderNumber, 
                                       BigDecimal totalAmount, String status) {
        return new CreateOrderResponse(
            orderId,
            orderNumber,
            null,
            status,
            totalAmount,
            "KRW",
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(3),
            null,
            "주문이 성공적으로 생성되었습니다"
        );
    }
}