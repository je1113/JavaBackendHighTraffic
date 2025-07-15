package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CacheHealthIndicator implements HealthIndicator {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public CacheHealthIndicator(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public Health health() {
        try {
            // Test Redis connection
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            
            Map<String, Object> details = new HashMap<>();
            details.put("redis", "UP");
            details.put("ping", pong);
            
            // Get Redis info
            try {
                details.put("info", redisTemplate.getConnectionFactory().getConnection().info());
            } catch (Exception e) {
                details.put("info", "Unable to retrieve Redis info");
            }
            
            return Health.up()
                .withDetails(details)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("redis", "DOWN")
                .build();
        }
    }
}