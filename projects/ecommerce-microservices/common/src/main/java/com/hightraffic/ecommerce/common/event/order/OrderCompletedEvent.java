package com.hightraffic.ecommerce.common.event.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주문 완료 이벤트
 * 모든 배송이 완료되고 주문이 최종 완료되었을 때 발행되는 이벤트
 */
public class OrderCompletedEvent extends DomainEvent {
    
    @JsonProperty("customerId")
    @NotBlank(message = "고객 ID는 필수입니다")
    private final String customerId;
    
    @JsonProperty("previousStatus")
    @NotBlank(message = "이전 상태는 필수입니다")
    private final String previousStatus;
    
    @JsonProperty("completedAmount")
    @NotNull(message = "완료 금액은 필수입니다")
    @Positive(message = "완료 금액은 0보다 커야 합니다")
    private final BigDecimal completedAmount;
    
    @JsonProperty("deliveryCompletions")
    @NotEmpty(message = "배송 완료 정보는 최소 1개 이상이어야 합니다")
    @Valid
    private final List<DeliveryCompletion> deliveryCompletions;
    
    @JsonProperty("actualDeliveryDate")
    @NotNull(message = "실제 배송 완료일은 필수입니다")
    private final Instant actualDeliveryDate;
    
    @JsonProperty("customerSatisfactionScore")
    private final Integer customerSatisfactionScore; // 1-5 점수
    
    @JsonProperty("loyaltyPointsEarned")
    private final Integer loyaltyPointsEarned;
    
    @JsonProperty("completionNotes")
    private final String completionNotes;
    
