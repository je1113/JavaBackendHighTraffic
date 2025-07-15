package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 목록 조회 응답 DTO
 */
public record OrderListResponse(
    @JsonProperty("orders")
    List<OrderSummary> orders,
    
    @JsonProperty("totalElements")
    long totalElements,
    
    @JsonProperty("totalPages")
    int totalPages,
    
    @JsonProperty("currentPage")
    int currentPage,
    
    @JsonProperty("pageSize")
    int pageSize,
    
    @JsonProperty("hasNext")
    boolean hasNext,
    
    @JsonProperty("hasPrevious")
    boolean hasPrevious
) {
    
    /**
     * 주문 요약 정보 DTO
     */
    public record OrderSummary(
        @JsonProperty("orderId")
        String orderId,
        
        @JsonProperty("orderNumber")
        String orderNumber,
        
        @JsonProperty("status")
        String status,
        
        @JsonProperty("statusDescription")
        String statusDescription,
        
        @JsonProperty("totalAmount")
        BigDecimal totalAmount,
        
        @JsonProperty("currency")
        String currency,
        
        @JsonProperty("itemCount")
        int itemCount,
        
        @JsonProperty("firstProductName")
        String firstProductName,
        
        @JsonProperty("firstProductImage")
        String firstProductImage,
        
        @JsonProperty("createdAt")
        LocalDateTime createdAt,
        
        @JsonProperty("deliveredAt")
        LocalDateTime deliveredAt,
        
        @JsonProperty("isReviewable")
        boolean isReviewable,
        
        @JsonProperty("isCancellable")
        boolean isCancellable
    ) {
        
        /**
         * 주문 취소 가능 여부 판단
         */
        private static boolean checkCancellable(String status) {
            return List.of("PENDING", "STOCK_RESERVED", "PAYMENT_PROCESSING", "PAID")
                    .contains(status);
        }
        
        /**
         * 리뷰 작성 가능 여부 판단
         */
        private static boolean checkReviewable(String status, LocalDateTime deliveredAt) {
            if (!"COMPLETED".equals(status) || deliveredAt == null) {
                return false;
            }
            // 배송 완료 후 30일 이내만 리뷰 가능
            return deliveredAt.isAfter(LocalDateTime.now().minusDays(30));
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
        
        /**
         * 팩토리 메서드
         */
        public static OrderSummary create(String orderId, String orderNumber, String status,
                                        BigDecimal totalAmount, int itemCount,
                                        String firstProductName, LocalDateTime createdAt,
                                        LocalDateTime deliveredAt) {
            return new OrderSummary(
                orderId,
                orderNumber,
                status,
                getStatusDescription(status),
                totalAmount,
                "KRW",
                itemCount,
                firstProductName,
                null,
                createdAt,
                deliveredAt,
                checkReviewable(status, deliveredAt),
                checkCancellable(status)
            );
        }
    }
    
    /**
     * 빈 결과 생성
     */
    public static OrderListResponse empty() {
        return new OrderListResponse(List.of(), 0, 0, 0, 10, false, false);
    }
}