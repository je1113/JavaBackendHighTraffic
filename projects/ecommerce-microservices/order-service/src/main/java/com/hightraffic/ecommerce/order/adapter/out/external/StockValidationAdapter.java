package com.hightraffic.ecommerce.order.adapter.out.external;

import com.hightraffic.ecommerce.order.application.port.out.StockValidationPort;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 재고 검증 어댑터
 * 
 * StockValidationPort를 구현하여 Inventory Service와 통신합니다.
 * 
 * 주요 기능:
 * - 재고 가용성 확인
 * - 재고 예약 및 해제
 * - 재고 정보 조회
 * - 배치 처리 지원
 * 
 * 성능 최적화:
 * - 배치 요청 지원
 * - 비동기 처리
 * - 캐싱 전략 (향후 확장)
 * 
 * 신뢰성 보장:
 * - 재시도 메커니즘
 * - 회로 차단기 패턴 (향후 확장)
 * - 상세한 로깅과 모니터링
 */
@Component
public class StockValidationAdapter implements StockValidationPort {
    
    private static final Logger log = LoggerFactory.getLogger(StockValidationAdapter.class);
    
    private final RestTemplate restTemplate;
    private final Executor asyncExecutor;
    private final StockValidationConfigurationProperties config;
    
    // Inventory Service API 엔드포인트
    private static final String INVENTORY_API_BASE_URL = "/api/v1/inventory";
    private static final String CHECK_STOCK_URL = INVENTORY_API_BASE_URL + "/check";
    private static final String BATCH_CHECK_STOCK_URL = INVENTORY_API_BASE_URL + "/batch-check";
    private static final String RESERVE_STOCK_URL = INVENTORY_API_BASE_URL + "/reserve";
    private static final String BATCH_RESERVE_STOCK_URL = INVENTORY_API_BASE_URL + "/batch-reserve";
    private static final String RELEASE_STOCK_URL = INVENTORY_API_BASE_URL + "/release";
    private static final String STOCK_INFO_URL = INVENTORY_API_BASE_URL + "/info";
    
    public StockValidationAdapter(RestTemplate restTemplate, 
                                 Executor asyncExecutor,
                                 StockValidationConfigurationProperties config) {
        this.restTemplate = restTemplate;
        this.asyncExecutor = asyncExecutor;
        this.config = config;
    }
    
    @Override
    public boolean isStockAvailable(ProductId productId, Integer quantity) {
        log.debug("재고 가용성 확인: productId={}, quantity={}", productId, quantity);
        
        try {
            StockCheckRequest request = new StockCheckRequest(productId.getValue(), quantity);
            
            ResponseEntity<StockCheckResponse> response = restTemplate.postForEntity(
                buildFullUrl(CHECK_STOCK_URL),
                createHttpEntity(request),
                StockCheckResponse.class
            );
            
            StockCheckResponse responseBody = response.getBody();
            boolean available = responseBody != null && responseBody.isAvailable();
            
            log.debug("재고 가용성 확인 결과: productId={}, quantity={}, available={}", 
                     productId, quantity, available);
            
            return available;
            
        } catch (Exception e) {
            log.error("재고 가용성 확인 중 오류 발생: productId={}, quantity={}", 
                     productId, quantity, e);
            return false;
        }
    }
    
