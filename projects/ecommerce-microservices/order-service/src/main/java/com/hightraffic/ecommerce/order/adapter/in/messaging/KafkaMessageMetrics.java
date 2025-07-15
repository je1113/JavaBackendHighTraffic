package com.hightraffic.ecommerce.order.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Kafka 메시지 처리 메트릭
 * 
 * 메시지 처리 성공, 실패, 재시도, DLQ 전송 등의
 * 메트릭을 수집하고 모니터링합니다.
 */
@Component
public class KafkaMessageMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // 카운터 캐시
    private final ConcurrentMap<String, Counter> processedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> retryCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> dlqErrorCounters = new ConcurrentHashMap<>();
    
    // 타이머 캐시
    private final ConcurrentMap<String, Timer> processingTimers = new ConcurrentHashMap<>();
    
    public KafkaMessageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * 메시지 처리 성공 카운트 증가
     */
    public void incrementProcessedCount(String topic) {
        getProcessedCounter(topic).increment();
    }
    
    /**
     * 메시지 처리 에러 카운트 증가
     */
    public void incrementErrorCount(String topic) {
        getErrorCounter(topic).increment();
    }
    
    /**
     * 메시지 재시도 카운트 증가
     */
    public void incrementRetryCount(String topic) {
        getRetryCounter(topic).increment();
    }
    
    /**
     * DLQ 전송 카운트 증가
     */
    public void incrementDlqCount(String topic) {
        getDlqCounter(topic).increment();
    }
    
    /**
     * DLQ 전송 실패 카운트 증가
     */
    public void incrementDlqErrorCount(String topic) {
        getDlqErrorCounter(topic).increment();
    }
    
    /**
     * 메시지 처리 시간 기록
     */
    public Timer.Sample startTimer(String topic) {
        return Timer.start(meterRegistry);
    }
    
    public void recordProcessingTime(String topic, Timer.Sample sample) {
        sample.stop(getProcessingTimer(topic));
    }
    
    // === Private Helper Methods ===
    
    private Counter getProcessedCounter(String topic) {
        return processedCounters.computeIfAbsent(topic, t -> 
            Counter.builder("kafka.messages.processed")
                .description("Number of successfully processed messages")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
    
    private Counter getErrorCounter(String topic) {
        return errorCounters.computeIfAbsent(topic, t -> 
            Counter.builder("kafka.messages.error")
                .description("Number of failed message processing attempts")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
    
    private Counter getRetryCounter(String topic) {
        return retryCounters.computeIfAbsent(topic, t -> 
            Counter.builder("kafka.messages.retry")
                .description("Number of message processing retries")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
    
    private Counter getDlqCounter(String topic) {
        return dlqCounters.computeIfAbsent(topic, t -> 
            Counter.builder("kafka.messages.dlq")
                .description("Number of messages sent to DLQ")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
    
    private Counter getDlqErrorCounter(String topic) {
        return dlqErrorCounters.computeIfAbsent(topic, t -> 
            Counter.builder("kafka.messages.dlq.error")
                .description("Number of failed DLQ send attempts")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
    
    private Timer getProcessingTimer(String topic) {
        return processingTimers.computeIfAbsent(topic, t -> 
            Timer.builder("kafka.messages.processing.time")
                .description("Message processing time")
                .tag("topic", t)
                .register(meterRegistry)
        );
    }
}