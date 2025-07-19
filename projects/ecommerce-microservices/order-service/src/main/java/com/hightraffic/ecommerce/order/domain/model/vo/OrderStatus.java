package com.hightraffic.ecommerce.order.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 주문 상태 열거형
 * 주문의 생명주기를 나타내는 상태 정의
 */
public enum OrderStatus {
    
    /**
     * 주문 생성됨 (초기 상태)
     */
    PENDING("PENDING", "주문 대기중", 1),
    
    /**
     * 재고 예약 완료 및 주문 확정
     */
    CONFIRMED("CONFIRMED", "주문 확정", 2),
    
    /**
     * 결제 대기중
     */
    PAYMENT_PENDING("PAYMENT_PENDING", "결제 대기중", 3),
    
    /**
     * 결제 처리중
     */
    PAYMENT_PROCESSING("PAYMENT_PROCESSING", "결제 처리중", 4),
    
    /**
     * 결제 완료
     */
    PAID("PAID", "결제 완료", 5),
    
    /**
     * 배송 준비중
     */
    PREPARING("PREPARING", "배송 준비중", 6),
    
    /**
     * 배송중
     */
    SHIPPED("SHIPPED", "배송중", 7),
    
    /**
     * 배송 완료
     */
    DELIVERED("DELIVERED", "배송 완료", 8),
    
    /**
     * 주문 완료
     */
    COMPLETED("COMPLETED", "주문 완료", 9),
    
    /**
     * 주문 취소됨
     */
    CANCELLED("CANCELLED", "주문 취소", 10),
    
    /**
     * 환불 처리중
     */
    REFUNDING("REFUNDING", "환불 처리중", 11),
    
    /**
     * 환불 완료
     */
    REFUNDED("REFUNDED", "환불 완료", 12),
    
    /**
     * 주문 실패
     */
    FAILED("FAILED", "주문 실패", 13);
    
    private final String code;
    private final String description;
    private final int order;
    
    // 취소 가능한 상태들
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Collections.unmodifiableSet(
        EnumSet.of(PENDING, CONFIRMED, PAYMENT_PROCESSING, PAID, PREPARING)
    );
    
    // 환불 가능한 상태들
    private static final Set<OrderStatus> REFUNDABLE_STATUSES = Collections.unmodifiableSet(
        EnumSet.of(PAID, PREPARING, SHIPPED, DELIVERED, COMPLETED)
    );
    
    // 최종 상태들
    private static final Set<OrderStatus> FINAL_STATUSES = Collections.unmodifiableSet(
        EnumSet.of(CANCELLED, REFUNDED, FAILED)
    );
    
    OrderStatus(String code, String description, int order) {
        this.code = code;
        this.description = description;
        this.order = order;
    }
    
    @JsonValue
    public String getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public int getOrder() {
        return order;
    }
    
    /**
     * 코드로 OrderStatus 찾기
     */
    @JsonCreator
    public static OrderStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 주문 상태 코드: " + code));
    }
    
    /**
     * 다음 상태로 전이 가능한지 확인
     */
    public boolean canTransitionTo(OrderStatus targetStatus) {
        // 최종 상태에서는 전이 불가
        if (isFinalStatus()) {
            return false;
        }
        
        // 특별한 전이 규칙들
        switch (this) {
            case PENDING:
                return targetStatus == CONFIRMED || targetStatus == CANCELLED || targetStatus == FAILED;
            case CONFIRMED:
                return targetStatus == PAYMENT_PENDING || targetStatus == CANCELLED;
            case PAYMENT_PENDING:
                return targetStatus == PAYMENT_PROCESSING || targetStatus == CANCELLED || targetStatus == FAILED;
            case PAYMENT_PROCESSING:
                return targetStatus == PAID || targetStatus == FAILED || targetStatus == CANCELLED;
            case PAID:
                return targetStatus == PREPARING || targetStatus == REFUNDING;
            case PREPARING:
                return targetStatus == SHIPPED || targetStatus == CANCELLED;
            case SHIPPED:
                return targetStatus == DELIVERED;
            case DELIVERED:
                return targetStatus == COMPLETED || targetStatus == REFUNDING;
            case COMPLETED:
                return targetStatus == REFUNDING;
            case REFUNDING:
                return targetStatus == REFUNDED;
            default:
                return false;
        }
    }
    
    /**
     * 취소 가능한 상태인지 확인
     */
    public boolean isCancellable() {
        return CANCELLABLE_STATUSES.contains(this);
    }
    
    /**
     * 환불 가능한 상태인지 확인
     */
    public boolean isRefundable() {
        return REFUNDABLE_STATUSES.contains(this);
    }
    
    /**
     * 최종 상태인지 확인
     */
    public boolean isFinalStatus() {
        return FINAL_STATUSES.contains(this);
    }
    
    /**
     * 결제 완료 상태인지 확인
     */
    public boolean isPaid() {
        return this == PAID || order > PAID.order;
    }
    
    /**
     * 활성 상태인지 확인 (취소되지 않고 실패하지 않은 상태)
     */
    public boolean isActive() {
        return this != CANCELLED && this != FAILED && this != REFUNDED;
    }
    
    /**
     * 배송 이후 상태인지 확인
     */
    public boolean isAfterShipping() {
        return this == DELIVERED || this == COMPLETED || this == REFUNDING || this == REFUNDED;
    }
}