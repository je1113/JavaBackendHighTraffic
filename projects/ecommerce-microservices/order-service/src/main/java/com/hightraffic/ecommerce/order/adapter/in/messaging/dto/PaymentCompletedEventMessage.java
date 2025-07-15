package com.hightraffic.ecommerce.order.adapter.in.messaging.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 결제 완료 이벤트 메시지 DTO
 * 
 * Kafka를 통해 수신하는 결제 완료 이벤트
 */
public record PaymentCompletedEventMessage(
    @JsonProperty("eventId")
    String eventId,
    
    @JsonProperty("eventType")
    String eventType,
    
    @JsonProperty("timestamp")
    Instant timestamp,
    
    @JsonProperty("version")
    int version,
    
    @JsonProperty("aggregateId")
    String aggregateId,
    
    @JsonProperty("orderId")
    String orderId,
    
    @JsonProperty("paymentId")
    String paymentId,
    
    @JsonProperty("amount")
    BigDecimal amount,
    
    @JsonProperty("currency")
    String currency,
    
    @JsonProperty("paymentMethod")
    String paymentMethod,
    
    @JsonProperty("transactionId")
    String transactionId,
    
    @JsonProperty("customerId")
    String customerId,
    
    @JsonProperty("paidAt")
    Instant paidAt
) {
    
    /**
     * 결제 방법이 카드인지 확인
     */
    public boolean isCardPayment() {
        return "CARD".equals(paymentMethod);
    }
    
    /**
     * 결제 방법이 계좌이체인지 확인
     */
    public boolean isBankTransfer() {
        return "BANK_TRANSFER".equals(paymentMethod);
    }
    
    /**
     * 모바일 결제인지 확인
     */
    public boolean isMobilePayment() {
        return paymentMethod != null && 
               (paymentMethod.contains("KAKAO") || paymentMethod.contains("NAVER"));
    }
    
    /**
     * 결제 금액이 유효한지 확인
     */
    public boolean isValidAmount() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
}