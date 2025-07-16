package com.hightraffic.ecommerce.inventory.adapter.out.event;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Dead Letter Queue 처리기
 * 
 * 발행에 실패한 이벤트를 DLQ로 전송하여 나중에 재처리할 수 있도록 합니다.
 */
@Component
public class DeadLetterQueueHandler {
    
    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueHandler.class);
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${app.kafka.topics.dead-letter-queue:inventory.dlq}")
    private String deadLetterQueueTopic;
    
    @Value("${app.kafka.dlq.enabled:true}")
    private boolean dlqEnabled;
    
    public DeadLetterQueueHandler(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * 실패한 이벤트를 DLQ로 전송
     */
    public CompletableFuture<Boolean> sendToDeadLetterQueue(
            DomainEvent originalEvent, 
            String originalTopic, 
            Throwable error,
            int attemptCount) {
        
        if (!dlqEnabled) {
            log.warn("DLQ is disabled. Dropping failed event: {} from topic: {}", 
                originalEvent.getEventId(), originalTopic);
            return CompletableFuture.completedFuture(false);
        }
        
        try {
            // DLQ 메시지 생성
            DeadLetterMessage dlqMessage = new DeadLetterMessage(
                originalEvent.getEventId(),
                originalEvent.getEventType(),
                originalTopic,
                originalEvent.getAggregateId(),
                error.getMessage(),
                attemptCount,
                Instant.now(),
                originalEvent
            );
            
            log.warn("Sending event to DLQ: eventId={}, originalTopic={}, error={}, attempts={}", 
                originalEvent.getEventId(), originalTopic, error.getMessage(), attemptCount);
            
            return kafkaTemplate.send(deadLetterQueueTopic, originalEvent.getAggregateId(), 
                    serializeToJson(dlqMessage))
                .thenApply(result -> {
                    log.info("Successfully sent event to DLQ: eventId={}, dlqTopic={}", 
                        originalEvent.getEventId(), deadLetterQueueTopic);
                    return true;
                })
                .exceptionally(dlqError -> {
                    log.error("Failed to send event to DLQ: eventId={}, dlqError={}", 
                        originalEvent.getEventId(), dlqError.getMessage(), dlqError);
                    return false;
                });
                
        } catch (Exception e) {
            log.error("Error creating DLQ message for event: {}", originalEvent.getEventId(), e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * DLQ에서 이벤트 재처리 가능 여부 확인
     */
    public boolean canRetryFromDlq(int currentAttemptCount, int maxRetryAttempts) {
        return currentAttemptCount < maxRetryAttempts;
    }
    
    private String serializeToJson(DeadLetterMessage message) {
        // 간단한 JSON 직렬화 (실제로는 ObjectMapper 사용)
        return String.format("""
            {
                "eventId": "%s",
                "eventType": "%s",
                "originalTopic": "%s",
                "aggregateId": "%s",
                "errorMessage": "%s",
                "attemptCount": %d,
                "failedAt": "%s",
                "originalEvent": %s
            }
            """, 
            message.eventId(), 
            message.eventType(),
            message.originalTopic(),
            message.aggregateId(),
            message.errorMessage().replace("\"", "\\\""),
            message.attemptCount(),
            message.failedAt(),
            "{}"); // 원본 이벤트는 별도 직렬화 필요
    }
    
    /**
     * DLQ 메시지 레코드
     */
    public record DeadLetterMessage(
        String eventId,
        String eventType,
        String originalTopic,
        String aggregateId,
        String errorMessage,
        int attemptCount,
        Instant failedAt,
        DomainEvent originalEvent
    ) {}
}