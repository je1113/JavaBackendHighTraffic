package com.hightraffic.ecommerce.inventory.adapter.in.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 재고 서비스 Kafka 설정
 * 
 * Kafka Consumer와 Producer 설정을 담당합니다.
 */
@Configuration
@EnableKafka
public class KafkaConfiguration {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${app.kafka.consumer.group-id.inventory:inventory-service-group}")
    private String consumerGroupId;
    
    @Value("${app.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;
    
    @Value("${app.kafka.consumer.enable-auto-commit:false}")
    private Boolean enableAutoCommit;
    
    @Value("${app.kafka.consumer.max-poll-records:10}")
    private Integer maxPollRecords;
    
    @Value("${app.kafka.consumer.session-timeout-ms:30000}")
    private Integer sessionTimeoutMs;
    
    @Value("${app.kafka.consumer.heartbeat-interval-ms:3000}")
    private Integer heartbeatIntervalMs;
    
    @Value("${app.kafka.consumer.concurrency:3}")
    private Integer concurrency;
    
    @Value("${app.kafka.producer.retries:3}")
    private Integer retries;
    
    @Value("${app.kafka.producer.batch-size:16384}")
    private Integer batchSize;
    
    @Value("${app.kafka.producer.linger-ms:100}")
    private Integer lingerMs;
    
    @Value("${app.kafka.producer.buffer-memory:33554432}")
    private Long bufferMemory;
    
    /**
     * Kafka Consumer Factory 설정
     */
    @Bean
    public ConsumerFactory<String, String> inventoryConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // 오프셋 관리
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        
        // 성능 최적화
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        
        // 신뢰성 향상
        configProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5분
        
        // 재고 서비스 특화 설정
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024); // 1KB
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // 0.5초
        
        log.info("재고 서비스 Kafka Consumer Factory 설정 완료: groupId={}, servers={}", 
                consumerGroupId, bootstrapServers);
        
        return new DefaultKafkaConsumerFactory<>(configProps);
    }
    
    /**
     * Kafka Listener Container Factory 설정
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> inventoryKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaErrorHandler errorHandler) {
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        
        // 동시성 설정
        factory.setConcurrency(concurrency);
        
        // 수동 커밋 설정
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        // 에러 핸들러 설정
        factory.setCommonErrorHandler(errorHandler);
        
        // 재고 서비스 특화 설정
        factory.getContainerProperties().setPollTimeout(3000);
        factory.getContainerProperties().setIdleEventInterval(30000L); // 30초
        
        // 배치 리스너 비활성화 (개별 메시지 처리)
        factory.setBatchListener(false);
        
        log.info("재고 서비스 Kafka Listener Container Factory 설정 완료: concurrency={}", concurrency);
        
        return factory;
    }
    
    /**
     * Kafka Producer Factory 설정
     */
    @Bean
    public ProducerFactory<String, String> inventoryProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // 신뢰성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 복제본 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 방지
        
        // 성능 최적화
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        
        // 타임아웃 설정
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // 30초
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 2분
        
        // 재고 서비스 특화 설정
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1); // 순서 보장
        
        log.info("재고 서비스 Kafka Producer Factory 설정 완료: servers={}", bootstrapServers);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Kafka Template 설정
     */
    @Bean
    public KafkaTemplate<String, String> inventoryKafkaTemplate(
            ProducerFactory<String, String> producerFactory) {
        
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        
        // 재고 이벤트 발행 시 실패 처리
        template.setProducerInterceptors(null);
        
        log.info("재고 서비스 Kafka Template 설정 완료");
        
        return template;
    }
    
    /**
     * DLQ 전용 Kafka Producer 설정
     */
    @Bean
    public KafkaProducer<String, String> dlqKafkaProducer() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // DLQ 특화 설정 (빠른 실패)
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // 리더만 확인
        configProps.put(ProducerConfig.RETRIES_CONFIG, 1); // 최소 재시도
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000); // 10초
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 30000); // 30초
        
        // 배치 비활성화 (즉시 전송)
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 0);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        
        log.info("DLQ Kafka Producer 설정 완료");
        
        return new KafkaProducer<>(configProps);
    }
}