package com.hightraffic.ecommerce.order.domain.service;

import java.math.BigDecimal;

/**
 * 주문 가격 정책 인터페이스
 * 
 * 도메인 레이어에서 필요한 가격 관련 비즈니스 규칙을 정의합니다.
 * 구현체는 어댑터 레이어에서 제공합니다.
 */
public interface OrderPricingPolicy {
    
    BigDecimal getVipDiscountRate();
    
    int getBulkDiscountThreshold();
    
    BigDecimal getBulkDiscountRate();
    
    BigDecimal getLoyaltyDiscountRate();
    
    int getLoyaltyOrderThreshold();
    
    BigDecimal getFreeShippingThreshold();
    
    BigDecimal getStandardShippingFee();
    
    BigDecimal getExpressShippingFee();
    
    BigDecimal getWeekendSurchargeRate();
    
    boolean isEnableWeekendSurcharge();
    
    BigDecimal getVipThreshold();
}