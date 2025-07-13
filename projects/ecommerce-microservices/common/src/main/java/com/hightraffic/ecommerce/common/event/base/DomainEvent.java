package com.hightraffic.ecommerce.common.event.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/**
 * 도메인 이벤트 기본 클래스
 * 모든 도메인 이벤트는 이 클래스를 상속받아 구현
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public abstract class DomainEvent {
    
    @JsonProperty("eventId")
    private final String eventId;
    
    @JsonProperty("eventType")
    private final String eventType;
    
    @JsonProperty("timestamp")
    private final Instant timestamp;
    
    @JsonProperty("version")
    private final int version;
    
    @JsonProperty("aggregateId")
    private final String aggregateId;
    
    protected DomainEvent(String aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = this.getClass().getSimpleName();
        this.timestamp = Instant.now();
        this.version = 1;
        this.aggregateId = aggregateId;
    }
    
    protected DomainEvent(String eventId, String eventType, Instant timestamp, 
                         int version, String aggregateId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.version = version;
        this.aggregateId = aggregateId;
    }
    
    public String getEventId() {
        return eventId;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public int getVersion() {
        return version;
    }
    
    public String getAggregateId() {
        return aggregateId;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DomainEvent that = (DomainEvent) obj;
        return eventId.equals(that.eventId);
    }
    
    @Override
    public int hashCode() {
        return eventId.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("%s{eventId='%s', aggregateId='%s', timestamp=%s}", 
                eventType, eventId, aggregateId, timestamp);
    }
}