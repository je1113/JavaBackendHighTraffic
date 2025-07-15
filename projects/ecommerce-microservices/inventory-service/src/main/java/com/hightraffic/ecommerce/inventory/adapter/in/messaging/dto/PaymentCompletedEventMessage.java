package com.hightraffic.ecommerce.inventory.adapter.in.messaging.dto;

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
     * 결제 방법 타입 enum
     */
    public enum PaymentMethodType {
        CARD("카드"),
        BANK_TRANSFER("계좌이체"),
        KAKAO_PAY("카카오페이"),
        NAVER_PAY("네이버페이"),
        SAMSUNG_PAY("삼성페이"),
        APPLE_PAY("애플페이"),
        PAYPAL("페이팔"),
        VIRTUAL_ACCOUNT("가상계좌");
        
        private final String description;
        
        PaymentMethodType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 결제 방법이 카드인지 확인
     */
    public boolean isCardPayment() {
        return PaymentMethodType.CARD.name().equals(paymentMethod);
    }
    
    /**
     * 결제 방법이 계좌이체인지 확인
     */
    public boolean isBankTransfer() {
        return PaymentMethodType.BANK_TRANSFER.name().equals(paymentMethod);
    }
    
    /**
     * 모바일 결제인지 확인
     */
    public boolean isMobilePayment() {
        return paymentMethod != null && 
               (paymentMethod.contains("KAKAO") || 
                paymentMethod.contains("NAVER") ||
                paymentMethod.contains("SAMSUNG") ||
                paymentMethod.contains("APPLE"));
    }
    
    /**
     * 간편결제인지 확인 (카카오페이, 네이버페이 등)
     */
    public boolean isSimplePayment() {
        return PaymentMethodType.KAKAO_PAY.name().equals(paymentMethod) ||
               PaymentMethodType.NAVER_PAY.name().equals(paymentMethod) ||
               PaymentMethodType.SAMSUNG_PAY.name().equals(paymentMethod) ||
               PaymentMethodType.APPLE_PAY.name().equals(paymentMethod);
    }
    
    /**
     * 해외 결제인지 확인
     */
    public boolean isInternationalPayment() {
        return PaymentMethodType.PAYPAL.name().equals(paymentMethod) ||
               !"KRW".equals(currency);
    }
    
    /**
     * 결제 금액이 유효한지 확인
     */
    public boolean isValidAmount() {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    /**
     * 고액 결제인지 확인 (100만원 이상)
     */
    public boolean isHighValuePayment() {
        return isValidAmount() && 
               "KRW".equals(currency) && 
               amount.compareTo(new BigDecimal("1000000")) >= 0;
    }
    
    /**
     * 소액 결제인지 확인 (1만원 미만)
     */
    public boolean isSmallValuePayment() {
        return isValidAmount() && 
               "KRW".equals(currency) && 
               amount.compareTo(new BigDecimal("10000")) < 0;
    }
}