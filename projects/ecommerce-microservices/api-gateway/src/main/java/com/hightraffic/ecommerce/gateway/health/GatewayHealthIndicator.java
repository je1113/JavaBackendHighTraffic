package com.hightraffic.ecommerce.gateway.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
public class GatewayHealthIndicator implements ReactiveHealthIndicator {
    
    private final ReactiveDiscoveryClient discoveryClient;
    
    public GatewayHealthIndicator(ReactiveDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }
    
    @Override
    public Mono<Health> health() {
        return checkDownstreamServices()
                .map(services -> {
                    if (services.isEmpty()) {
                        return Health.down()
                                .withDetail("message", "No downstream services discovered")
                                .build();
                    }
                    
                    return Health.up()
                            .withDetail("discovered_services", services)
                            .withDetail("service_count", services.size())
                            .build();
                })
                .onErrorResume(error -> Mono.just(
                        Health.down()
                                .withDetail("error", error.getMessage())
                                .build()
                ));
    }
    
    private Mono<Map<String, Object>> checkDownstreamServices() {
        return discoveryClient.getServices()
                .collectList()
                .map(services -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("services", services);
                    result.put("inventory-service", services.contains("INVENTORY-SERVICE"));
                    result.put("order-service", services.contains("ORDER-SERVICE"));
                    return result;
                });
    }
}