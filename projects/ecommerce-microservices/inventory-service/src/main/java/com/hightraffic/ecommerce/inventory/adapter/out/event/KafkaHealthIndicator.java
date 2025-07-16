package com.hightraffic.ecommerce.inventory.adapter.out.event;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 연결 상태 모니터링
 * 
 * Kafka 클러스터와의 연결 상태를 확인하여 로깅과 모니터링을 제공합니다.
 */
@Component
public class KafkaHealthIndicator {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${app.kafka.health.timeout-ms:5000}")
    private long healthCheckTimeoutMs;
    
    @Value("${app.kafka.health.enabled:true}")
    private boolean healthCheckEnabled;
    
    public KafkaHealthIndicator(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Kafka 연결 상태 확인
     */
    public boolean isKafkaHealthy() {
        if (!healthCheckEnabled) {
            log.debug("Kafka health check is disabled");
            return true;
        }
        
        try {
            // Kafka Admin Client를 사용하여 연결 상태 확인
            Properties adminProps = new Properties();
            adminProps.putAll(kafkaTemplate.getProducerFactory().getConfigurationProperties());
            
            try (AdminClient adminClient = KafkaAdminClient.create(adminProps)) {
                // 토픽 목록 조회로 연결 확인 (타임아웃 설정)
                ListTopicsOptions options = new ListTopicsOptions()
                    .timeoutMs((int) healthCheckTimeoutMs);
                
                var topicsResult = adminClient.listTopics(options);
                var topics = topicsResult.names().get(healthCheckTimeoutMs, TimeUnit.MILLISECONDS);
                
                log.debug("Kafka health check successful: {} topics found", topics.size());
                return true;
                    
            }
        } catch (Exception e) {
            log.warn("Kafka health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Kafka 연결 상태 정보 로깅
     */
    public void logKafkaStatus() {
        boolean isHealthy = isKafkaHealthy();
        String bootstrapServers = getBootstrapServers();
        
        if (isHealthy) {
            log.info("Kafka connection status: HEALTHY, servers: {}", bootstrapServers);
        } else {
            log.warn("Kafka connection status: UNHEALTHY, servers: {}", bootstrapServers);
        }
    }
    
    private String getBootstrapServers() {
        Map<String, Object> configs = kafkaTemplate.getProducerFactory().getConfigurationProperties();
        return String.valueOf(configs.get("bootstrap.servers"));
    }
}