    public OrderCompletedEvent(String orderId, String customerId, 
                             String previousStatus, BigDecimal completedAmount,
                             List<DeliveryCompletion> deliveryCompletions,
                             Instant actualDeliveryDate, Integer customerSatisfactionScore,
                             Integer loyaltyPointsEarned, String completionNotes) {
        super(orderId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.completedAmount = completedAmount;
        this.deliveryCompletions = deliveryCompletions != null ? List.copyOf(deliveryCompletions) : List.of();
        this.actualDeliveryDate = actualDeliveryDate;
        this.customerSatisfactionScore = customerSatisfactionScore;
        this.loyaltyPointsEarned = loyaltyPointsEarned;
        this.completionNotes = completionNotes;
    }
    
    @JsonCreator
    public OrderCompletedEvent(@JsonProperty("eventId") String eventId,
                             @JsonProperty("eventType") String eventType,
                             @JsonProperty("timestamp") Instant timestamp,
                             @JsonProperty("version") int version,
                             @JsonProperty("aggregateId") String aggregateId,
                             @JsonProperty("customerId") String customerId,
                             @JsonProperty("previousStatus") String previousStatus,
                             @JsonProperty("completedAmount") BigDecimal completedAmount,
                             @JsonProperty("deliveryCompletions") List<DeliveryCompletion> deliveryCompletions,
                             @JsonProperty("actualDeliveryDate") Instant actualDeliveryDate,
                             @JsonProperty("customerSatisfactionScore") Integer customerSatisfactionScore,
                             @JsonProperty("loyaltyPointsEarned") Integer loyaltyPointsEarned,
                             @JsonProperty("completionNotes") String completionNotes) {
        super(eventId, eventType, timestamp, version, aggregateId);
        this.customerId = customerId;
        this.previousStatus = previousStatus;
        this.completedAmount = completedAmount;
        this.deliveryCompletions = deliveryCompletions != null ? List.copyOf(deliveryCompletions) : List.of();
        this.actualDeliveryDate = actualDeliveryDate;
        this.customerSatisfactionScore = customerSatisfactionScore;
        this.loyaltyPointsEarned = loyaltyPointsEarned;
        this.completionNotes = completionNotes;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public String getPreviousStatus() {
        return previousStatus;
    }
    
    public BigDecimal getCompletedAmount() {
        return completedAmount;
    }
    
    public List<DeliveryCompletion> getDeliveryCompletions() {
        return deliveryCompletions;
    }
    
    public Instant getActualDeliveryDate() {
        return actualDeliveryDate;
    }
    
    public Integer getCustomerSatisfactionScore() {
        return customerSatisfactionScore;
    }
    
    public Integer getLoyaltyPointsEarned() {
        return loyaltyPointsEarned;
    }
    
    public String getCompletionNotes() {
        return completionNotes;
    }
    
    public String getOrderId() {
        return getAggregateId();
    }
    
    /**
     * 상태 전이가 유효한지 검증
     */
    public boolean isValidStatusTransition() {
        return List.of("CONFIRMED", "SHIPPED", "DELIVERED").contains(previousStatus);
    }
    
    /**
     * 고객 만족도가 설정되어 있는지 확인
     */
    public boolean hasCustomerSatisfaction() {
        return customerSatisfactionScore != null && 
               customerSatisfactionScore >= 1 && customerSatisfactionScore <= 5;
    }
    
    /**
     * 적립금이 발생했는지 확인
     */
    public boolean hasLoyaltyPoints() {
        return loyaltyPointsEarned != null && loyaltyPointsEarned > 0;
    }
    
    /**
     * 모든 배송이 완료되었는지 확인
     */
    public boolean isAllItemsDelivered() {
        return deliveryCompletions.stream()
                .allMatch(delivery -> "COMPLETED".equals(delivery.getDeliveryStatus()));
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        OrderCompletedEvent that = (OrderCompletedEvent) obj;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(previousStatus, that.previousStatus) &&
               Objects.equals(completedAmount, that.completedAmount) &&
               Objects.equals(deliveryCompletions, that.deliveryCompletions) &&
               Objects.equals(actualDeliveryDate, that.actualDeliveryDate) &&
               Objects.equals(customerSatisfactionScore, that.customerSatisfactionScore) &&
               Objects.equals(loyaltyPointsEarned, that.loyaltyPointsEarned) &&
               Objects.equals(completionNotes, that.completionNotes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), customerId, previousStatus, completedAmount, 
                          deliveryCompletions, actualDeliveryDate, customerSatisfactionScore, 
                          loyaltyPointsEarned, completionNotes);
    }
    
    @Override
    public String toString() {
        return String.format("OrderCompletedEvent{orderId='%s', customerId='%s', %s->COMPLETED, amount=%s, deliveries=%d, satisfaction=%s}", 
                getOrderId(), customerId, previousStatus, completedAmount, 
                deliveryCompletions.size(), customerSatisfactionScore);
    }
    
    /**
     * 배송 완료 정보 데이터 클래스
     */
    public static class DeliveryCompletion {
        
        @JsonProperty("deliveryId")
        @NotBlank(message = "배송 ID는 필수입니다")
        private final String deliveryId;
        
        @JsonProperty("productId")
        @NotBlank(message = "상품 ID는 필수입니다")
        private final String productId;
        
        @JsonProperty("productName")
        @NotBlank(message = "상품명은 필수입니다")
        private final String productName;
        
        @JsonProperty("quantity")
        @Positive(message = "수량은 0보다 커야 합니다")
        private final int quantity;
        
        @JsonProperty("deliveryStatus")
        @NotBlank(message = "배송 상태는 필수입니다")
        private final String deliveryStatus; // COMPLETED, FAILED, PARTIAL
        
        @JsonProperty("deliveryDate")
        @NotNull(message = "배송 완료일은 필수입니다")
        private final Instant deliveryDate;
        
        @JsonProperty("deliveryAddress")
        @NotBlank(message = "배송 주소는 필수입니다")
        private final String deliveryAddress;
        
        @JsonProperty("recipientName")
        @NotBlank(message = "수령인명은 필수입니다")
        private final String recipientName;
        
        @JsonProperty("deliveryNotes")
        private final String deliveryNotes;
        
        @JsonCreator
        public DeliveryCompletion(@JsonProperty("deliveryId") String deliveryId,
                                @JsonProperty("productId") String productId,
                                @JsonProperty("productName") String productName,
                                @JsonProperty("quantity") int quantity,
                                @JsonProperty("deliveryStatus") String deliveryStatus,
                                @JsonProperty("deliveryDate") Instant deliveryDate,
                                @JsonProperty("deliveryAddress") String deliveryAddress,
                                @JsonProperty("recipientName") String recipientName,
                                @JsonProperty("deliveryNotes") String deliveryNotes) {
            this.deliveryId = deliveryId;
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.deliveryStatus = deliveryStatus;
            this.deliveryDate = deliveryDate;
            this.deliveryAddress = deliveryAddress;
            this.recipientName = recipientName;
            this.deliveryNotes = deliveryNotes;
        }
        
        public String getDeliveryId() {
            return deliveryId;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public int getQuantity() {
            return quantity;
        }
        
        public String getDeliveryStatus() {
            return deliveryStatus;
        }
        
        public Instant getDeliveryDate() {
            return deliveryDate;
        }
        
        public String getDeliveryAddress() {
            return deliveryAddress;
        }
        
        public String getRecipientName() {
            return recipientName;
        }
        
        public String getDeliveryNotes() {
            return deliveryNotes;
        }
        
        public boolean isSuccessfulDelivery() {
            return "COMPLETED".equals(deliveryStatus);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            DeliveryCompletion that = (DeliveryCompletion) obj;
            return quantity == that.quantity &&
                   Objects.equals(deliveryId, that.deliveryId) &&
                   Objects.equals(productId, that.productId) &&
                   Objects.equals(productName, that.productName) &&
                   Objects.equals(deliveryStatus, that.deliveryStatus) &&
                   Objects.equals(deliveryDate, that.deliveryDate) &&
                   Objects.equals(deliveryAddress, that.deliveryAddress) &&
                   Objects.equals(recipientName, that.recipientName) &&
                   Objects.equals(deliveryNotes, that.deliveryNotes);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(deliveryId, productId, productName, quantity, deliveryStatus, 
                              deliveryDate, deliveryAddress, recipientName, deliveryNotes);
        }
        
        @Override
        public String toString() {
            return String.format("DeliveryCompletion{deliveryId='%s', productId='%s', status='%s', quantity=%d, date=%s}", 
                    deliveryId, productId, deliveryStatus, quantity, deliveryDate);
        }
    }
}