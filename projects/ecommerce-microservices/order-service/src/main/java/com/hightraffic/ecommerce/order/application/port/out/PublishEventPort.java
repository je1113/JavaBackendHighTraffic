package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import java.util.List;

/**
 * 이벤트 발행 Outbound Port
 * 
 * 메시징 어댑터에서 구현해야 하는 이벤트 발행 인터페이스
 * 도메인 이벤트를 외부 시스템에 전파하는 책임
 */
public interface PublishEventPort {
    
    /**
     * 단일 도메인 이벤트 발행
     * 
     * @param event 발행할 도메인 이벤트
     */
    void publishEvent(DomainEvent event);
    
    /**
     * 다수의 도메인 이벤트 일괄 발행
     * 
     * @param events 발행할 도메인 이벤트 목록
     */
    void publishEvents(List<DomainEvent> events);
}