package com.hightraffic.ecommerce.order.adapter.out.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 재고 검증 어댑터 설정 프로퍼티
 * 
 * Inventory Service 연동에 필요한 설정값들을 관리합니다.
 */
@Component
@ConfigurationProperties(prefix = "app.inventory")
public class StockValidationConfigurationProperties {
    
    // 기본 설정
    private String baseUrl = "http://inventory-service:8080";
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxRetryAttempts = 3;
    private Duration retryDelay = Duration.ofSeconds(1);
    
    // 재고 검증 설정
    private int batchSize = 100; // 일괄 처리 최대 크기
    private Duration cacheTimeout = Duration.ofSeconds(30); // 캐시 만료 시간
    private boolean enableCaching = true;
    
    // 연결 설정
    private int connectionTimeout = 5000; // 5초
    private int readTimeout = 30000; // 30초
    private int maxConnections = 50;
    
    // 서킷 브레이커 설정
    private int circuitBreakerFailureThreshold = 5;
    private Duration circuitBreakerTimeout = Duration.ofMinutes(1);
    
    // 재고 예약 설정
    private Duration reservationTimeout = Duration.ofMinutes(10); // 예약 유효 시간
    private int maxReservationAttempts = 3;
    
    // Getters and Setters
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
    
    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }
    
    public void setMaxRetryAttempts(int maxRetryAttempts) {
        this.maxRetryAttempts = maxRetryAttempts;
    }
    
    public Duration getRetryDelay() {
        return retryDelay;
    }
    
    public void setRetryDelay(Duration retryDelay) {
        this.retryDelay = retryDelay;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public Duration getCacheTimeout() {
        return cacheTimeout;
    }
    
    public void setCacheTimeout(Duration cacheTimeout) {
        this.cacheTimeout = cacheTimeout;
    }
    
    public boolean isEnableCaching() {
        return enableCaching;
    }
    
    public void setEnableCaching(boolean enableCaching) {
        this.enableCaching = enableCaching;
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    
    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }
    
    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }
    
    public Duration getCircuitBreakerTimeout() {
        return circuitBreakerTimeout;
    }
    
    public void setCircuitBreakerTimeout(Duration circuitBreakerTimeout) {
        this.circuitBreakerTimeout = circuitBreakerTimeout;
    }
    
    public Duration getReservationTimeout() {
        return reservationTimeout;
    }
    
    public void setReservationTimeout(Duration reservationTimeout) {
        this.reservationTimeout = reservationTimeout;
    }
    
    public int getMaxReservationAttempts() {
        return maxReservationAttempts;
    }
    
    public void setMaxReservationAttempts(int maxReservationAttempts) {
        this.maxReservationAttempts = maxReservationAttempts;
    }
}