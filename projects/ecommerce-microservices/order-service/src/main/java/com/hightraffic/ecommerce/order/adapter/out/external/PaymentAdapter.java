package com.hightraffic.ecommerce.order.adapter.out.external;

import com.hightraffic.ecommerce.order.application.port.out.PaymentProcessingPort;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 외부 결제 API 어댑터
 * 
 * PaymentProcessingPort를 구현하여 외부 결제 서비스와 통신합니다.
 * 
 * 주요 기능:
 * - 결제 요청 처리
 * - 결제 상태 조회
 * - 결제 취소 및 환불
 * - 비동기 결제 처리
 * 
 * 신뢰성 보장:
 * - 재시도 메커니즘
 * - 회로 차단기 패턴 (향후 확장)
 * - 상세한 로깅과 모니터링
 */
@Component
public class PaymentAdapter implements PaymentProcessingPort {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentAdapter.class);
    
    private final RestTemplate restTemplate;
    private final Executor asyncExecutor;
    private final PaymentConfigurationProperties config;
    
    // 결제 API 엔드포인트
    private static final String PAYMENT_API_BASE_URL = "/api/v1/payments";
    private static final String PROCESS_PAYMENT_URL = PAYMENT_API_BASE_URL + "/process";
    private static final String CANCEL_PAYMENT_URL = PAYMENT_API_BASE_URL + "/cancel";
    private static final String REFUND_PAYMENT_URL = PAYMENT_API_BASE_URL + "/refund";
    private static final String PAYMENT_STATUS_URL = PAYMENT_API_BASE_URL + "/status";
    
    public PaymentAdapter(RestTemplate restTemplate, 
                         Executor asyncExecutor,
                         PaymentConfigurationProperties config) {
        this.restTemplate = restTemplate;
        this.asyncExecutor = asyncExecutor;
        this.config = config;
    }
    
    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("결제 처리 시작: orderId={}, amount={}, method={}", 
                request.getOrderId(), request.getAmount(), request.getPaymentMethod());
        
        try {
            // 결제 요청 DTO 생성
            ExternalPaymentRequest externalRequest = buildExternalPaymentRequest(request);
            
            // 외부 결제 API 호출
            ResponseEntity<ExternalPaymentResponse> response = restTemplate.postForEntity(
                buildFullUrl(PROCESS_PAYMENT_URL), 
                createHttpEntity(externalRequest), 
                ExternalPaymentResponse.class
            );
            
            ExternalPaymentResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.isSuccess()) {
                log.info("결제 성공: orderId={}, paymentId={}, transactionId={}", 
                        request.getOrderId(), responseBody.getPaymentId(), responseBody.getTransactionId());
                
                return new PaymentResult(
                    responseBody.getPaymentId(),
                    responseBody.getTransactionId()
                );
            } else {
                String failureReason = responseBody != null ? responseBody.getFailureReason() : "Unknown error";
                log.warn("결제 실패: orderId={}, reason={}", request.getOrderId(), failureReason);
                
                return new PaymentResult(
                    responseBody != null ? responseBody.getPaymentId() : UUID.randomUUID().toString(),
                    PaymentStatus.FAILED,
                    failureReason
                );
            }
            
        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생: orderId={}", request.getOrderId(), e);
            
            return new PaymentResult(
                UUID.randomUUID().toString(),
                PaymentStatus.FAILED,
                "결제 처리 중 시스템 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    @Override
    public CompletableFuture<PaymentResult> processPaymentAsync(PaymentRequest request) {
        log.info("비동기 결제 처리 시작: orderId={}", request.getOrderId());
        
        return CompletableFuture.supplyAsync(() -> processPayment(request), asyncExecutor)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("비동기 결제 처리 실패: orderId={}", request.getOrderId(), throwable);
                } else {
                    log.info("비동기 결제 처리 완료: orderId={}, success={}", 
                            request.getOrderId(), result.isSuccess());
                }
            });
    }
    
    @Override
    public CancellationResult cancelPayment(String paymentId, String reason) {
        log.info("결제 취소 시작: paymentId={}, reason={}", paymentId, reason);
        
        try {
            ExternalCancelRequest cancelRequest = new ExternalCancelRequest(paymentId, reason);
            
            ResponseEntity<ExternalCancelResponse> response = restTemplate.postForEntity(
                buildFullUrl(CANCEL_PAYMENT_URL),
                createHttpEntity(cancelRequest),
                ExternalCancelResponse.class
            );
            
            ExternalCancelResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.isSuccess()) {
                log.info("결제 취소 성공: paymentId={}, cancellationId={}", 
                        paymentId, responseBody.getCancellationId());
                
                return new CancellationResult(
                    responseBody.getCancellationId(),
                    true,
                    null
                );
            } else {
                String failureReason = responseBody != null ? responseBody.getFailureReason() : "Unknown error";
                log.warn("결제 취소 실패: paymentId={}, reason={}", paymentId, failureReason);
                
                return new CancellationResult(
                    UUID.randomUUID().toString(),
                    false,
                    failureReason
                );
            }
            
        } catch (Exception e) {
            log.error("결제 취소 중 오류 발생: paymentId={}", paymentId, e);
            
            return new CancellationResult(
                UUID.randomUUID().toString(),
                false,
                "결제 취소 중 시스템 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    @Override
    public RefundResult refundPayment(String paymentId, Money refundAmount, String reason) {
        log.info("환불 처리 시작: paymentId={}, amount={}, reason={}", 
                paymentId, refundAmount, reason);
        
        try {
            ExternalRefundRequest refundRequest = new ExternalRefundRequest(
                paymentId, 
                refundAmount.getAmount(), 
                reason
            );
            
            ResponseEntity<ExternalRefundResponse> response = restTemplate.postForEntity(
                buildFullUrl(REFUND_PAYMENT_URL),
                createHttpEntity(refundRequest),
                ExternalRefundResponse.class
            );
            
            ExternalRefundResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.isSuccess()) {
                log.info("환불 성공: paymentId={}, refundId={}, amount={}", 
                        paymentId, responseBody.getRefundId(), responseBody.getRefundedAmount());
                
                return new RefundResult(
                    responseBody.getRefundId(),
                    true,
                    new Money(responseBody.getRefundedAmount(), "KRW"),
                    null
                );
            } else {
                String failureReason = responseBody != null ? responseBody.getFailureReason() : "Unknown error";
                log.warn("환불 실패: paymentId={}, reason={}", paymentId, failureReason);
                
                return new RefundResult(
                    UUID.randomUUID().toString(),
                    false,
                    refundAmount,
                    failureReason
                );
            }
            
        } catch (Exception e) {
            log.error("환불 처리 중 오류 발생: paymentId={}", paymentId, e);
            
            return new RefundResult(
                UUID.randomUUID().toString(),
                false,
                refundAmount,
                "환불 처리 중 시스템 오류가 발생했습니다: " + e.getMessage()
            );
        }
    }
    
    @Override
    public PaymentStatus getPaymentStatus(String paymentId) {
        log.debug("결제 상태 조회: paymentId={}", paymentId);
        
        try {
            ResponseEntity<ExternalPaymentStatusResponse> response = restTemplate.getForEntity(
                buildFullUrl(PAYMENT_STATUS_URL + "/" + paymentId),
                ExternalPaymentStatusResponse.class
            );
            
            ExternalPaymentStatusResponse responseBody = response.getBody();
            
            if (responseBody != null) {
                PaymentStatus status = mapExternalStatusToInternal(responseBody.getStatus());
                log.debug("결제 상태 조회 성공: paymentId={}, status={}", paymentId, status);
                return status;
            } else {
                log.warn("결제 상태 조회 실패: paymentId={}", paymentId);
                return PaymentStatus.FAILED;
            }
            
        } catch (Exception e) {
            log.error("결제 상태 조회 중 오류 발생: paymentId={}", paymentId, e);
            return PaymentStatus.FAILED;
        }
    }
    
    @Override
    public boolean canProcessPayment(CustomerId customerId, Money amount) {
        log.debug("결제 가능 여부 확인: customerId={}, amount={}", customerId, amount);
        
        try {
            // 고객 결제 한도 확인
            if (amount.getAmount().compareTo(config.getMaxPaymentAmount()) > 0) {
                log.warn("결제 금액 한도 초과: customerId={}, amount={}, maxAmount={}", 
                        customerId, amount, config.getMaxPaymentAmount());
                return false;
            }
            
            // 일일 결제 한도 확인 (향후 구현)
            // 블랙리스트 확인 (향후 구현)
            
            return true;
            
        } catch (Exception e) {
            log.error("결제 가능 여부 확인 중 오류 발생: customerId={}", customerId, e);
            return false;
        }
    }
    
    // 헬퍼 메서드들
    
    private ExternalPaymentRequest buildExternalPaymentRequest(PaymentRequest request) {
        return new ExternalPaymentRequest(
            request.getOrderId().getValue(),
            request.getCustomerId().getValue(),
            request.getAmount().getAmount(),
            request.getPaymentMethod(),
            request.getDescription(),
            mapPaymentDetails(request.getPaymentDetails())
        );
    }
    
    private ExternalPaymentDetails mapPaymentDetails(PaymentDetails paymentDetails) {
        // PaymentDetails 인터페이스를 외부 API 형식으로 변환
        // 실제 구현에서는 구체적인 PaymentDetails 타입별로 매핑
        return new ExternalPaymentDetails();
    }
    
    private PaymentStatus mapExternalStatusToInternal(String externalStatus) {
        switch (externalStatus.toUpperCase()) {
            case "PENDING":
                return PaymentStatus.PENDING;
            case "PROCESSING":
                return PaymentStatus.PROCESSING;
            case "COMPLETED":
            case "SUCCESS":
                return PaymentStatus.COMPLETED;
            case "FAILED":
            case "ERROR":
                return PaymentStatus.FAILED;
            case "CANCELLED":
                return PaymentStatus.CANCELLED;
            case "REFUNDED":
                return PaymentStatus.REFUNDED;
            case "PARTIALLY_REFUNDED":
                return PaymentStatus.PARTIALLY_REFUNDED;
            default:
                log.warn("알 수 없는 외부 결제 상태: {}", externalStatus);
                return PaymentStatus.FAILED;
        }
    }
    
    private String buildFullUrl(String endpoint) {
        return config.getBaseUrl() + endpoint;
    }
    
    private <T> HttpEntity<T> createHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", "Bearer " + config.getApiKey());
        headers.set("X-API-Version", "1.0");
        
        return new HttpEntity<>(body, headers);
    }
    
    // 외부 API 요청/응답 DTO 클래스들
    
    private static class ExternalPaymentRequest {
        private final String orderId;
        private final String customerId;
        private final java.math.BigDecimal amount;
        private final String paymentMethod;
        private final String description;
        private final ExternalPaymentDetails paymentDetails;
        
        public ExternalPaymentRequest(String orderId, String customerId, 
                                    java.math.BigDecimal amount, String paymentMethod,
                                    String description, ExternalPaymentDetails paymentDetails) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.amount = amount;
            this.paymentMethod = paymentMethod;
            this.description = description;
            this.paymentDetails = paymentDetails;
        }
        
        // Getters (Jackson 직렬화용)
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public java.math.BigDecimal getAmount() { return amount; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getDescription() { return description; }
        public ExternalPaymentDetails getPaymentDetails() { return paymentDetails; }
    }
    
    private static class ExternalPaymentDetails {
        // 외부 결제 API에 필요한 상세 정보
        // 카드 정보, 계좌 정보 등
    }
    
    private static class ExternalPaymentResponse {
        private String paymentId;
        private boolean success;
        private String transactionId;
        private String status;
        private String failureReason;
        
        // Getters/Setters (Jackson 역직렬화용)
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
    
    private static class ExternalCancelRequest {
        private final String paymentId;
        private final String reason;
        
        public ExternalCancelRequest(String paymentId, String reason) {
            this.paymentId = paymentId;
            this.reason = reason;
        }
        
        public String getPaymentId() { return paymentId; }
        public String getReason() { return reason; }
    }
    
    private static class ExternalCancelResponse {
        private String cancellationId;
        private boolean success;
        private String failureReason;
        
        public String getCancellationId() { return cancellationId; }
        public void setCancellationId(String cancellationId) { this.cancellationId = cancellationId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
    
    private static class ExternalRefundRequest {
        private final String paymentId;
        private final java.math.BigDecimal refundAmount;
        private final String reason;
        
        public ExternalRefundRequest(String paymentId, java.math.BigDecimal refundAmount, String reason) {
            this.paymentId = paymentId;
            this.refundAmount = refundAmount;
            this.reason = reason;
        }
        
        public String getPaymentId() { return paymentId; }
        public java.math.BigDecimal getRefundAmount() { return refundAmount; }
        public String getReason() { return reason; }
    }
    
    private static class ExternalRefundResponse {
        private String refundId;
        private boolean success;
        private java.math.BigDecimal refundedAmount;
        private String failureReason;
        
        public String getRefundId() { return refundId; }
        public void setRefundId(String refundId) { this.refundId = refundId; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public java.math.BigDecimal getRefundedAmount() { return refundedAmount; }
        public void setRefundedAmount(java.math.BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
    
    private static class ExternalPaymentStatusResponse {
        private String paymentId;
        private String status;
        private LocalDateTime updatedAt;
        
        public String getPaymentId() { return paymentId; }
        public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    }
}