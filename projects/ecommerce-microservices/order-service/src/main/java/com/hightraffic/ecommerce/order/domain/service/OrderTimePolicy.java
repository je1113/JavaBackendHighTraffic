package com.hightraffic.ecommerce.order.domain.service;

/**
 * 주문 시간 정책 인터페이스
 * 
 * 도메인 레이어에서 필요한 시간 관련 비즈니스 규칙을 정의합니다.
 * 구현체는 어댑터 레이어에서 제공합니다.
 */
public interface OrderTimePolicy {
    
    int getDuplicateOrderPreventionMinutes();
    
    int getOrderCancellationHours();
}