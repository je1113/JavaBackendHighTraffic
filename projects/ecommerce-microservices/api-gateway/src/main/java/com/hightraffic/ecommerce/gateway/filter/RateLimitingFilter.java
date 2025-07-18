package com.hightraffic.ecommerce.gateway.filter;

import com.hightraffic.ecommerce.gateway.config.ApiKeyConfiguration;
import com.hightraffic.ecommerce.gateway.config.RateLimitingConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);
    
    @Value("${rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;
    
    @Value("${api-keys.header-name:X-API-Key}")
    private String apiKeyHeaderName;
    
    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final RateLimitingConfiguration rateLimitingConfig;
    private final ApiKeyConfiguration apiKeyConfig;
    
    public RateLimitingFilter(ReactiveRedisTemplate<String, String> redisTemplate,
                            RateLimitingConfiguration rateLimitingConfig,
                            ApiKeyConfiguration apiKeyConfig) {
        this.redisTemplate = redisTemplate;
        this.rateLimitingConfig = rateLimitingConfig;
        this.apiKeyConfig = apiKeyConfig;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!rateLimitingEnabled) {
            return chain.filter(exchange);
        }
        
        return getKey(exchange)
                .flatMap(key -> {
                    RateLimit limit = getRateLimit(exchange, key);
                    return checkRateLimit(key, limit)
                            .flatMap(allowed -> {
                                if (allowed) {
                                    addRateLimitHeaders(exchange, limit);
                                    return chain.filter(exchange);
                                } else {
                                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                                    exchange.getResponse().getHeaders().add("X-Rate-Limit-Retry-After", "1");
                                    logger.warn("Rate limit exceeded for key: {}", key);
                                    return exchange.getResponse().setComplete();
                                }
                            });
                })
                .switchIfEmpty(chain.filter(exchange));
    }
    
    private Mono<String> getKey(ServerWebExchange exchange) {
        // First check for API key
        String apiKey = exchange.getRequest().getHeaders().getFirst(apiKeyHeaderName);
        if (apiKey != null) {
            return apiKeyConfig.findByKey(apiKey)
                    .map(info -> "api-key:" + info.getName())
                    .switchIfEmpty(Mono.just("ip:" + getClientIp(exchange)));
        }
        
        // Then check for authenticated user
        return exchange.getPrincipal()
                .cast(Authentication.class)
                .map(auth -> "user:" + auth.getName())
                .switchIfEmpty(Mono.just("ip:" + getClientIp(exchange)));
    }
    
    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return exchange.getRequest().getRemoteAddress() != null ? 
                exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : "unknown";
    }
    
    private RateLimit getRateLimit(ServerWebExchange exchange, String key) {
        int replenishRate = rateLimitingConfig.getDefaultReplenishRate();
        int burstCapacity = rateLimitingConfig.getDefaultBurstCapacity();
        
        // Check for route-specific limits
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route != null) {
            RateLimitingConfiguration.RouteLimit routeLimit = 
                    rateLimitingConfig.getRoutes().get(route.getId());
            if (routeLimit != null) {
                replenishRate = routeLimit.getReplenishRate();
                burstCapacity = routeLimit.getBurstCapacity();
            }
        }
        
        return new RateLimit(replenishRate, burstCapacity);
    }
    
    private Mono<Boolean> checkRateLimit(String key, RateLimit limit) {
        String rateLimitKey = "rate-limit:" + key;
        String timestampKey = rateLimitKey + ":timestamp";
        
        Instant now = Instant.now();
        long currentSecond = now.getEpochSecond();
        
        return redisTemplate.opsForValue().get(timestampKey)
                .defaultIfEmpty(String.valueOf(currentSecond))
                .flatMap(lastTimestamp -> {
                    long lastSecond = Long.parseLong(lastTimestamp);
                    
                    if (currentSecond != lastSecond) {
                        // New second, reset counter
                        return redisTemplate.opsForValue().set(rateLimitKey, "1", Duration.ofSeconds(2))
                                .then(redisTemplate.opsForValue().set(timestampKey, String.valueOf(currentSecond), Duration.ofSeconds(2)))
                                .thenReturn(true);
                    } else {
                        // Same second, increment counter
                        return redisTemplate.opsForValue().increment(rateLimitKey)
                                .map(count -> count <= limit.replenishRate)
                                .defaultIfEmpty(false);
                    }
                });
    }
    
    private void addRateLimitHeaders(ServerWebExchange exchange, RateLimit limit) {
        exchange.getResponse().getHeaders().add("X-Rate-Limit-Burst-Capacity", 
                String.valueOf(limit.burstCapacity));
        exchange.getResponse().getHeaders().add("X-Rate-Limit-Replenish-Rate",
                String.valueOf(limit.replenishRate));
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
    
    private static class RateLimit {
        final int replenishRate;
        final int burstCapacity;
        
        RateLimit(int replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }
    }
}