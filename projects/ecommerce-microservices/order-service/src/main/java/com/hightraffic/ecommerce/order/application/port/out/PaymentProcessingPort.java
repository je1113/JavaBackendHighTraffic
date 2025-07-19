package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 결제 처리 Outbound Port
 * 
 * Payment Service 또는 외부 결제 게이트웨이와의 통신을 담당하는 인터페이스
 * 결제 요청, 취소, 환불 등의 기능 제공
 */
public interface PaymentProcessingPort {
    
    /**
     * 결제 처리 요청
     * 
     * @param request 결제 요청 정보
     * @return 결제 결과
     */
    PaymentResult processPayment(PaymentRequest request);
    
    /**
     * 비동기 결제 처리 요청
     * 
     * @param request 결제 요청 정보
     * @return 결제 결과 Future
     */
    CompletableFuture<PaymentResult> processPaymentAsync(PaymentRequest request);
    
    /**
     * 결제 취소
     * 
     * @param paymentId 결제 ID
     * @param reason 취소 사유
     * @return 취소 결과
     */
    CancellationResult cancelPayment(String paymentId, String reason);
    
    /**
     * 부분 환불
     * 
     * @param paymentId 결제 ID
     * @param refundAmount 환불 금액
     * @param reason 환불 사유
     * @return 환불 결과
     */
    RefundResult refundPayment(String paymentId, Money refundAmount, String reason);
    
    /**
     * 결제 상태 조회
     * 
     * @param paymentId 결제 ID
     * @return 결제 상태
     */
    PaymentStatus getPaymentStatus(String paymentId);
    
    /**
     * 결제 가능 여부 확인
     * 
     * @param customerId 고객 ID
     * @param amount 결제 금액
     * @return 결제 가능 여부
     */
    boolean canProcessPayment(CustomerId customerId, Money amount);
    
    /**
     * 결제 요청 정보
     */
    class PaymentRequest {
        private final OrderId orderId;
        private final CustomerId customerId;
        private final Money amount;
        private final String paymentMethod;
        private final String description;
        private final PaymentDetails paymentDetails;
        
        public PaymentRequest(OrderId orderId, CustomerId customerId, Money amount,
                            String paymentMethod, String description, PaymentDetails paymentDetails) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
            this.description = description;
            this.paymentDetails = paymentDetails;
        }
        
        // Getters
        public OrderId getOrderId() { return orderId; }
        public CustomerId getCustomerId() { return customerId; }
        public Money getAmount() { return amount; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getDescription() { return description; }
        public PaymentDetails getPaymentDetails() { return paymentDetails; }
    }
    
    /**
     * 결제 상세 정보 (카드 정보 등)
     */
    abstract class PaymentDetails {
        // 추상 클래스로 변경, 구체적인 구현은 어댑터에서
    }
    
    /**
     * 결제 결과
     */
    class PaymentResult {
        private final String paymentId;
        private final boolean success;
        private final PaymentStatus status;
        private final String failureReason;
        private final LocalDateTime processedAt;
        private final String transactionId;
        
        // 성공 생성자
        public PaymentResult(String paymentId, String transactionId) {
            this.paymentId = paymentId;
            this.success = true;
            this.status = PaymentStatus.COMPLETED;
            this.failureReason = null;
            this.processedAt = LocalDateTime.now();
            this.transactionId = transactionId;
        }
        
        // 실패 생성자
        public PaymentResult(String paymentId, PaymentStatus status, String failureReason) {
            this.paymentId = paymentId;
            this.success = false;
            this.status = status;
            this.failureReason = failureReason;
            this.processedAt = LocalDateTime.now();
            this.transactionId = null;
        }
        
        // Getters
        public String getPaymentId() { return paymentId; }
        public boolean isSuccess() { return success; }
        public PaymentStatus getStatus() { return status; }
        public String getFailureReason() { return failureReason; }
        public LocalDateTime getProcessedAt() { return processedAt; }
        public String getTransactionId() { return transactionId; }
    }
    
    /**
     * 결제 취소 결과
     */
    class CancellationResult {
        private final String cancellationId;
        private final boolean success;
        private final String failureReason;
        private final LocalDateTime cancelledAt;
        
        public CancellationResult(String cancellationId, boolean success, String failureReason) {
            this.cancellationId = cancellationId;
            this.success = success;
            this.failureReason = failureReason;
            this.cancelledAt = LocalDateTime.now();
        }
        
        // Getters
        public String getCancellationId() { return cancellationId; }
        public boolean isSuccess() { return success; }
        public String getFailureReason() { return failureReason; }
        public LocalDateTime getCancelledAt() { return cancelledAt; }
    }
    
    /**
     * 환불 결과
     */
    class RefundResult {
        private final String refundId;
        private final boolean success;
        private final Money refundedAmount;
        private final String failureReason;
        private final LocalDateTime refundedAt;
        
        public RefundResult(String refundId, boolean success, Money refundedAmount, String failureReason) {
            this.refundId = refundId;
            this.success = success;
            this.refundedAmount = refundedAmount;
            this.failureReason = failureReason;
            this.refundedAt = LocalDateTime.now();
        }
        
        // Getters
        public String getRefundId() { return refundId; }
        public boolean isSuccess() { return success; }
        public Money getRefundedAmount() { return refundedAmount; }
        public String getFailureReason() { return failureReason; }
        public LocalDateTime getRefundedAt() { return refundedAt; }
    }
    
    /**
     * 결제 상태
     */
    enum PaymentStatus {
        PENDING("결제 대기중"),
        PROCESSING("결제 처리중"),
        COMPLETED("결제 완료"),
        FAILED("결제 실패"),
        CANCELLED("결제 취소"),
        REFUNDED("환불 완료"),
        PARTIALLY_REFUNDED("부분 환불");
        
        private final String description;
        
        PaymentStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}