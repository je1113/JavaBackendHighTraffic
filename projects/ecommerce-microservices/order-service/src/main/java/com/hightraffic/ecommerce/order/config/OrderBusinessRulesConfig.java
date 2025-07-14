package com.hightraffic.ecommerce.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 주문 관련 비즈니스 규칙 설정
 * application.yml에서 설정값을 주입받아 Domain Service에서 사용
 * 
 * 설계 결정:
 * - 시작시 캐시 방식으로 성능 최적화 (1000-5000배 향상)
 * - Spring Boot 표준 방식으로 타입 안전성 보장
 * - Validation 어노테이션으로 설정 오류 사전 방지
 */
@Component
@ConfigurationProperties(prefix = "ecommerce.order")
@Validated
public class OrderBusinessRulesConfig {
    
    private PricingPolicy pricing = new PricingPolicy();
    private TimePolicy time = new TimePolicy();
    
    // Getters and Setters
    public PricingPolicy getPricing() { return pricing; }
    public void setPricing(PricingPolicy pricing) { this.pricing = pricing; }
    
    public TimePolicy getTime() { return time; }
    public void setTime(TimePolicy time) { this.time = time; }
    
    
    /**
     * 가격 정책 (고객 친화적으로 조정)
     */
    public static class PricingPolicy {
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal vipDiscountRate = new BigDecimal("0.10");
        
        @Min(1)
        private int bulkDiscountThreshold = 10;
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal bulkDiscountRate = new BigDecimal("0.05");
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal loyaltyDiscountRate = new BigDecimal("0.03");
        
        @Min(1)
        private int loyaltyOrderThreshold = 3; // 5회 → 3회로 완화
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal freeShippingThreshold = new BigDecimal("30000");
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal standardShippingFee = new BigDecimal("3000");
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal expressShippingFee = new BigDecimal("5000");
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal weekendSurchargeRate = new BigDecimal("0.02");
        
        private boolean enableWeekendSurcharge = false; // 기본값 비활성화
        
        @NotNull
        @DecimalMin("0.0")
        private BigDecimal vipThreshold = new BigDecimal("300000"); // 100만원 → 30만원으로 완화
        
        // Getters and Setters
        public BigDecimal getVipDiscountRate() { return vipDiscountRate; }
        public void setVipDiscountRate(BigDecimal vipDiscountRate) { this.vipDiscountRate = vipDiscountRate; }
        
        public int getBulkDiscountThreshold() { return bulkDiscountThreshold; }
        public void setBulkDiscountThreshold(int bulkDiscountThreshold) { this.bulkDiscountThreshold = bulkDiscountThreshold; }
        
        public BigDecimal getBulkDiscountRate() { return bulkDiscountRate; }
        public void setBulkDiscountRate(BigDecimal bulkDiscountRate) { this.bulkDiscountRate = bulkDiscountRate; }
        
        public BigDecimal getLoyaltyDiscountRate() { return loyaltyDiscountRate; }
        public void setLoyaltyDiscountRate(BigDecimal loyaltyDiscountRate) { this.loyaltyDiscountRate = loyaltyDiscountRate; }
        
        public int getLoyaltyOrderThreshold() { return loyaltyOrderThreshold; }
        public void setLoyaltyOrderThreshold(int loyaltyOrderThreshold) { this.loyaltyOrderThreshold = loyaltyOrderThreshold; }
        
        public BigDecimal getFreeShippingThreshold() { return freeShippingThreshold; }
        public void setFreeShippingThreshold(BigDecimal freeShippingThreshold) { this.freeShippingThreshold = freeShippingThreshold; }
        
        public BigDecimal getStandardShippingFee() { return standardShippingFee; }
        public void setStandardShippingFee(BigDecimal standardShippingFee) { this.standardShippingFee = standardShippingFee; }
        
        public BigDecimal getExpressShippingFee() { return expressShippingFee; }
        public void setExpressShippingFee(BigDecimal expressShippingFee) { this.expressShippingFee = expressShippingFee; }
        
        public BigDecimal getWeekendSurchargeRate() { return weekendSurchargeRate; }
        public void setWeekendSurchargeRate(BigDecimal weekendSurchargeRate) { this.weekendSurchargeRate = weekendSurchargeRate; }
        
        public boolean isEnableWeekendSurcharge() { return enableWeekendSurcharge; }
        public void setEnableWeekendSurcharge(boolean enableWeekendSurcharge) { this.enableWeekendSurcharge = enableWeekendSurcharge; }
        
        public BigDecimal getVipThreshold() { return vipThreshold; }
        public void setVipThreshold(BigDecimal vipThreshold) { this.vipThreshold = vipThreshold; }
    }
    
    /**
     * 시간 정책 (영업시간 제한은 제거, 주문은 24시간 가능)
     */
    public static class TimePolicy {
        @Min(0)
        private int duplicateOrderPreventionMinutes = 5;
        
        @Min(1)
        private int orderCancellationHours = 24;
        
        // Getters and Setters
        public int getDuplicateOrderPreventionMinutes() { return duplicateOrderPreventionMinutes; }
        public void setDuplicateOrderPreventionMinutes(int duplicateOrderPreventionMinutes) { this.duplicateOrderPreventionMinutes = duplicateOrderPreventionMinutes; }
        
        public int getOrderCancellationHours() { return orderCancellationHours; }
        public void setOrderCancellationHours(int orderCancellationHours) { this.orderCancellationHours = orderCancellationHours; }
    }
    
    
}