package com.hightraffic.ecommerce.common.adapter.out.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 이벤트 발행기
 * 
 * 도메인 이벤트를 Kafka로 발행하는 공통 컴포넌트입니다.
 * 비동기 발행, 재시도, 에러 처리 등을 담당합니다.
 */
@Component
public class KafkaEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final EventPublishingMetrics metrics;
    
    // 설정 상수
    private static final int DEFAULT_SEND_TIMEOUT_SECONDS = 10;
    private static final String HEADER_EVENT_ID = "event-id";
    private static final String HEADER_EVENT_TYPE = "event-type";
    private static final String HEADER_AGGREGATE_ID = "aggregate-id";
    private static final String HEADER_AGGREGATE_TYPE = "aggregate-type";
    private static final String HEADER_CORRELATION_ID = "correlation-id";
    private static final String HEADER_SOURCE_SERVICE = "source-service";
    private static final String HEADER_TIMESTAMP = "timestamp";
    private static final String HEADER_VERSION = "version";
    
    public KafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            EventPublishingMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }
    
    /**
     * 도메인 이벤트 발행
     * 
     * @param event 발행할 도메인 이벤트
     * @param topic 대상 토픽
     * @return 발행 결과
     */
    public CompletableFuture<EventPublishResult> publish(DomainEvent event, String topic) {
        return publish(event, topic, event.getAggregateId());
    }
    
    /**
     * 도메인 이벤트 발행 (파티션 키 지정)
     * 
     * @param event 발행할 도메인 이벤트
     * @param topic 대상 토픽
     * @param partitionKey 파티션 키
     * @return 발행 결과
     */
    public CompletableFuture<EventPublishResult> publish(
            DomainEvent event, 
            String topic, 
            String partitionKey) {
        
        long startTime = System.currentTimeMillis();
        String eventId = event.getEventId();
        
        log.debug("이벤트 발행 시작: eventId={}, eventType={}, topic={}", 
            eventId, event.getEventType(), topic);
        
        try {
            // 이벤트 직렬화
            String payload = serializeEvent(event);
            
            // Kafka 레코드 생성
            ProducerRecord<String, String> record = createProducerRecord(
                topic, partitionKey, payload, event
            );
            
            // 비동기 발행
            CompletableFuture<SendResult<String, String>> future = 
                kafkaTemplate.send(record);
            
            // 결과 처리
            return future
                .orTimeout(DEFAULT_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((result, throwable) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    
                    if (throwable != null) {
                        return handlePublishFailure(event, topic, throwable, duration);
                    } else {
                        return handlePublishSuccess(event, topic, result, duration);
                    }
                });
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("이벤트 발행 실패: eventId={}, topic={}", eventId, topic, e);
            metrics.recordPublishError(topic, event.getEventType(), duration);
            
            return CompletableFuture.completedFuture(
                new EventPublishResult(false, eventId, null, e.getMessage())
            );
        }
    }
    
    /**
     * 여러 이벤트 일괄 발행
     * 
     * @param events 발행할 이벤트 목록
     * @param topic 대상 토픽
     * @return 발행 결과 목록
     */
    public CompletableFuture<BatchPublishResult> publishBatch(
            List<DomainEvent> events, 
            String topic) {
        
        log.debug("이벤트 일괄 발행 시작: count={}, topic={}", events.size(), topic);
        
        List<CompletableFuture<EventPublishResult>> futures = events.stream()
            .map(event -> publish(event, topic))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<EventPublishResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                long successCount = results.stream().filter(EventPublishResult::success).count();
                long failureCount = results.size() - successCount;
                
                log.info("이벤트 일괄 발행 완료: total={}, success={}, failure={}", 
                    results.size(), successCount, failureCount);
                
                return new BatchPublishResult(results, successCount, failureCount);
            });
    }
    
    /**
     * 이벤트 직렬화
     */
    private String serializeEvent(DomainEvent event) throws JsonProcessingException {
        return objectMapper.writeValueAsString(event);
    }
    
    /**
     * Kafka 프로듀서 레코드 생성
     */
    private ProducerRecord<String, String> createProducerRecord(
            String topic,
            String partitionKey,
            String payload,
            DomainEvent event) {
        
        Headers headers = createEventHeaders(event);
        
        return new ProducerRecord<>(
            topic,
            null,  // partition
            partitionKey,
            payload,
            headers
        );
    }
    
    /**
     * 이벤트 헤더 생성
     */
    private Headers createEventHeaders(DomainEvent event) {
        Headers headers = new RecordHeaders();
        
        // 필수 헤더
        addHeader(headers, HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, HEADER_AGGREGATE_ID, event.getAggregateId());
        addHeader(headers, HEADER_AGGREGATE_TYPE, event.getAggregateType());
        addHeader(headers, HEADER_TIMESTAMP, event.getTimestamp().toString());
        addHeader(headers, HEADER_VERSION, String.valueOf(event.getVersion()));
        
        // 선택적 헤더
        if (event.getCorrelationId() != null) {
            addHeader(headers, HEADER_CORRELATION_ID, event.getCorrelationId());
        }
        
        if (event.getSourceService() != null) {
            addHeader(headers, HEADER_SOURCE_SERVICE, event.getSourceService());
        }
        
        return headers;
    }
    
    /**
     * 헤더 추가 헬퍼 메서드
     */
    private void addHeader(Headers headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * 발행 성공 처리
     */
    private EventPublishResult handlePublishSuccess(
            DomainEvent event,
            String topic,
            SendResult<String, String> result,
            long duration) {
        
        RecordMetadata metadata = result.getRecordMetadata();
        
        log.info("이벤트 발행 성공: eventId={}, topic={}, partition={}, offset={}, duration={}ms",
            event.getEventId(), topic, metadata.partition(), metadata.offset(), duration);
        
        metrics.recordPublishSuccess(topic, event.getEventType(), duration);
        
        return new EventPublishResult(
            true,
            event.getEventId(),
            new PublishMetadata(
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                metadata.timestamp()
            ),
            null
        );
    }
    
    /**
     * 발행 실패 처리
     */
    private EventPublishResult handlePublishFailure(
            DomainEvent event,
            String topic,
            Throwable throwable,
            long duration) {
        
        log.error("이벤트 발행 실패: eventId={}, topic={}, duration={}ms",
            event.getEventId(), topic, duration, throwable);
        
        metrics.recordPublishError(topic, event.getEventType(), duration);
        
        return new EventPublishResult(
            false,
            event.getEventId(),
            null,
            throwable.getMessage()
        );
    }
    
    /**
     * 이벤트 발행 결과
     */
    public record EventPublishResult(
        boolean success,
        String eventId,
        PublishMetadata metadata,
        String errorMessage
    ) {
        public boolean isSuccess() {
            return success;
        }
        
        public boolean isFailure() {
            return !success;
        }
    }
    
    /**
     * 발행 메타데이터
     */
    public record PublishMetadata(
        String topic,
        int partition,
        long offset,
        long timestamp
    ) {}
    
    /**
     * 일괄 발행 결과
     */
    public record BatchPublishResult(
        List<EventPublishResult> results,
        long successCount,
        long failureCount
    ) {
        public boolean isAllSuccess() {
            return failureCount == 0;
        }
        
        public boolean hasFailures() {
            return failureCount > 0;
        }
        
        public List<EventPublishResult> getFailures() {
            return results.stream()
                .filter(EventPublishResult::isFailure)
                .toList();
        }
    }
}