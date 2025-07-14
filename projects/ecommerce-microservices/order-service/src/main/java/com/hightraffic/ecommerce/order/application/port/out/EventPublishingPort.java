package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 이벤트 발행 확장 Outbound Port
 * 
 * 메시징 어댑터에서 구현해야 하는 고급 이벤트 발행 인터페이스
 * PublishEventPort를 확장하여 비동기 처리, 재시도, 모니터링 기능 추가
 */
public interface EventPublishingPort extends PublishEventPort {
    
    /**
     * 비동기 이벤트 발행
     * 
     * @param event 발행할 도메인 이벤트
     * @return 발행 완료 Future
     */
    CompletableFuture<Void> publishEventAsync(DomainEvent event);
    
    /**
     * 트랜잭션 이벤트 발행
     * 트랜잭션 커밋 후 발행을 보장
     * 
     * @param event 발행할 도메인 이벤트
     */
    void publishEventAfterCommit(DomainEvent event);
    
    /**
     * 지연된 이벤트 발행
     * 
     * @param event 발행할 도메인 이벤트
     * @param delayMillis 지연 시간 (밀리초)
     */
    void publishEventWithDelay(DomainEvent event, long delayMillis);
    
    /**
     * 이벤트 발행 재시도
     * 실패한 이벤트를 재시도 정책에 따라 다시 발행
     * 
     * @param event 재발행할 도메인 이벤트
     * @param maxRetries 최대 재시도 횟수
     */
    void publishEventWithRetry(DomainEvent event, int maxRetries);
    
    /**
     * 이벤트 발행 상태 확인
     * 
     * @param eventId 이벤트 ID
     * @return 발행 성공 여부
     */
    boolean isEventPublished(String eventId);
    
    /**
     * 이벤트 발행 통계
     * 
     * @return 발행 성공/실패 통계 정보
     */
    EventPublishingStatistics getPublishingStatistics();
    
    /**
     * 이벤트 발행 통계 정보
     */
    class EventPublishingStatistics {
        private final long totalPublished;
        private final long successCount;
        private final long failureCount;
        private final long pendingCount;
        private final double averagePublishTimeMs;
        
        public EventPublishingStatistics(long totalPublished, long successCount, 
                                       long failureCount, long pendingCount, 
                                       double averagePublishTimeMs) {
            this.totalPublished = totalPublished;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.pendingCount = pendingCount;
            this.averagePublishTimeMs = averagePublishTimeMs;
        }
        
        // Getters
        public long getTotalPublished() { return totalPublished; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public long getPendingCount() { return pendingCount; }
        public double getAveragePublishTimeMs() { return averagePublishTimeMs; }
    }
}