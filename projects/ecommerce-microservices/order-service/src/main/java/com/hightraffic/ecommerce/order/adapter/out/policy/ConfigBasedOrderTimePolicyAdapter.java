package com.hightraffic.ecommerce.order.adapter.out.policy;

import com.hightraffic.ecommerce.order.application.port.out.OrderPolicyPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.service.OrderTimePolicy;
import org.springframework.stereotype.Component;

/**
 * 설정 기반 주문 시간 정책 어댑터
 * 
 * Spring의 설정 값을 도메인 정책 인터페이스로 변환하는 어댑터입니다.
 */
@Component
public class ConfigBasedOrderTimePolicyAdapter implements OrderTimePolicy, OrderPolicyPort {
    
    private final OrderBusinessRulesConfig config;
    
    public ConfigBasedOrderTimePolicyAdapter(OrderBusinessRulesConfig config) {
        this.config = config;
    }
    
    @Override
    public int getDuplicateOrderPreventionMinutes() {
        return config.getTime().getDuplicateOrderPreventionMinutes();
    }
    
    @Override
    public int getOrderCancellationHours() {
        return config.getTime().getOrderCancellationHours();
    }
}