    @Override
    public Map<ProductId, Boolean> checkBatchStockAvailability(Map<ProductId, Integer> stockRequests) {
        log.debug("배치 재고 가용성 확인: requests={}", stockRequests.size());
        
        try {
            List<BatchStockCheckRequest.StockCheckItem> items = stockRequests.entrySet().stream()
                .map(entry -> new BatchStockCheckRequest.StockCheckItem(
                    entry.getKey().getValue(), 
                    entry.getValue()
                ))
                .collect(Collectors.toList());
            
            BatchStockCheckRequest request = new BatchStockCheckRequest(items);
            
            ResponseEntity<BatchStockCheckResponse> response = restTemplate.postForEntity(
                buildFullUrl(BATCH_CHECK_STOCK_URL),
                createHttpEntity(request),
                BatchStockCheckResponse.class
            );
            
            BatchStockCheckResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.getResults() != null) {
                Map<ProductId, Boolean> results = new HashMap<>();
                
                for (BatchStockCheckResponse.StockCheckResult result : responseBody.getResults()) {
                    ProductId productId = ProductId.of(result.getProductId());
                    results.put(productId, result.isAvailable());
                }
                
                log.debug("배치 재고 가용성 확인 완료: successCount={}", results.size());
                return results;
            } else {
                log.warn("배치 재고 가용성 확인 응답이 비어있음");
                return new HashMap<>();
            }
            
        } catch (Exception e) {
            log.error("배치 재고 가용성 확인 중 오류 발생", e);
            return new HashMap<>();
        }
    }
    
    @Override
    public String reserveStock(ProductId productId, Integer quantity, String orderId) {
        log.info("재고 예약: productId={}, quantity={}, orderId={}", 
                productId, quantity, orderId);
        
        try {
            StockReservationRequest request = new StockReservationRequest(
                productId.getValue(), 
                quantity, 
                orderId
            );
            
            ResponseEntity<StockReservationResponse> response = restTemplate.postForEntity(
                buildFullUrl(RESERVE_STOCK_URL),
                createHttpEntity(request),
                StockReservationResponse.class
            );
            
            StockReservationResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.isSuccess()) {
                log.info("재고 예약 성공: productId={}, orderId={}, reservationId={}", 
                        productId, orderId, responseBody.getReservationId());
                return responseBody.getReservationId();
            } else {
                String failureReason = responseBody != null ? responseBody.getFailureReason() : "Unknown error";
                log.warn("재고 예약 실패: productId={}, orderId={}, reason={}", 
                        productId, orderId, failureReason);
                return null;
            }
            
        } catch (Exception e) {
            log.error("재고 예약 중 오류 발생: productId={}, orderId={}", 
                     productId, orderId, e);
            return null;
        }
    }
    
    @Override
    public List<StockReservationResult> reserveBatchStock(List<StockReservationRequest> reservations) {
        log.info("배치 재고 예약: requestCount={}", reservations.size());
        
        try {
            List<BatchStockReservationRequest.ReservationItem> items = reservations.stream()
                .map(req -> new BatchStockReservationRequest.ReservationItem(
                    req.getProductId().getValue(),
                    req.getQuantity(),
                    req.getOrderId()
                ))
                .collect(Collectors.toList());
            
            BatchStockReservationRequest request = new BatchStockReservationRequest(items);
            
            ResponseEntity<BatchStockReservationResponse> response = restTemplate.postForEntity(
                buildFullUrl(BATCH_RESERVE_STOCK_URL),
                createHttpEntity(request),
                BatchStockReservationResponse.class
            );
            
            BatchStockReservationResponse responseBody = response.getBody();
            
            if (responseBody != null && responseBody.getResults() != null) {
                List<StockReservationResult> results = responseBody.getResults().stream()
                    .map(result -> {
                        ProductId productId = ProductId.of(result.getProductId());
                        
                        if (result.isSuccess()) {
                            return new StockReservationResult(productId, result.getReservationId());
                        } else {
                            return new StockReservationResult(productId, result.getFailureReason());
                        }
                    })
                    .collect(Collectors.toList());
                
                log.info("배치 재고 예약 완료: successCount={}", 
                        results.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum());
                
                return results;
            } else {
                log.warn("배치 재고 예약 응답이 비어있음");
                return List.of();
            }
            
        } catch (Exception e) {
            log.error("배치 재고 예약 중 오류 발생", e);
            return List.of();
        }
    }
    
    @Override
    public boolean releaseStock(String reservationId, String orderId) {
        log.info("재고 해제: reservationId={}, orderId={}", reservationId, orderId);
        
        try {
            StockReleaseRequest request = new StockReleaseRequest(reservationId, orderId);
            
            ResponseEntity<StockReleaseResponse> response = restTemplate.postForEntity(
                buildFullUrl(RELEASE_STOCK_URL),
                createHttpEntity(request),
                StockReleaseResponse.class
            );
            
            StockReleaseResponse responseBody = response.getBody();
            boolean success = responseBody != null && responseBody.isSuccess();
            
            if (success) {
                log.info("재고 해제 성공: reservationId={}, orderId={}", reservationId, orderId);
            } else {
                String failureReason = responseBody != null ? responseBody.getFailureReason() : "Unknown error";
                log.warn("재고 해제 실패: reservationId={}, orderId={}, reason={}", 
                        reservationId, orderId, failureReason);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("재고 해제 중 오류 발생: reservationId={}, orderId={}", 
                     reservationId, orderId, e);
            return false;
        }
    }
    
    @Override
    public StockInfo getStockInfo(ProductId productId) {
        log.debug("재고 정보 조회: productId={}", productId);
        
        try {
            ResponseEntity<StockInfoResponse> response = restTemplate.getForEntity(
                buildFullUrl(STOCK_INFO_URL + "/" + productId.getValue()),
                StockInfoResponse.class
            );
            
            StockInfoResponse responseBody = response.getBody();
            
            if (responseBody != null) {
                StockInfo stockInfo = new StockInfo(
                    productId,
                    responseBody.getProductName(),
                    responseBody.getAvailableQuantity(),
                    responseBody.getReservedQuantity(),
                    responseBody.getTotalQuantity(),
                    new Money(responseBody.getPrice(), "KRW"),
                    responseBody.isActive(),
                    responseBody.isLowStock()
                );
                
                log.debug("재고 정보 조회 완료: productId={}, available={}", 
                         productId, responseBody.getAvailableQuantity());
                
                return stockInfo;
            } else {
                log.warn("재고 정보 조회 응답이 비어있음: productId={}", productId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("재고 정보 조회 중 오류 발생: productId={}", productId, e);
            return null;
        }
    }
    
    @Override
    public CompletableFuture<Boolean> isStockAvailableAsync(ProductId productId, Integer quantity) {
        log.debug("비동기 재고 가용성 확인: productId={}, quantity={}", productId, quantity);
        
        return CompletableFuture.supplyAsync(() -> isStockAvailable(productId, quantity), asyncExecutor)
            .whenComplete((result, throwable) -> {
                if (throwable != null) {
                    log.error("비동기 재고 가용성 확인 실패: productId={}, quantity={}", 
                             productId, quantity, throwable);
                } else {
                    log.debug("비동기 재고 가용성 확인 완료: productId={}, quantity={}, available={}", 
                             productId, quantity, result);
                }
            });
    }
    
    // 헬퍼 메서드들
    
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
    
    private static class StockCheckRequest {
        private final String productId;
        private final Integer quantity;
        
        public StockCheckRequest(String productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
        
        public String getProductId() { return productId; }
        public Integer getQuantity() { return quantity; }
    }
    
    private static class StockCheckResponse {
        private String productId;
        private boolean available;
        private Integer availableQuantity;
        
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        
        public Integer getAvailableQuantity() { return availableQuantity; }
        public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
    }
    
    private static class BatchStockCheckRequest {
        private final List<StockCheckItem> items;
        
        public BatchStockCheckRequest(List<StockCheckItem> items) {
            this.items = items;
        }
        
        public List<StockCheckItem> getItems() { return items; }
        
        public static class StockCheckItem {
            private final String productId;
            private final Integer quantity;
            
            public StockCheckItem(String productId, Integer quantity) {
                this.productId = productId;
                this.quantity = quantity;
            }
            
            public String getProductId() { return productId; }
            public Integer getQuantity() { return quantity; }
        }
    }
    
    private static class BatchStockCheckResponse {
        private List<StockCheckResult> results;
        
        public List<StockCheckResult> getResults() { return results; }
        public void setResults(List<StockCheckResult> results) { this.results = results; }
        
        public static class StockCheckResult {
            private String productId;
            private boolean available;
            private Integer availableQuantity;
            
            public String getProductId() { return productId; }
            public void setProductId(String productId) { this.productId = productId; }
            
            public boolean isAvailable() { return available; }
            public void setAvailable(boolean available) { this.available = available; }
            
            public Integer getAvailableQuantity() { return availableQuantity; }
            public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
        }
    }
    
    // 로컬 StockReservationRequest 클래스 제거 - 포트의 클래스 사용
    
    private static class StockReservationResponse {
        private boolean success;
        private String reservationId;
        private String failureReason;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getReservationId() { return reservationId; }
        public void setReservationId(String reservationId) { this.reservationId = reservationId; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
    
    private static class BatchStockReservationRequest {
        private final List<ReservationItem> items;
        
        public BatchStockReservationRequest(List<ReservationItem> items) {
            this.items = items;
        }
        
        public List<ReservationItem> getItems() { return items; }
        
        public static class ReservationItem {
            private final String productId;
            private final Integer quantity;
            private final String orderId;
            
            public ReservationItem(String productId, Integer quantity, String orderId) {
                this.productId = productId;
                this.quantity = quantity;
                this.orderId = orderId;
            }
            
            public String getProductId() { return productId; }
            public Integer getQuantity() { return quantity; }
            public String getOrderId() { return orderId; }
        }
    }
    
    private static class BatchStockReservationResponse {
        private List<ReservationResult> results;
        
        public List<ReservationResult> getResults() { return results; }
        public void setResults(List<ReservationResult> results) { this.results = results; }
        
        public static class ReservationResult {
            private String productId;
            private boolean success;
            private String reservationId;
            private String failureReason;
            
            public String getProductId() { return productId; }
            public void setProductId(String productId) { this.productId = productId; }
            
            public boolean isSuccess() { return success; }
            public void setSuccess(boolean success) { this.success = success; }
            
            public String getReservationId() { return reservationId; }
            public void setReservationId(String reservationId) { this.reservationId = reservationId; }
            
            public String getFailureReason() { return failureReason; }
            public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
        }
    }
    
    private static class StockReleaseRequest {
        private final String reservationId;
        private final String orderId;
        
        public StockReleaseRequest(String reservationId, String orderId) {
            this.reservationId = reservationId;
            this.orderId = orderId;
        }
        
        public String getReservationId() { return reservationId; }
        public String getOrderId() { return orderId; }
    }
    
    private static class StockReleaseResponse {
        private boolean success;
        private String failureReason;
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    }
    
    private static class StockInfoResponse {
        private String productId;
        private String productName;
        private Integer availableQuantity;
        private Integer reservedQuantity;
        private Integer totalQuantity;
        private BigDecimal price;
        private boolean active;
        private boolean lowStock;
        
        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        
        public Integer getAvailableQuantity() { return availableQuantity; }
        public void setAvailableQuantity(Integer availableQuantity) { this.availableQuantity = availableQuantity; }
        
        public Integer getReservedQuantity() { return reservedQuantity; }
        public void setReservedQuantity(Integer reservedQuantity) { this.reservedQuantity = reservedQuantity; }
        
        public Integer getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(Integer totalQuantity) { this.totalQuantity = totalQuantity; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public boolean isLowStock() { return lowStock; }
        public void setLowStock(boolean lowStock) { this.lowStock = lowStock; }
    }
}