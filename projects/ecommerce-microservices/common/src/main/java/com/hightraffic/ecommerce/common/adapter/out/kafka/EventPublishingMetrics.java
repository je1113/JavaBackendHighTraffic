package com.hightraffic.ecommerce.common.adapter.out.kafka;

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
 * 이벤트 발행 메트릭
 * 
 * Kafka 이벤트 발행과 관련된 메트릭을 수집하고 관리합니다.
 */
@Component
public class EventPublishingMetrics {
    
    private static final Logger log = LoggerFactory.getLogger(EventPublishingMetrics.class);
    
    private final MeterRegistry meterRegistry;
    
    // 메트릭 캐시
    private final ConcurrentMap<String, Counter> publishSuccessCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> publishErrorCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> publishTimers = new ConcurrentHashMap<>();
    
    // 메트릭 이름 상수
    private static final String PUBLISH_SUCCESS_COUNTER = "kafka.event.publish.success";
    private static final String PUBLISH_ERROR_COUNTER = "kafka.event.publish.error";
    private static final String PUBLISH_TIMER = "kafka.event.publish.duration";
    
    public EventPublishingMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * 발행 성공 기록
     */
    public void recordPublishSuccess(String topic, String eventType, long durationMs) {
        try {
            // 성공 카운터
            String key = createKey(topic, eventType);
            Counter counter = publishSuccessCounters.computeIfAbsent(key, k -> 
                Counter.builder(PUBLISH_SUCCESS_COUNTER)
                    .tag("topic", topic)
                    .tag("event_type", eventType)
                    .tag("status", "success")
                    .description("성공적으로 발행된 이벤트 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            // 처리 시간
            recordPublishDuration(topic, eventType, durationMs);
            
            log.debug("이벤트 발행 성공 메트릭 기록: topic={}, eventType={}, duration={}ms", 
                topic, eventType, durationMs);
            
        } catch (Exception e) {
            log.warn("발행 성공 메트릭 기록 실패: topic={}, eventType={}", topic, eventType, e);
        }
    }
    
    /**
     * 발행 실패 기록
     */
    public void recordPublishError(String topic, String eventType, long durationMs) {
        try {
            // 실패 카운터
            String key = createKey(topic, eventType);
            Counter counter = publishErrorCounters.computeIfAbsent(key, k -> 
                Counter.builder(PUBLISH_ERROR_COUNTER)
                    .tag("topic", topic)
                    .tag("event_type", eventType)
                    .tag("status", "error")
                    .description("발행 실패한 이벤트 수")
                    .register(meterRegistry)
            );
            counter.increment();
            
            // 처리 시간 (실패도 기록)
            recordPublishDuration(topic, eventType, durationMs);
            
            log.debug("이벤트 발행 실패 메트릭 기록: topic={}, eventType={}, duration={}ms", 
                topic, eventType, durationMs);
            
        } catch (Exception e) {
            log.warn("발행 실패 메트릭 기록 실패: topic={}, eventType={}", topic, eventType, e);
        }
    }
    
    /**
     * 발행 시간 기록
     */
    private void recordPublishDuration(String topic, String eventType, long durationMs) {
        String key = createKey(topic, eventType);
        Timer timer = publishTimers.computeIfAbsent(key, k -> 
            Timer.builder(PUBLISH_TIMER)
                .tag("topic", topic)
                .tag("event_type", eventType)
                .description("이벤트 발행 소요 시간")
                .register(meterRegistry)
        );
        timer.record(Duration.ofMillis(durationMs));
    }
    
    /**
     * 메트릭 키 생성
     */
    private String createKey(String topic, String eventType) {
        return topic + ":" + eventType;
    }
    
    /**
     * 발행 성공률 계산
     */
    public double getPublishSuccessRate(String topic, String eventType) {
        try {
            String key = createKey(topic, eventType);
            Counter successCounter = publishSuccessCounters.get(key);
            Counter errorCounter = publishErrorCounters.get(key);
            
            if (successCounter == null) {
                return 0.0;
            }
            
            double successCount = successCounter.count();
            double errorCount = errorCounter != null ? errorCounter.count() : 0.0;
            double total = successCount + errorCount;
            
            return total > 0 ? (successCount / total) * 100.0 : 0.0;
            
        } catch (Exception e) {
            log.warn("성공률 계산 실패: topic={}, eventType={}", topic, eventType, e);
            return 0.0;
        }
    }
    
    /**
     * 평균 발행 시간 조회
     */
    public double getAveragePublishTime(String topic, String eventType) {
        try {
            String key = createKey(topic, eventType);
            Timer timer = publishTimers.get(key);
            return timer != null ? timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS) : 0.0;
            
        } catch (Exception e) {
            log.warn("평균 발행 시간 조회 실패: topic={}, eventType={}", topic, eventType, e);
            return 0.0;
        }
    }
    
    /**
     * 메트릭 요약 로깅
     */
    public void logMetricsSummary(String topic) {
        try {
            log.info("=== 이벤트 발행 메트릭 요약: topic={} ===", topic);
            
            publishSuccessCounters.forEach((key, counter) -> {
                if (key.startsWith(topic + ":")) {
                    String eventType = key.substring(topic.length() + 1);
                    double successRate = getPublishSuccessRate(topic, eventType);
                    double avgTime = getAveragePublishTime(topic, eventType);
                    
                    log.info("EventType: {}, SuccessCount: {}, SuccessRate: {:.2f}%, AvgTime: {:.2f}ms",
                        eventType,
                        (long) counter.count(),
                        successRate,
                        avgTime
                    );
                }
            });
            
        } catch (Exception e) {
            log.warn("메트릭 요약 로깅 실패: topic={}", topic, e);
        }
    }
}