package com.hightraffic.ecommerce.common.event.payment;

import com.hightraffic.ecommerce.common.event.base.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 결제 완료 이벤트
 * 
 * Payment Service에서 발행하고 Order Service에서 소비하는 이벤트
 * 결제가 성공적으로 완료되었음을 알림
 */
public class PaymentCompletedEvent extends DomainEvent {
    
    private final String paymentId;
    private final String orderId;
    private final String customerId;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    private final String transactionId;
    private final LocalDateTime paidAt;
    
    public PaymentCompletedEvent(String paymentId, String orderId, String customerId,
                               BigDecimal amount, String currency, String paymentMethod,
                               String transactionId, LocalDateTime paidAt) {
        super();
        this.paymentId = Objects.requireNonNull(paymentId, "Payment ID cannot be null");
        this.orderId = Objects.requireNonNull(orderId, "Order ID cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
        this.paymentMethod = Objects.requireNonNull(paymentMethod, "Payment method cannot be null");
        this.transactionId = Objects.requireNonNull(transactionId, "Transaction ID cannot be null");
        this.paidAt = Objects.requireNonNull(paidAt, "Paid at cannot be null");
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public BigDecimal getAmount() {
        return amount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getPaymentMethod() {
        return paymentMethod;
    }
    
    public String getTransactionId() {
        return transactionId;
    }
    
    public LocalDateTime getPaidAt() {
        return paidAt;
    }
    
    @Override
    public String toString() {
        return String.format("PaymentCompletedEvent{paymentId='%s', orderId='%s', amount=%s %s, transactionId='%s'}",
            paymentId, orderId, amount, currency, transactionId);
    }
}