package com.hightraffic.ecommerce.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConfigurationProperties(prefix = "api-keys")
public class ApiKeyConfiguration {
    
    private boolean enabled = false;
    private String headerName = "X-API-Key";
    private List<ApiKeyInfo> keys = new ArrayList<>();
    private final Map<String, ApiKeyInfo> keyMap = new ConcurrentHashMap<>();
    
    public void init() {
        keyMap.clear();
        for (ApiKeyInfo keyInfo : keys) {
            keyMap.put(keyInfo.getKey(), keyInfo);
        }
    }
    
    public Mono<ApiKeyInfo> findByKey(String key) {
        ApiKeyInfo info = keyMap.get(key);
        return info != null ? Mono.just(info) : Mono.empty();
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getHeaderName() {
        return headerName;
    }
    
    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
    
    public List<ApiKeyInfo> getKeys() {
        return keys;
    }
    
    public void setKeys(List<ApiKeyInfo> keys) {
        this.keys = keys;
        init();
    }
    
    public static class ApiKeyInfo {
        private String key;
        private String name;
        private int rateLimit;
        
        public String getKey() {
            return key;
        }
        
        public void setKey(String key) {
            this.key = key;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getRateLimit() {
            return rateLimit;
        }
        
        public void setRateLimit(int rateLimit) {
            this.rateLimit = rateLimit;
        }
    }
}