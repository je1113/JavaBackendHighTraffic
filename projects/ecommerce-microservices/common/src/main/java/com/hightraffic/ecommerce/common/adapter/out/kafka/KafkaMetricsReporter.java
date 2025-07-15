package com.hightraffic.ecommerce.common.adapter.out.kafka;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kafka 메트릭 리포터
 * 
 * Kafka Producer의 메트릭을 수집하고 모니터링 시스템에 전달합니다.
 */
public class KafkaMetricsReporter implements MetricsReporter {
    
    private static final Logger log = LoggerFactory.getLogger(KafkaMetricsReporter.class);
    
    private final Map<String, KafkaMetric> metrics = new ConcurrentHashMap<>();
    
    @Override
    public void init(List<KafkaMetric> metrics) {
        for (KafkaMetric metric : metrics) {
            this.metrics.put(metric.metricName().name(), metric);
        }
        log.info("Kafka 메트릭 리포터 초기화 완료: 메트릭 수={}", metrics.size());
    }
    
    @Override
    public void metricChange(KafkaMetric metric) {
        String metricName = metric.metricName().name();
        this.metrics.put(metricName, metric);
        
        // 중요 메트릭 로깅
        if (isImportantMetric(metricName)) {
            log.debug("Kafka 메트릭 변경: {}={}", metricName, metric.metricValue());
        }
    }
    
    @Override
    public void metricRemoval(KafkaMetric metric) {
        String metricName = metric.metricName().name();
        this.metrics.remove(metricName);
    }
    
    @Override
    public void close() {
        log.info("Kafka 메트릭 리포터 종료");
        metrics.clear();
    }
    
    @Override
    public void configure(Map<String, ?> configs) {
        log.info("Kafka 메트릭 리포터 설정: {}", configs);
    }
    
    /**
     * 중요 메트릭 판별
     */
    private boolean isImportantMetric(String metricName) {
        return metricName.contains("record-send-rate") ||
               metricName.contains("record-error-rate") ||
               metricName.contains("request-latency-avg") ||
               metricName.contains("outgoing-byte-rate") ||
               metricName.contains("failed-sends");
    }
    
    /**
     * 현재 메트릭 조회
     */
    public Map<String, Double> getCurrentMetrics() {
        Map<String, Double> currentValues = new ConcurrentHashMap<>();
        
        metrics.forEach((name, metric) -> {
            Object value = metric.metricValue();
            if (value instanceof Number) {
                currentValues.put(name, ((Number) value).doubleValue());
            }
        });
        
        return currentValues;
    }
}