package com.hightraffic.ecommerce.common.adapter.out.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 도메인 이벤트 직렬화기
 * 
 * Kafka에서 사용할 도메인 이벤트 직렬화를 담당합니다.
 */
public class EventSerializer implements Serializer<DomainEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(EventSerializer.class);
    
    private ObjectMapper objectMapper;
    
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        log.info("EventSerializer 설정 완료");
    }
    
    @Override
    public byte[] serialize(String topic, DomainEvent event) {
        if (event == null) {
            return null;
        }
        
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(event);
            
            log.debug("이벤트 직렬화 성공: eventId={}, eventType={}, size={}bytes", 
                event.getEventId(), event.getEventType(), serialized.length);
            
            return serialized;
            
        } catch (Exception e) {
            String errorMsg = String.format(
                "이벤트 직렬화 실패: eventId=%s, eventType=%s", 
                event.getEventId(), event.getEventType()
            );
            log.error(errorMsg, e);
            throw new SerializationException(errorMsg, e);
        }
    }
    
    @Override
    public void close() {
        // 정리할 리소스 없음
    }
}