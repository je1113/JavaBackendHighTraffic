package com.hightraffic.ecommerce.order.adapter.out.event;

import com.hightraffic.ecommerce.common.adapter.out.kafka.KafkaEventPublisher;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import com.hightraffic.ecommerce.common.event.order.*;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 주문 이벤트 발행 어댑터
 * 
 * 주문 도메인 이벤트를 Kafka로 발행하는 아웃바운드 어댑터입니다.
 * 헥사고날 아키텍처의 Port 인터페이스를 구현합니다.
 */
@Component
public class OrderEventPublishingAdapter implements PublishEventPort {
    
    private static final Logger log = LoggerFactory.getLogger(OrderEventPublishingAdapter.class);
    
    private final KafkaEventPublisher eventPublisher;
    private final EventTopicMapper topicMapper;
    
    // 토픽 설정
    @Value("${app.kafka.topics.order-created}")
    private String orderCreatedTopic;
    
    @Value("${app.kafka.topics.order-confirmed}")
    private String orderConfirmedTopic;
    
    @Value("${app.kafka.topics.order-paid}")
    private String orderPaidTopic;
    
    @Value("${app.kafka.topics.order-completed}")
    private String orderCompletedTopic;
    
    @Value("${app.kafka.topics.order-cancelled}")
    private String orderCancelledTopic;
    
    @Value("${spring.application.name:order-service}")
    private String serviceName;
    
    public OrderEventPublishingAdapter(KafkaEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.topicMapper = new EventTopicMapper();
    }
    
    @Override
    public void publishOrderCreatedEvent(OrderCreatedEvent event) {
        enrichAndPublish(event, orderCreatedTopic);
    }
    
    @Override
    public void publishOrderConfirmedEvent(OrderConfirmedEvent event) {
        enrichAndPublish(event, orderConfirmedTopic);
    }
    
    @Override
    public void publishOrderPaidEvent(OrderPaidEvent event) {
        enrichAndPublish(event, orderPaidTopic);
    }
    
    @Override
    public void publishOrderCompletedEvent(OrderCompletedEvent event) {
        enrichAndPublish(event, orderCompletedTopic);
    }
    
    @Override
    public void publishOrderCancelledEvent(OrderCancelledEvent event) {
        enrichAndPublish(event, orderCancelledTopic);
    }
    
    /**
     * 이벤트 발행 (동기)
     */
    private void enrichAndPublish(DomainEvent event, String topic) {
        try {
            // 이벤트 메타데이터 보강
            enrichEvent(event);
            
            // 비동기 발행 후 결과 대기
            KafkaEventPublisher.EventPublishResult result = 
                eventPublisher.publish(event, topic).join();
            
            if (result.isSuccess()) {
                log.info("이벤트 발행 성공: eventType={}, eventId={}, orderId={}, topic={}, offset={}", 
                    event.getEventType(), event.getEventId(), event.getAggregateId(), 
                    topic, result.metadata().offset());
            } else {
                log.error("이벤트 발행 실패: eventType={}, eventId={}, orderId={}, error={}", 
                    event.getEventType(), event.getEventId(), event.getAggregateId(), 
                    result.errorMessage());
                throw new EventPublishException("이벤트 발행 실패: " + result.errorMessage());
            }
            
        } catch (Exception e) {
            log.error("이벤트 발행 중 오류 발생: eventType={}, orderId={}", 
                event.getEventType(), event.getAggregateId(), e);
            throw new EventPublishException("이벤트 발행 실패", e);
        }
    }
    
    /**
     * 비동기 이벤트 발행
     */
    public CompletableFuture<Void> publishAsync(DomainEvent event) {
        String topic = topicMapper.getTopicForEvent(event);
        enrichEvent(event);
        
        return eventPublisher.publish(event, topic)
            .thenAccept(result -> {
                if (result.isFailure()) {
                    log.error("비동기 이벤트 발행 실패: eventType={}, eventId={}, error={}", 
                        event.getEventType(), event.getEventId(), result.errorMessage());
                }
            });
    }
    
    /**
     * 이벤트 일괄 발행
     */
    public CompletableFuture<Void> publishBatch(List<DomainEvent> events) {
        // 이벤트 타입별로 그룹화하여 발행
        events.stream()
            .collect(java.util.stream.Collectors.groupingBy(DomainEvent::getEventType))
            .forEach((eventType, eventList) -> {
                String topic = topicMapper.getTopicForEventType(eventType);
                eventList.forEach(this::enrichEvent);
                
                eventPublisher.publishBatch(eventList, topic)
                    .thenAccept(result -> {
                        if (result.hasFailures()) {
                            log.warn("일괄 발행 중 일부 실패: eventType={}, total={}, failed={}", 
                                eventType, eventList.size(), result.failureCount());
                        }
                    });
            });
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 이벤트 메타데이터 보강
     */
    private void enrichEvent(DomainEvent event) {
        // 이벤트 ID가 없으면 생성
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        
        // 타임스탬프가 없으면 현재 시간
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        
        // 서비스 정보 추가
        event.setSourceService(serviceName);
        
        // Aggregate 타입 설정
        if (event.getAggregateType() == null) {
            event.setAggregateType("Order");
        }
    }
    
    /**
     * 이벤트 타입별 토픽 매핑
     */
    private class EventTopicMapper {
        
        public String getTopicForEvent(DomainEvent event) {
            return getTopicForEventType(event.getEventType());
        }
        
        public String getTopicForEventType(String eventType) {
            return switch (eventType) {
                case "OrderCreated" -> orderCreatedTopic;
                case "OrderConfirmed" -> orderConfirmedTopic;
                case "OrderPaid" -> orderPaidTopic;
                case "OrderCompleted" -> orderCompletedTopic;
                case "OrderCancelled" -> orderCancelledTopic;
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        }
    }
    
    /**
     * 이벤트 발행 예외
     */
    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message) {
            super(message);
        }
        
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}