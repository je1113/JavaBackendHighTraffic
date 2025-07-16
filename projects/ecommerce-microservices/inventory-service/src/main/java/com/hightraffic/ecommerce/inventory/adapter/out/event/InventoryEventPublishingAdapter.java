package com.hightraffic.ecommerce.inventory.adapter.out.event;

import com.hightraffic.ecommerce.common.adapter.out.kafka.KafkaEventPublisher;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;
import com.hightraffic.ecommerce.common.event.inventory.*;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 재고 이벤트 발행 어댑터
 * 
 * 재고 도메인 이벤트를 Kafka로 발행하는 아웃바운드 어댑터입니다.
 * 헥사고날 아키텍처의 Port 인터페이스를 구현합니다.
 */
@Component
public class InventoryEventPublishingAdapter implements PublishEventPort {
    
    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublishingAdapter.class);
    
    private final KafkaEventPublisher eventPublisher;
    
    // 토픽 설정
    @Value("${app.kafka.topics.stock-reserved}")
    private String stockReservedTopic;
    
    @Value("${app.kafka.topics.stock-released}")
    private String stockReleasedTopic;
    
    @Value("${app.kafka.topics.stock-deducted}")
    private String stockDeductedTopic;
    
    @Value("${app.kafka.topics.stock-adjusted}")
    private String stockAdjustedTopic;
    
    @Value("${app.kafka.topics.low-stock-alert}")
    private String lowStockAlertTopic;
    
    @Value("${app.kafka.topics.insufficient-stock}")
    private String insufficientStockTopic;
    
    @Value("${spring.application.name:inventory-service}")
    private String serviceName;
    
    // 재시도 설정
    @Value("${app.kafka.publish.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    @Value("${app.kafka.publish.retry.delay-ms:1000}")
    private long retryDelayMs;
    
    public InventoryEventPublishingAdapter(KafkaEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    @Override
    public void publishEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            publishEvent(event);
        }
    }
    
    @Override
    public void publishEvent(DomainEvent event) {
        // 이벤트 타입에 따라 적절한 토픽으로 발행
        if (event instanceof StockReservedEvent) {
            publishStockReservedEvent((StockReservedEvent) event);
        } else if (event instanceof StockReleasedEvent) {
            publishStockReleasedEvent((StockReleasedEvent) event);
        } else if (event instanceof StockDeductedEvent) {
            publishStockDeductedEvent((StockDeductedEvent) event);
        } else if (event instanceof StockAdjustedEvent) {
            publishStockAdjustedEvent((StockAdjustedEvent) event);
        } else if (event instanceof LowStockAlertEvent) {
            publishLowStockAlertEvent((LowStockAlertEvent) event);
        } else {
            log.warn("알 수 없는 이벤트 타입: {}", event.getClass().getSimpleName());
        }
    }
    
    @Override
    public void publishStockReservedEvent(StockReservedEvent event) {
        publishWithRetry(event, stockReservedTopic);
    }
    
    @Override
    public void publishStockReleasedEvent(StockReleasedEvent event) {
        publishWithRetry(event, stockReleasedTopic);
    }
    
    @Override
    public void publishStockDeductedEvent(StockDeductedEvent event) {
        publishWithRetry(event, stockDeductedTopic);
    }
    
    @Override
    public void publishStockAdjustedEvent(StockAdjustedEvent event) {
        publishWithRetry(event, stockAdjustedTopic);
    }
    
    @Override
    public void publishLowStockAlertEvent(LowStockAlertEvent event) {
        // 낮은 재고 알림은 비동기로 처리 (실패해도 무방)
        publishAsync(event, lowStockAlertTopic);
    }
    
    /**
     * 재고 부족 이벤트 발행
     */
    public void publishInsufficientStockEvent(String orderId, String productId, 
                                            Integer requestedQuantity, Integer availableQuantity) {
        InsufficientStockEvent event = new InsufficientStockEvent(
            UUID.randomUUID().toString(),
            Instant.now(),
            productId,
            orderId,
            requestedQuantity,
            availableQuantity
        );
        
        enrichEvent(event);
        publishWithRetry(event, insufficientStockTopic);
    }
    
    /**
     * 재시도 포함 동기 발행
     */
    private void publishWithRetry(DomainEvent event, String topic) {
        enrichEvent(event);
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetryAttempts) {
            try {
                KafkaEventPublisher.EventPublishResult result = 
                    eventPublisher.publish(event, topic).join();
                
                if (result.isSuccess()) {
                    log.info("재고 이벤트 발행 성공: eventType={}, eventId={}, productId={}, attempt={}", 
                        event.getEventType(), event.getEventId(), event.getAggregateId(), attempt + 1);
                    return;
                } else {
                    throw new EventPublishException("이벤트 발행 실패: " + result.errorMessage());
                }
                
            } catch (Exception e) {
                lastException = e;
                attempt++;
                
                log.warn("재고 이벤트 발행 실패 (시도 {}/{}): eventType={}, productId={}, error={}", 
                    attempt, maxRetryAttempts, event.getEventType(), event.getAggregateId(), e.getMessage());
                
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new EventPublishException("재시도 중 인터럽트 발생", ie);
                    }
                }
            }
        }
        
        // 모든 재시도 실패
        log.error("재고 이벤트 발행 최종 실패: eventType={}, eventId={}, productId={}", 
            event.getEventType(), event.getEventId(), event.getAggregateId(), lastException);
        
        throw new EventPublishException("이벤트 발행 실패 (재시도 초과)", lastException);
    }
    
    /**
     * 비동기 발행 (중요도 낮은 이벤트)
     */
    private void publishAsync(DomainEvent event, String topic) {
        enrichEvent(event);
        
        eventPublisher.publish(event, topic)
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    log.debug("비동기 이벤트 발행 성공: eventType={}, eventId={}", 
                        event.getEventType(), event.getEventId());
                } else {
                    log.warn("비동기 이벤트 발행 실패 (무시됨): eventType={}, eventId={}, error={}", 
                        event.getEventType(), event.getEventId(), result.errorMessage());
                }
            })
            .exceptionally(throwable -> {
                log.warn("비동기 이벤트 발행 예외 (무시됨): eventType={}, eventId={}", 
                    event.getEventType(), event.getEventId(), throwable);
                return null;
            });
    }
    
    /**
     * 재고 스냅샷 이벤트 발행
     * 주기적으로 전체 재고 현황을 발행
     */
    public CompletableFuture<Void> publishInventorySnapshot(InventorySnapshotEvent snapshot) {
        enrichEvent(snapshot);
        
        return eventPublisher.publish(snapshot, "inventory-snapshot")
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    log.info("재고 스냅샷 발행 성공: snapshotId={}, productCount={}", 
                        snapshot.getEventId(), snapshot.getProductCount());
                } else {
                    log.error("재고 스냅샷 발행 실패: snapshotId={}, error={}", 
                        snapshot.getEventId(), result.errorMessage());
                }
            });
    }
    
    /**
     * 이벤트 메타데이터 보강
     */
    private void enrichEvent(DomainEvent event) {
        // DomainEvent는 불변 객체이므로 별도 설정 없이 사용
        log.debug("이벤트 메타데이터: eventId={}, eventType={}, aggregateId={}", 
            event.getEventId(), event.getEventType(), event.getAggregateId());
    }
    
    /**
     * 재고 부족 이벤트
     */
    public static class InsufficientStockEvent extends DomainEvent {
        private final String orderId;
        private final Integer requestedQuantity;
        private final Integer availableQuantity;
        
        public InsufficientStockEvent(String eventId, Instant timestamp, String productId,
                                    String orderId, Integer requestedQuantity, Integer availableQuantity) {
            super(eventId, "InsufficientStock", timestamp, 1, productId);
            this.orderId = orderId;
            this.requestedQuantity = requestedQuantity;
            this.availableQuantity = availableQuantity;
        }
        
        public String getOrderId() {
            return orderId;
        }
        
        public Integer getRequestedQuantity() {
            return requestedQuantity;
        }
        
        public Integer getAvailableQuantity() {
            return availableQuantity;
        }
    }
    
    /**
     * 재고 스냅샷 이벤트
     */
    public static class InventorySnapshotEvent extends DomainEvent {
        private final int productCount;
        private final long totalValue;
        private final Instant snapshotTime;
        
        public InventorySnapshotEvent(int productCount, long totalValue) {
            super(UUID.randomUUID().toString(), "InventorySnapshot", 
                  Instant.now(), 1, "SYSTEM");
            this.productCount = productCount;
            this.totalValue = totalValue;
            this.snapshotTime = Instant.now();
        }
        
        public int getProductCount() {
            return productCount;
        }
        
        public long getTotalValue() {
            return totalValue;
        }
        
        public Instant getSnapshotTime() {
            return snapshotTime;
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