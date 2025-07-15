package com.hightraffic.ecommerce.inventory.adapter.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 재고 서비스 Kafka 메시지 메트릭
 * 
 * Kafka 메시지 처리와 관련된 다양한 메트릭을 수집하고 관리합니다.
 */
@Component
public class KafkaMessageMetrics {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMessageMetrics.class);
    
    private final MeterRegistry meterRegistry;
    
    // 메트릭 캐시
    private final ConcurrentMap<String, Counter> processedCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> retryCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> dlqCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> dlqErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> skipCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> processingTimers = new ConcurrentHashMap<>();
    
    // 메트릭 이름 상수
    private static final String SERVICE_NAME = "inventory-service";
    private static final String PROCESSED_COUNTER = "kafka.messages.processed";
    private static final String ERROR_COUNTER = "kafka.messages.error";
    private static final String RETRY_COUNTER = "kafka.messages.retry";
    private static final String DLQ_COUNTER = "kafka.messages.dlq";
    private static final String DLQ_ERROR_COUNTER = "kafka.messages.dlq.error";
    private static final String SKIP_COUNTER = "kafka.messages.skip";
    private static final String PROCESSING_TIMER = "kafka.messages.processing.time";
    
    public KafkaMessageMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * 메시지 처리 완료 카운트 증가
     */
    public void incrementProcessedCount(String topic) {
        try {
            Counter counter = processedCounters.computeIfAbsent(topic, t -> 
                Counter.builder(PROCESSED_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "success")
                    .description("재고 서비스에서 성공적으로 처리된 Kafka 메시지 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("메시지 처리 완료 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("메시지 처리 완료 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * 메시지 처리 에러 카운트 증가
     */
    public void incrementErrorCount(String topic) {
        try {
            Counter counter = errorCounters.computeIfAbsent(topic, t -> 
                Counter.builder(ERROR_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "error")
                    .description("재고 서비스에서 처리 실패한 Kafka 메시지 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("메시지 처리 에러 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("메시지 처리 에러 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * 메시지 재시도 카운트 증가
     */
    public void incrementRetryCount(String topic) {
        try {
            Counter counter = retryCounters.computeIfAbsent(topic, t -> 
                Counter.builder(RETRY_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "retry")
                    .description("재고 서비스에서 재시도된 Kafka 메시지 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("메시지 재시도 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("메시지 재시도 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * DLQ 전송 카운트 증가
     */
    public void incrementDlqCount(String topic) {
        try {
            Counter counter = dlqCounters.computeIfAbsent(topic, t -> 
                Counter.builder(DLQ_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "dlq")
                    .description("재고 서비스에서 DLQ로 전송된 Kafka 메시지 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("DLQ 전송 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("DLQ 전송 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * DLQ 전송 에러 카운트 증가
     */
    public void incrementDlqErrorCount(String topic) {
        try {
            Counter counter = dlqErrorCounters.computeIfAbsent(topic, t -> 
                Counter.builder(DLQ_ERROR_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "dlq-error")
                    .description("재고 서비스에서 DLQ 전송 실패한 Kafka 메시지 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("DLQ 전송 에러 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("DLQ 전송 에러 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * 메시지 건너뛰기 카운트 증가
     */
    public void incrementSkipCount(String topic) {
        try {
            Counter counter = skipCounters.computeIfAbsent(topic, t -> 
                Counter.builder(SKIP_COUNTER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .tag("status", "skip")
                    .description("재고 서비스에서 건너뛴 Kafka 메시지 수 (비즈니스 규칙 위반)")
                    .register(meterRegistry)
            );
            counter.increment();
            
            log.debug("메시지 건너뛰기 메트릭 업데이트: topic={}, count={}", topic, counter.count());
            
        } catch (Exception e) {
            log.warn("메시지 건너뛰기 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * 메시지 처리 시간 기록
     */
    public void recordProcessingTime(String topic, Duration duration) {
        try {
            Timer timer = processingTimers.computeIfAbsent(topic, t -> 
                Timer.builder(PROCESSING_TIMER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .description("재고 서비스 Kafka 메시지 처리 시간")
                    .register(meterRegistry)
            );
            timer.record(duration);
            
            log.debug("메시지 처리 시간 메트릭 업데이트: topic={}, duration={}ms", topic, duration.toMillis());
            
        } catch (Exception e) {
            log.warn("메시지 처리 시간 메트릭 업데이트 실패: topic={}", topic, e);
        }
    }
    
    /**
     * Timer.Sample 시작
     */
    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * Timer.Sample 종료
     */
    public void stopProcessingTimer(Timer.Sample sample, String topic) {
        try {
            Timer timer = processingTimers.computeIfAbsent(topic, t -> 
                Timer.builder(PROCESSING_TIMER)
                    .tag("service", SERVICE_NAME)
                    .tag("topic", t)
                    .description("재고 서비스 Kafka 메시지 처리 시간")
                    .register(meterRegistry)
            );
            sample.stop(timer);
            
        } catch (Exception e) {
            log.warn("메시지 처리 시간 타이머 종료 실패: topic={}", topic, e);
        }
    }
    
    /**
     * 특정 토픽의 처리 성공률 계산
     */
    public double getSuccessRate(String topic) {
        try {
            Counter processedCounter = processedCounters.get(topic);
            Counter errorCounter = errorCounters.get(topic);
            
            if (processedCounter == null) {
                return 0.0;
            }
            
            double processed = processedCounter.count();
            double errors = errorCounter != null ? errorCounter.count() : 0.0;
            double total = processed + errors;
            
            return total > 0 ? (processed / total) * 100.0 : 0.0;
            
        } catch (Exception e) {
            log.warn("성공률 계산 실패: topic={}", topic, e);
            return 0.0;
        }
    }
    
    /**
     * 특정 토픽의 평균 처리 시간 조회
     */
    public double getAverageProcessingTime(String topic) {
        try {
            Timer timer = processingTimers.get(topic);
            return timer != null ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
            
        } catch (Exception e) {
            log.warn("평균 처리 시간 조회 실패: topic={}", topic, e);
            return 0.0;
        }
    }
    
    /**
     * 메트릭 요약 정보 로깅
     */
    public void logMetricsSummary(String topic) {
        try {
            double successRate = getSuccessRate(topic);
            double avgProcessingTime = getAverageProcessingTime(topic);
            
            Counter processedCounter = processedCounters.get(topic);
            Counter errorCounter = errorCounters.get(topic);
            Counter retryCounter = retryCounters.get(topic);
            Counter dlqCounter = dlqCounters.get(topic);
            Counter skipCounter = skipCounters.get(topic);
            
            log.info("재고 서비스 Kafka 메트릭 요약 - topic: {}, " +
                    "processed: {}, errors: {}, retries: {}, dlq: {}, skips: {}, " +
                    "successRate: {:.2f}%, avgProcessingTime: {:.2f}ms", 
                    topic,
                    processedCounter != null ? (long) processedCounter.count() : 0,
                    errorCounter != null ? (long) errorCounter.count() : 0,
                    retryCounter != null ? (long) retryCounter.count() : 0,
                    dlqCounter != null ? (long) dlqCounter.count() : 0,
                    skipCounter != null ? (long) skipCounter.count() : 0,
                    successRate,
                    avgProcessingTime
            );
            
        } catch (Exception e) {
            log.warn("메트릭 요약 로깅 실패: topic={}", topic, e);
        }
    }
}