package com.hightraffic.ecommerce.order.adapter.out.external;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * 결제 어댑터 설정 프로퍼티
 * 
 * 외부 결제 서비스 연동에 필요한 설정값들을 관리합니다.
 */
@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentConfigurationProperties {
    
    // 기본 설정
    private String baseUrl = "https://api.payment-service.com";
    private String apiKey;
    private Duration timeout = Duration.ofSeconds(30);
    private int maxRetryAttempts = 3;
    private Duration retryDelay = Duration.ofSeconds(1);
    
    // 결제 제한 설정
    private BigDecimal maxPaymentAmount = new BigDecimal("1000000"); // 1백만원
    private BigDecimal minPaymentAmount = new BigDecimal("1000"); // 1천원
    
    // 연결 설정
    private int connectionTimeout = 5000; // 5초
    private int readTimeout = 30000; // 30초
    private int maxConnections = 100;
    
    // 서킷 브레이커 설정
    private int circuitBreakerFailureThreshold = 5;
    private Duration circuitBreakerTimeout = Duration.ofMinutes(1);
    
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
    
    public BigDecimal getMaxPaymentAmount() {
        return maxPaymentAmount;
    }
    
    public void setMaxPaymentAmount(BigDecimal maxPaymentAmount) {
        this.maxPaymentAmount = maxPaymentAmount;
    }
    
    public BigDecimal getMinPaymentAmount() {
        return minPaymentAmount;
    }
    
    public void setMinPaymentAmount(BigDecimal minPaymentAmount) {
        this.minPaymentAmount = minPaymentAmount;
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
}