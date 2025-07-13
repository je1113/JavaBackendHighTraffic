package com.hightraffic.ecommerce.common.event.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 주문 확정 이벤트
 * 재고 예약이 성공하고 주문이 확정되었을 때 발행되는 이벤트
 */
public class OrderConfirmedEvent extends DomainEvent {
    
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    private final String customerId;
    
    @JsonProperty("previousStatus")
    @NotBlank(message = "이전 상태는 필수입니다")
    private final String previousStatus;
    
    @JsonProperty("currentStatus")
    @NotBlank(message = "현재 상태는 필수입니다")
    private final String currentStatus;
    
    @JsonProperty("confirmedAmount")
    @NotNull(message = "확정 금액은 필수입니다")
    @Positive(message = "확정 금액은 0보다 커야 합니다")
    private final BigDecimal confirmedAmount;
    
    @JsonProperty("stockReservationId")
    @NotBlank(message = "재고 예약 ID는 필수입니다")
    private final String stockReservationId;
    
    @JsonProperty("estimatedDeliveryDate")
    private final Instant estimatedDeliveryDate;
    
    @JsonProperty("notes")
    private final String notes;
    
    public OrderConfirmedEvent(String orderId, String customerId, 
                             String previousStatus, String currentStatus,
                             BigDecimal confirmedAmount, String stockReservationId,
                             Instant estimatedDeliveryDate, String notes) {
        super(orderId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.confirmedAmount = confirmedAmount;
        this.stockReservationId = stockReservationId;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.notes = notes;
    }
    
    @JsonCreator
    public OrderConfirmedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("customerId") String customerId,
                             @JsonProperty("previousStatus") String previousStatus,
                             @JsonProperty("currentStatus") String currentStatus,
                             @JsonProperty("confirmedAmount") BigDecimal confirmedAmount,
                             @JsonProperty("stockReservationId") String stockReservationId,
                             @JsonProperty("estimatedDeliveryDate") Instant estimatedDeliveryDate,
                             @JsonProperty("notes") String notes) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.confirmedAmount = confirmedAmount;
        this.stockReservationId = stockReservationId;
        this.estimatedDeliveryDate = estimatedDeliveryDate;
        this.notes = notes;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public String getCurrentStatus() {
        return currentStatus;
    }
    
    public BigDecimal getConfirmedAmount() {
        return confirmedAmount;
    }
    
    public String getStockReservationId() {
        return stockReservationId;
    }
    
    public Instant getEstimatedDeliveryDate() {
        return estimatedDeliveryDate;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public String getOrderId() {
        return getAggregateId();
    }
    
    /**
     * 상태 전이가 유효한지 검증
     */
    public boolean isValidStatusTransition() {
        return "PENDING".equals(previousStatus) && "CONFIRMED".equals(currentStatus);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        OrderConfirmedEvent that = (OrderConfirmedEvent) obj;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(previousStatus, that.previousStatus) &&
               Objects.equals(currentStatus, that.currentStatus) &&
               Objects.equals(confirmedAmount, that.confirmedAmount) &&
               Objects.equals(stockReservationId, that.stockReservationId) &&
               Objects.equals(estimatedDeliveryDate, that.estimatedDeliveryDate) &&
               Objects.equals(notes, that.notes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customerId, previousStatus, currentStatus, 
                          confirmedAmount, stockReservationId, estimatedDeliveryDate, notes);
    }
    
    @Override
    public String toString() {
        return String.format("OrderConfirmedEvent{orderId='%s', customerId='%s', %s->%s, amount=%s, reservationId='%s'}", 
                getOrderId(), customerId, previousStatus, currentStatus, confirmedAmount, stockReservationId);
    }
}