package com.hightraffic.ecommerce.common.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer 설정
 * 
 * 이벤트 발행을 위한 Kafka Producer 설정을 담당합니다.
 * 성능과 신뢰성을 위한 최적화 설정을 포함합니다.
 */
@Configuration
public class KafkaProducerConfiguration {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${app.kafka.producer.acks:all}")
    private String acks;
    
    @Value("${app.kafka.producer.retries:3}")
    private Integer retries;
    
    @Value("${app.kafka.producer.batch-size:16384}")
    private Integer batchSize;
    
    @Value("${app.kafka.producer.linger-ms:100}")
    private Integer lingerMs;
    
    @Value("${app.kafka.producer.buffer-memory:33554432}")
    private Long bufferMemory;
    
    @Value("${app.kafka.producer.compression-type:snappy}")
    private String compressionType;
    
    @Value("${app.kafka.producer.max-in-flight-requests:5}")
    private Integer maxInFlightRequests;
    
    @Value("${app.kafka.producer.enable-idempotence:true}")
    private Boolean enableIdempotence;
    
    @Value("${app.kafka.producer.request-timeout-ms:30000}")
    private Integer requestTimeoutMs;
    
    @Value("${app.kafka.producer.delivery-timeout-ms:120000}")
    private Integer deliveryTimeoutMs;
    
    /**
     * Kafka Producer Factory 빈 생성
     */
    @Bean
    public ProducerFactory<String, String> kafkaProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // 기본 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // 신뢰성 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);
        
        // 성능 최적화
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);
        
        // 순서 보장 및 동시성
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlightRequests);
        
        // 타임아웃 설정
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        
        // 메트릭 설정
        configProps.put(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, 
            "com.hightraffic.ecommerce.common.adapter.out.kafka.KafkaMetricsReporter");
        
        // 재시도 정책
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Kafka Template 빈 생성
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(kafkaProducerFactory());
        
        // 프로듀서 리스너 설정 (선택적)
        template.setProducerListener(new EventPublishingProducerListener());
        
        return template;
    }
    
    /**
     * ObjectMapper 빈 생성
     * 이벤트 직렬화를 위한 설정
     */
    @Bean
    public ObjectMapper eventObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Java 8 시간 타입 지원
        mapper.registerModule(new JavaTimeModule());
        
        // 날짜를 타임스탬프가 아닌 ISO 형식으로
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Null 값 무시
        mapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        
        // 알 수 없는 속성 무시 (버전 호환성)
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        return mapper;
    }
    
    /**
     * 성능과 신뢰성 설정 설명:
     * 
     * 1. acks=all: 모든 복제본이 메시지를 받을 때까지 대기 (데이터 손실 방지)
     * 2. enable.idempotence=true: 중복 메시지 방지
     * 3. compression.type=snappy: 네트워크 대역폭 절약
     * 4. batch.size & linger.ms: 배치 처리로 처리량 향상
     * 5. buffer.memory: 프로듀서 메모리 버퍼 크기
     * 6. max.in.flight.requests: 동시 요청 수 (순서 보장과 성능의 균형)
     */
}