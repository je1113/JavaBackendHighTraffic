package com.hightraffic.ecommerce.order.application.port.out;

/**
 * 주문 정책 아웃바운드 포트
 * 
 * 외부 설정이나 정책 시스템으로부터 주문 관련 정책을 가져오는 포트입니다.
 * 가격 정책, 시간 정책 등 다양한 정책 어댑터들이 이 포트를 구현합니다.
 */
public interface OrderPolicyPort {
    // 마커 인터페이스 - 정책 관련 어댑터들을 위한 공통 포트
    // 실제 정책 메서드는 각 도메인 정책 인터페이스(OrderPricingPolicy, OrderTimePolicy)에 정의됨
}