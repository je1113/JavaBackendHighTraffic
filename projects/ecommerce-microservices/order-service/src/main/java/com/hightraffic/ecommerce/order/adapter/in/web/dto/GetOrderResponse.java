package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 조회 응답 DTO
 */
public record GetOrderResponse(
    @JsonProperty("orderId")
    String orderId,
    
    @JsonProperty("orderNumber")
    String orderNumber,
    
    @JsonProperty("customerId")
    String customerId,
    
    @JsonProperty("status")
    String status,
    
    @JsonProperty("statusDescription")
    String statusDescription,
    
    @JsonProperty("orderItems")
    List<OrderItemResponse> orderItems,
    
    @JsonProperty("totalAmount")
    BigDecimal totalAmount,
    
    @JsonProperty("currency")
    String currency,
    
    @JsonProperty("shippingAddress")
    ShippingAddressResponse shippingAddress,
    
    @JsonProperty("payment")
    PaymentResponse payment,
    
    @JsonProperty("createdAt")
    LocalDateTime createdAt,
    
    @JsonProperty("updatedAt")
    LocalDateTime updatedAt,
    
    @JsonProperty("confirmedAt")
    LocalDateTime confirmedAt,
    
    @JsonProperty("cancelledAt")
    LocalDateTime cancelledAt,
    
    @JsonProperty("deliveredAt")
    LocalDateTime deliveredAt,
    
    @JsonProperty("estimatedDeliveryDate")
    LocalDateTime estimatedDeliveryDate,
    
    @JsonProperty("trackingNumber")
    String trackingNumber,
    
    @JsonProperty("orderNotes")
    String orderNotes,
    
    @JsonProperty("cancelReason")
    String cancelReason
) {
    
    /**
     * 주문 항목 응답 DTO
     */
    public record OrderItemResponse(
        @JsonProperty("productId")
        String productId,
        
        @JsonProperty("productName")
        String productName,
        
        @JsonProperty("productImage")
        String productImage,
        
        @JsonProperty("quantity")
        int quantity,
        
        @JsonProperty("unitPrice")
        BigDecimal unitPrice,
        
        @JsonProperty("totalPrice")
        BigDecimal totalPrice,
        
        @JsonProperty("discountAmount")
        BigDecimal discountAmount,
        
        @JsonProperty("status")
        String status
    ) {
        public static OrderItemResponse from(String productId, String productName, 
                                           int quantity, BigDecimal unitPrice) {
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            return new OrderItemResponse(
                productId,
                productName,
                null,
                quantity,
                unitPrice,
                totalPrice,
                BigDecimal.ZERO,
                "CONFIRMED"
            );
        }
    }
    
    /**
     * 배송 주소 응답 DTO
     */
    public record ShippingAddressResponse(
        @JsonProperty("recipientName")
        String recipientName,
        
        @JsonProperty("recipientPhone")
        String recipientPhone,
        
        @JsonProperty("zipCode")
        String zipCode,
        
        @JsonProperty("address")
        String address,
        
        @JsonProperty("addressDetail")
        String addressDetail,
        
        @JsonProperty("deliveryNotes")
        String deliveryNotes
    ) {}
    
    /**
     * 결제 정보 응답 DTO
     */
    public record PaymentResponse(
        @JsonProperty("paymentId")
        String paymentId,
        
        @JsonProperty("paymentType")
        String paymentType,
        
        @JsonProperty("paymentStatus")
        String paymentStatus,
        
        @JsonProperty("paidAmount")
        BigDecimal paidAmount,
        
        @JsonProperty("paidAt")
        LocalDateTime paidAt,
        
        @JsonProperty("paymentMethod")
        String paymentMethod,
        
        @JsonProperty("transactionId")
        String transactionId,
        
        @JsonProperty("receiptUrl")
        String receiptUrl
    ) {
        public static PaymentResponse pending(String paymentType) {
            return new PaymentResponse(
                null,
                paymentType,
                "PENDING",
                BigDecimal.ZERO,
                null,
                null,
                null,
                null
            );
        }
        
        public static PaymentResponse completed(String paymentId, String paymentType,
                                              BigDecimal paidAmount, LocalDateTime paidAt) {
            return new PaymentResponse(
                paymentId,
                paymentType,
                "COMPLETED",
                paidAmount,
                paidAt,
                paymentType,
                paymentId,
                null
            );
        }
    }
    
    /**
     * 상태별 설명 반환
     */
    private static String getStatusDescription(String status) {
        return switch (status) {
            case "PENDING" -> "주문 접수";
            case "STOCK_RESERVED" -> "재고 예약 완료";
            case "PAYMENT_PROCESSING" -> "결제 진행 중";
            case "PAID" -> "결제 완료";
            case "CONFIRMED" -> "주문 확정";
            case "PREPARING" -> "상품 준비 중";
            case "SHIPPING" -> "배송 중";
            case "DELIVERED" -> "배송 완료";
            case "COMPLETED" -> "구매 확정";
            case "CANCELLED" -> "주문 취소";
            case "REFUNDED" -> "환불 완료";
            default -> "알 수 없음";
        };
    }
}