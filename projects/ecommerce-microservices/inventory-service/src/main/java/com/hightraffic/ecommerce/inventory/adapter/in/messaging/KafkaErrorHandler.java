package com.hightraffic.ecommerce.inventory.adapter.in.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerAwareListenerErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 재고 서비스 Kafka 에러 핸들러
 * 
 * 메시지 처리 실패 시 Dead Letter Queue (DLQ)로 전송하고
 * 적절한 에러 처리를 수행합니다.
 */
@Component
public class KafkaErrorHandler implements ConsumerAwareListenerErrorHandler {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandler.class);
    
    private final KafkaProducer<String, String> dlqProducer;
    private final KafkaMessageMetrics messageMetrics;
    
    // DLQ 토픽 접미사
    private static final String DLQ_SUFFIX = "-dlq";
    
    // 재시도 가능한 예외 타입들
    private static final List<Class<? extends Exception>> RETRYABLE_EXCEPTIONS = List.of(
        org.springframework.dao.TransientDataAccessException.class,
        org.springframework.kafka.KafkaException.class,
        java.net.SocketTimeoutException.class,
        java.util.concurrent.TimeoutException.class,
        org.springframework.orm.jpa.JpaSystemException.class,
        org.springframework.transaction.TransactionException.class
    );
    
    // 재고 관련 비즈니스 예외들 (재시도 불가)
    private static final List<Class<? extends Exception>> NON_RETRYABLE_BUSINESS_EXCEPTIONS = List.of(
        com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException.class,
        com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException.class,
        com.hightraffic.ecommerce.inventory.domain.exception.InvalidStockOperationException.class,
        com.hightraffic.ecommerce.inventory.domain.exception.ReservationNotFoundException.class
    );
    
    public KafkaErrorHandler(KafkaProducer<String, String> dlqProducer,
                           KafkaMessageMetrics messageMetrics) {
        this.dlqProducer = dlqProducer;
        this.messageMetrics = messageMetrics;
    }
    
    @Override
    public Object handleError(Message<?> message, ListenerExecutionFailedException exception,
                            Consumer<?, ?> consumer) {
        
        log.error("재고 서비스 Kafka 메시지 처리 실패", exception);
        
        try {
            // 메시지 정보 추출
            ConsumerRecord<?, ?> record = extractConsumerRecord(message);
            if (record == null) {
                log.error("ConsumerRecord를 추출할 수 없습니다: {}", message);
                return null;
            }
            
            // 메트릭 업데이트
            messageMetrics.incrementErrorCount(record.topic());
            
            // 재시도 전략 결정
            RetryStrategy retryStrategy = determineRetryStrategy(exception.getCause(), record);
            
            switch (retryStrategy) {
                case RETRY:
                    log.warn("재시도 가능한 예외로 메시지 재처리: topic={}, partition={}, offset={}", 
                            record.topic(), record.partition(), record.offset());
                    messageMetrics.incrementRetryCount(record.topic());
                    throw exception; // 재시도를 위해 예외 다시 발생
                    
                case DLQ:
                    log.error("복구 불가능한 예외로 DLQ 전송: topic={}, partition={}, offset={}", 
                            record.topic(), record.partition(), record.offset());
                    sendToDLQ(record, exception);
                    commitOffset(consumer, record);
                    break;
                    
                case SKIP:
                    log.warn("비즈니스 규칙 위반으로 메시지 건너뛰기: topic={}, partition={}, offset={}", 
                            record.topic(), record.partition(), record.offset());
                    messageMetrics.incrementSkipCount(record.topic());
                    commitOffset(consumer, record);
                    break;
            }
            
        } catch (Exception e) {
            log.error("에러 핸들링 중 추가 오류 발생", e);
        }
        
        return null;
    }
    
    /**
     * 재시도 전략 결정
     */
    private RetryStrategy determineRetryStrategy(Throwable throwable, ConsumerRecord<?, ?> record) {
        if (throwable == null) {
            return RetryStrategy.DLQ;
        }
        
        // 비즈니스 예외는 재시도하지 않고 건너뛰기
        if (isBusinessException(throwable)) {
            log.warn("비즈니스 예외 감지: {}", throwable.getClass().getSimpleName());
            return RetryStrategy.SKIP;
        }
        
        // 기술적 예외는 재시도
        if (isRetryableException(throwable)) {
            return RetryStrategy.RETRY;
        }
        
        // 그 외는 DLQ로 전송
        return RetryStrategy.DLQ;
    }
    
    /**
     * 메시지를 DLQ로 전송
     */
    private void sendToDLQ(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            String dlqTopic = record.topic() + DLQ_SUFFIX;
            
            // DLQ 메시지 헤더 생성
            Map<String, String> dlqHeaders = createDLQHeaders(record, exception);
            
            // DLQ로 전송
            ProducerRecord<String, String> dlqRecord = new ProducerRecord<>(
                dlqTopic,
                record.partition(),
                record.key() != null ? record.key().toString() : null,
                record.value() != null ? record.value().toString() : null
            );
            
            // 헤더 추가
            dlqHeaders.forEach((key, value) -> 
                dlqRecord.headers().add(key, value.getBytes()));
            
            dlqProducer.send(dlqRecord, (metadata, error) -> {
                if (error != null) {
                    log.error("DLQ 전송 실패: topic={}, partition={}, offset={}", 
                            record.topic(), record.partition(), record.offset(), error);
                    messageMetrics.incrementDlqErrorCount(record.topic());
                } else {
                    log.info("DLQ 전송 성공: originalTopic={}, dlqTopic={}, partition={}, offset={}", 
                            record.topic(), dlqTopic, metadata.partition(), metadata.offset());
                    messageMetrics.incrementDlqCount(record.topic());
                }
            });
            
        } catch (Exception e) {
            log.error("DLQ 전송 중 오류 발생: topic={}, partition={}, offset={}", 
                    record.topic(), record.partition(), record.offset(), e);
        }
    }
    
    /**
     * DLQ 헤더 생성
     */
    private Map<String, String> createDLQHeaders(ConsumerRecord<?, ?> record, Exception exception) {
        Map<String, String> headers = new HashMap<>();
        
        headers.put("dlq.service", "inventory-service");
        headers.put("dlq.original.topic", record.topic());
        headers.put("dlq.original.partition", String.valueOf(record.partition()));
        headers.put("dlq.original.offset", String.valueOf(record.offset()));
        headers.put("dlq.original.timestamp", String.valueOf(record.timestamp()));
        headers.put("dlq.error.timestamp", Instant.now().toString());
        headers.put("dlq.error.class", exception.getClass().getName());
        headers.put("dlq.error.message", exception.getMessage() != null ? exception.getMessage() : "");
        
        // 예외 스택 트레이스 (첫 10줄만)
        String stackTrace = getStackTraceString(exception);
        headers.put("dlq.error.stacktrace", stackTrace);
        
        // 원본 헤더 복사
        if (record.headers() != null) {
            record.headers().forEach(header -> {
                String value = new String(header.value());
                headers.put("dlq.original.header." + header.key(), value);
            });
        }
        
        return headers;
    }
    
    /**
     * 오프셋 커밋
     */
    private void commitOffset(Consumer<?, ?> consumer, ConsumerRecord<?, ?> record) {
        try {
            TopicPartition topicPartition = new TopicPartition(record.topic(), record.partition());
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsetMap = 
                Map.of(topicPartition, new org.apache.kafka.clients.consumer.OffsetAndMetadata(record.offset() + 1));
            
            consumer.commitSync(offsetMap);
            
            log.debug("오프셋 커밋 완료: topic={}, partition={}, offset={}", 
                    record.topic(), record.partition(), record.offset() + 1);
            
        } catch (Exception e) {
            log.error("오프셋 커밋 실패: topic={}, partition={}, offset={}", 
                    record.topic(), record.partition(), record.offset(), e);
        }
    }
    
    /**
     * 재시도 가능한 예외인지 확인
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        return RETRYABLE_EXCEPTIONS.stream()
            .anyMatch(retryableClass -> retryableClass.isAssignableFrom(throwable.getClass()));
    }
    
    /**
     * 비즈니스 예외인지 확인
     */
    private boolean isBusinessException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        
        return NON_RETRYABLE_BUSINESS_EXCEPTIONS.stream()
            .anyMatch(businessClass -> businessClass.isAssignableFrom(throwable.getClass()));
    }
    
    /**
     * 메시지에서 ConsumerRecord 추출
     */
    @SuppressWarnings("unchecked")
    private ConsumerRecord<?, ?> extractConsumerRecord(Message<?> message) {
        Object payload = message.getPayload();
        if (payload instanceof ConsumerRecord<?, ?>) {
            return (ConsumerRecord<?, ?>) payload;
        }
        
        // 헤더에서 추출 시도
        Object record = message.getHeaders().get("kafka_receivedMessageKey");
        if (record instanceof ConsumerRecord<?, ?>) {
            return (ConsumerRecord<?, ?>) record;
        }
        
        return null;
    }
    
    /**
     * 스택 트레이스를 문자열로 변환 (제한된 길이)
     */
    private String getStackTraceString(Exception exception) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            exception.printStackTrace(pw);
            String stackTrace = sw.toString();
            
            // 스택 트레이스가 너무 길면 처음 1000자만 저장
            if (stackTrace.length() > 1000) {
                stackTrace = stackTrace.substring(0, 1000) + "... (truncated)";
            }
            
            return stackTrace;
        } catch (Exception e) {
            return "스택 트레이스 추출 실패: " + e.getMessage();
        }
    }
    
    /**
     * 재시도 전략 enum
     */
    private enum RetryStrategy {
        RETRY,    // 재시도
        DLQ,      // DLQ로 전송
        SKIP      // 건너뛰기
    }
}