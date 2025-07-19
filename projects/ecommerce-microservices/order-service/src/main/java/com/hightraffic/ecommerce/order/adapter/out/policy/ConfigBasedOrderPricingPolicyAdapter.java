package com.hightraffic.ecommerce.order.adapter.out.policy;

import com.hightraffic.ecommerce.order.application.port.out.OrderPolicyPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.service.OrderPricingPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 설정 기반 주문 가격 정책 어댑터
 * 
 * Spring의 설정 값을 도메인 정책 인터페이스로 변환하는 어댑터입니다.
 */
@Component
public class ConfigBasedOrderPricingPolicyAdapter implements OrderPricingPolicy, OrderPolicyPort {
    
    private final OrderBusinessRulesConfig config;
    
    public ConfigBasedOrderPricingPolicyAdapter(OrderBusinessRulesConfig config) {
        this.config = config;
    }
    
    @Override
    public BigDecimal getVipDiscountRate() {
        return config.getPricing().getVipDiscountRate();
    }
    
    @Override
    public int getBulkDiscountThreshold() {
        return config.getPricing().getBulkDiscountThreshold();
    }
    
    @Override
    public BigDecimal getBulkDiscountRate() {
        return config.getPricing().getBulkDiscountRate();
    }
    
    @Override
    public BigDecimal getLoyaltyDiscountRate() {
        return config.getPricing().getLoyaltyDiscountRate();
    }
    
    @Override
    public int getLoyaltyOrderThreshold() {
        return config.getPricing().getLoyaltyOrderThreshold();
    }
    
    @Override
    public BigDecimal getFreeShippingThreshold() {
        return config.getPricing().getFreeShippingThreshold();
    }
    
    @Override
    public BigDecimal getStandardShippingFee() {
        return config.getPricing().getStandardShippingFee();
    }
    
    @Override
    public BigDecimal getExpressShippingFee() {
        return config.getPricing().getExpressShippingFee();
    }
    
    @Override
    public BigDecimal getWeekendSurchargeRate() {
        return config.getPricing().getWeekendSurchargeRate();
    }
    
    @Override
    public boolean isEnableWeekendSurcharge() {
        return config.getPricing().isEnableWeekendSurcharge();
    }
    
    @Override
    public BigDecimal getVipThreshold() {
        return config.getPricing().getVipThreshold();
    }
}