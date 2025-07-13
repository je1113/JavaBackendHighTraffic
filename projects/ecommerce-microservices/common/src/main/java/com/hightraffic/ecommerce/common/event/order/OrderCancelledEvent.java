package com.hightraffic.ecommerce.common.event.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주문 취소 이벤트
 * 고객이나 시스템에 의해 주문이 취소되었을 때 발행되는 이벤트
 */
public class OrderCancelledEvent extends DomainEvent {
    
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    private final String customerId;
    
    @JsonProperty("previousStatus")
    @NotBlank(message = "이전 상태는 필수입니다")
    private final String previousStatus;
    
    @JsonProperty("cancelReason")
    @NotBlank(message = "취소 사유는 필수입니다")
    private final String cancelReason;
    
    @JsonProperty("cancelReasonCode")
    @NotBlank(message = "취소 사유 코드는 필수입니다")
    private final String cancelReasonCode;
    
    @JsonProperty("cancelledBy")
    @NotBlank(message = "취소 주체는 필수입니다")
    private final String cancelledBy;
    
    @JsonProperty("cancelledByType")
    @NotBlank(message = "취소 주체 타입은 필수입니다")
    private final String cancelledByType; // CUSTOMER, SYSTEM, ADMIN
    
    @JsonProperty("refundAmount")
    @NotNull(message = "환불 금액은 필수입니다")
    private final BigDecimal refundAmount;
    
    @JsonProperty("compensationActions")
    private final List<CompensationAction> compensationActions;
    
    @JsonProperty("cancellationNotes")
    private final String cancellationNotes;
    
    public OrderCancelledEvent(String orderId, String customerId, 
                             String previousStatus, String cancelReason, String cancelReasonCode,
                             String cancelledBy, String cancelledByType,
                             BigDecimal refundAmount, List<CompensationAction> compensationActions,
                             String cancellationNotes) {
        super(orderId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.cancelReason = cancelReason;
        this.cancelReasonCode = cancelReasonCode;
        this.cancelledBy = cancelledBy;
        this.cancelledByType = cancelledByType;
        this.refundAmount = refundAmount;
        this.compensationActions = compensationActions != null ? List.copyOf(compensationActions) : List.of();
        this.cancellationNotes = cancellationNotes;
    }
    
    @JsonCreator
    public OrderCancelledEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("customerId") String customerId,
                             @JsonProperty("previousStatus") String previousStatus,
                             @JsonProperty("cancelReason") String cancelReason,
                             @JsonProperty("cancelReasonCode") String cancelReasonCode,
                             @JsonProperty("cancelledBy") String cancelledBy,
                             @JsonProperty("cancelledByType") String cancelledByType,
                             @JsonProperty("refundAmount") BigDecimal refundAmount,
                             @JsonProperty("compensationActions") List<CompensationAction> compensationActions,
                             @JsonProperty("cancellationNotes") String cancellationNotes) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.cancelReason = cancelReason;
        this.cancelReasonCode = cancelReasonCode;
        this.cancelledBy = cancelledBy;
        this.cancelledByType = cancelledByType;
        this.refundAmount = refundAmount;
        this.compensationActions = compensationActions != null ? List.copyOf(compensationActions) : List.of();
        this.cancellationNotes = cancellationNotes;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public String getCancelReason() {
        return cancelReason;
    }
    
    public String getCancelReasonCode() {
        return cancelReasonCode;
    }
    
    public String getCancelledBy() {
        return cancelledBy;
    }
    
    public String getCancelledByType() {
        return cancelledByType;
    }
    
    public BigDecimal getRefundAmount() {
        return refundAmount;
    }
    
    public List<CompensationAction> getCompensationActions() {
        return compensationActions;
    }
    
    public String getCancellationNotes() {
        return cancellationNotes;
    }
    
    public String getOrderId() {
        return getAggregateId();
    }
    
    /**
     * 고객에 의한 취소인지 확인
     */
    public boolean isCancelledByCustomer() {
        return "CUSTOMER".equals(cancelledByType);
    }
    
    /**
     * 시스템에 의한 취소인지 확인
     */
    public boolean isCancelledBySystem() {
        return "SYSTEM".equals(cancelledByType);
    }
    
    /**
     * 환불이 필요한지 확인
     */
    public boolean requiresRefund() {
        return refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        OrderCancelledEvent that = (OrderCancelledEvent) obj;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(previousStatus, that.previousStatus) &&
               Objects.equals(cancelReason, that.cancelReason) &&
               Objects.equals(cancelReasonCode, that.cancelReasonCode) &&
               Objects.equals(cancelledBy, that.cancelledBy) &&
               Objects.equals(cancelledByType, that.cancelledByType) &&
               Objects.equals(refundAmount, that.refundAmount) &&
               Objects.equals(compensationActions, that.compensationActions) &&
               Objects.equals(cancellationNotes, that.cancellationNotes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customerId, previousStatus, cancelReason, 
                          cancelReasonCode, cancelledBy, cancelledByType, refundAmount, 
                          compensationActions, cancellationNotes);
    }
    
    @Override
    public String toString() {
        return String.format("OrderCancelledEvent{orderId='%s', customerId='%s', reason='%s', cancelledBy='%s(%s)', refund=%s}", 
                getOrderId(), customerId, cancelReason, cancelledBy, cancelledByType, refundAmount);
    }
    
    /**
     * 보상 액션 데이터 클래스
     */
    public static class CompensationAction {
        
        @JsonProperty("actionType")
        @NotBlank(message = "액션 타입은 필수입니다")
        private final String actionType; // STOCK_RESTORE, PAYMENT_REFUND, NOTIFICATION_SEND
        
        @JsonProperty("targetService")
        @NotBlank(message = "대상 서비스는 필수입니다")
        private final String targetService;
        
        @JsonProperty("actionData")
        private final String actionData;
        
        @JsonProperty("priority")
        private final int priority;
        
        @JsonCreator
        public CompensationAction(@JsonProperty("actionType") String actionType,
                                @JsonProperty("targetService") String targetService,
                                @JsonProperty("actionData") String actionData,
                                @JsonProperty("priority") int priority) {
            this.actionType = actionType;
            this.targetService = targetService;
            this.actionData = actionData;
            this.priority = priority;
        }
        
        public String getActionType() {
            return actionType;
        }
        
        public String getTargetService() {
            return targetService;
        }
        
        public String getActionData() {
            return actionData;
        }
        
        public int getPriority() {
            return priority;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CompensationAction that = (CompensationAction) obj;
            return priority == that.priority &&
                   Objects.equals(actionType, that.actionType) &&
                   Objects.equals(targetService, that.targetService) &&
                   Objects.equals(actionData, that.actionData);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(actionType, targetService, actionData, priority);
        }
        
        @Override
        public String toString() {
            return String.format("CompensationAction{type='%s', service='%s', priority=%d}", 
                    actionType, targetService, priority);
        }
    }
}