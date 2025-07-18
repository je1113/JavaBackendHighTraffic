package com.hightraffic.ecommerce.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingConfiguration {
    
    private boolean enabled = true;
    private String repository = "REDIS";
    private int defaultReplenishRate = 100;
    private int defaultBurstCapacity = 200;
    private int defaultRequestedTokens = 1;
    private Map<String, RouteLimit> routes = new HashMap<>();
    
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getRepository() {
        return repository;
    }
    
    public void setRepository(String repository) {
        this.repository = repository;
    }
    
    public int getDefaultReplenishRate() {
        return defaultReplenishRate;
    }
    
    public void setDefaultReplenishRate(int defaultReplenishRate) {
        this.defaultReplenishRate = defaultReplenishRate;
    }
    
    public int getDefaultBurstCapacity() {
        return defaultBurstCapacity;
    }
    
    public void setDefaultBurstCapacity(int defaultBurstCapacity) {
        this.defaultBurstCapacity = defaultBurstCapacity;
    }
    
    public int getDefaultRequestedTokens() {
        return defaultRequestedTokens;
    }
    
    public void setDefaultRequestedTokens(int defaultRequestedTokens) {
        this.defaultRequestedTokens = defaultRequestedTokens;
    }
    
    public Map<String, RouteLimit> getRoutes() {
        return routes;
    }
    
    public void setRoutes(Map<String, RouteLimit> routes) {
        this.routes = routes;
    }
    
    public static class RouteLimit {
        private int replenishRate;
        private int burstCapacity;
        
        public int getReplenishRate() {
            return replenishRate;
        }
        
        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }
        
        public int getBurstCapacity() {
            return burstCapacity;
        }
        
        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }
}