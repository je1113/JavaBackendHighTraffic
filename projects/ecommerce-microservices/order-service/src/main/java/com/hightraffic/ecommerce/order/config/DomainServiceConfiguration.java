package com.hightraffic.ecommerce.order.config;

import com.hightraffic.ecommerce.order.domain.service.OrderPricingPolicy;
import com.hightraffic.ecommerce.order.domain.service.OrderTimePolicy;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import com.hightraffic.ecommerce.order.domain.service.OrderDomainService;
import com.hightraffic.ecommerce.order.domain.service.OrderPricingService;
import com.hightraffic.ecommerce.order.domain.service.OrderValidationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 도메인 서비스 빈 설정
 * 
 * 도메인 서비스들을 Spring Bean으로 등록하여
 * 애플리케이션 계층에서 사용할 수 있도록 합니다.
 */
@Configuration
public class DomainServiceConfiguration {
    
    @Bean
    public OrderDomainService orderDomainService(
            OrderRepository orderRepository,
            OrderPricingPolicy pricingPolicy) {
        return new OrderDomainService(orderRepository, pricingPolicy);
    }
    
    @Bean
    public OrderPricingService orderPricingService(
            OrderRepository orderRepository,
            OrderPricingPolicy pricingPolicy) {
        return new OrderPricingService(orderRepository, pricingPolicy);
    }
    
    @Bean
    public OrderValidationService orderValidationService(
            OrderRepository orderRepository,
            OrderTimePolicy timePolicy) {
        return new OrderValidationService(orderRepository, timePolicy);
    }
}