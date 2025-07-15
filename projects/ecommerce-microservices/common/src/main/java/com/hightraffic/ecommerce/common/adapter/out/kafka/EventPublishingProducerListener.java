package com.hightraffic.ecommerce.common.adapter.out.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;

/**
 * Kafka Producer 리스너
 * 
 * 이벤트 발행 과정을 모니터링하고 로깅합니다.
 */
public class EventPublishingProducerListener implements ProducerListener<String, String> {
    
    private static final Logger log = LoggerFactory.getLogger(EventPublishingProducerListener.class);
    
    @Override
    public void onSuccess(ProducerRecord<String, String> producerRecord, RecordMetadata recordMetadata) {
        log.trace("이벤트 발행 성공 콜백: topic={}, partition={}, offset={}, key={}",
            recordMetadata.topic(), 
            recordMetadata.partition(), 
            recordMetadata.offset(),
            producerRecord.key());
    }
    
    @Override
    public void onError(ProducerRecord<String, String> producerRecord, RecordMetadata recordMetadata, Exception exception) {
        log.error("이벤트 발행 실패 콜백: topic={}, key={}, error={}",
            producerRecord.topic(),
            producerRecord.key(),
            exception.getMessage());
    }
}