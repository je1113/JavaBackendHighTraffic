package com.hightraffic.ecommerce.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 재고 예약 부하 테스트
 * 
 * 시나리오: 재고 100개 상품에 5000개의 동시 주문 요청
 */
@Slf4j
@SpringBootApplication
public class StockReservationLoadTest implements CommandLineRunner {
    
    private static final String INVENTORY_SERVICE_URL = "http://localhost:8081";
    private static final String ORDER_SERVICE_URL = "http://localhost:8081";
    private static final String PRODUCT_ID = "550e8400-e29b-41d4-a716-446655440001";
    private static final int TOTAL_REQUESTS = 5000;
    private static final int INITIAL_STOCK = 100;
    private static final Duration TEST_DURATION = Duration.ofSeconds(1);
    
    private final WebClient inventoryClient;
    private final WebClient orderClient;
    private final ObjectMapper objectMapper;
    
    // Metrics
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger stockReservedCount = new AtomicInteger(0);
    private final AtomicInteger insufficientStockCount = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    public StockReservationLoadTest() {
        this.inventoryClient = WebClient.builder()
            .baseUrl(INVENTORY_SERVICE_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
            
        this.orderClient = WebClient.builder()
            .baseUrl(ORDER_SERVICE_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
            
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    public static void main(String[] args) {
        SpringApplication.run(StockReservationLoadTest.class, args);
    }
    
    @Override
    public void run(String... args) throws Exception {
        log.info("=== 재고 예약 부하 테스트 시작 ===");
        log.info("상품 ID: {}", PRODUCT_ID);
        log.info("초기 재고: {}", INITIAL_STOCK);
        log.info("총 요청 수: {}", TOTAL_REQUESTS);
        log.info("테스트 시간: {}초", TEST_DURATION.getSeconds());
        
        // 1. 테스트 데이터 초기화
        initializeTestData();
        
        // 2. 부하 테스트 실행
        runLoadTest();
        
        // 3. 결과 출력
        printResults();
    }
    
    private void initializeTestData() {
        log.info("테스트 데이터 초기화 중...");
        
        // 상품 생성 또는 재고 조정
        String createProductJson = String.format("""
            {
                "productId": "%s",
                "productName": "Test Product for Load Test",
                "initialStock": %d,
                "lowStockThreshold": 10
            }
            """, PRODUCT_ID, INITIAL_STOCK);
            
        try {
            inventoryClient.post()
                .uri("/api/v1/inventory/products")
                .bodyValue(createProductJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            log.info("테스트 상품 생성 완료");
        } catch (Exception e) {
            // 이미 존재하는 경우 재고 조정
            log.info("상품이 이미 존재함. 재고 조정 중...");
            
            String adjustStockJson = String.format("""
                {
                    "productId": "%s",
                    "newTotalQuantity": %d,
                    "reason": "Load test initialization"
                }
                """, PRODUCT_ID, INITIAL_STOCK);
                
            inventoryClient.post()
                .uri("/api/v1/inventory/products/{productId}/stock/adjust", PRODUCT_ID)
                .bodyValue(adjustStockJson)
                .retrieve()
                .bodyToMono(String.class)
                .block();
        }
        
        log.info("테스트 데이터 초기화 완료");
    }
    
    private void runLoadTest() throws InterruptedException {
        log.info("부하 테스트 시작: {} 요청을 {}초 내에 전송", TOTAL_REQUESTS, TEST_DURATION.getSeconds());
        
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        LocalDateTime startTime = LocalDateTime.now();
        
        // 5000개의 동시 요청 생성
        Flux.range(1, TOTAL_REQUESTS)
            .flatMap(i -> createOrder(i)
                .doOnTerminate(latch::countDown)
                .subscribeOn(Schedulers.parallel())
            , TOTAL_REQUESTS) // 최대 동시 실행 수
            .subscribe(
                result -> log.debug("요청 완료: {}", result),
                error -> log.error("요청 실패", error),
                () -> log.info("모든 요청 전송 완료")
            );
        
        // 모든 요청이 완료될 때까지 대기
        latch.await();
        
        LocalDateTime endTime = LocalDateTime.now();
        Duration actualDuration = Duration.between(startTime, endTime);
        log.info("실제 테스트 소요 시간: {}초", actualDuration.getSeconds());
    }
    
    private Mono<OrderResult> createOrder(int orderIndex) {
        long startTime = System.currentTimeMillis();
        
        String orderId = String.format("ORDER-%05d-%s", orderIndex, UUID.randomUUID().toString().substring(0, 8));
        String customerId = String.format("550e8400-e29b-41d4-a716-%012d", orderIndex % 1000);
        
        String orderJson = String.format("""
            {
                "customerId": "%s",
                "items": [
                    {
                        "productId": "%s",
                        "quantity": 1,
                        "unitPrice": 10000
                    }
                ],
                "shippingAddress": {
                    "street": "Test Street %d",
                    "city": "Seoul",
                    "zipCode": "12345"
                }
            }
            """, customerId, PRODUCT_ID, orderIndex);
        
        return orderClient.post()
            .uri("/api/v1/orders")
            .bodyValue(orderJson)
            .exchangeToMono(response -> {
                long responseTime = System.currentTimeMillis() - startTime;
                totalResponseTime.addAndGet(responseTime);
                
                if (response.statusCode().is2xxSuccessful()) {
                    successCount.incrementAndGet();
                    stockReservedCount.incrementAndGet();
                    return response.bodyToMono(String.class)
                        .map(body -> new OrderResult(orderId, true, responseTime, body));
                } else {
                    failureCount.incrementAndGet();
                    
                    return response.bodyToMono(String.class)
                        .map(body -> {
                            if (body.contains("insufficient") || body.contains("재고 부족")) {
                                insufficientStockCount.incrementAndGet();
                            }
                            return new OrderResult(orderId, false, responseTime, body);
                        })
                        .onErrorReturn(new OrderResult(orderId, false, responseTime, "Error: " + response.statusCode()));
                }
            })
            .onErrorResume(error -> {
                long responseTime = System.currentTimeMillis() - startTime;
                totalResponseTime.addAndGet(responseTime);
                failureCount.incrementAndGet();
                return Mono.just(new OrderResult(orderId, false, responseTime, error.getMessage()));
            });
    }
    
    private void printResults() {
        log.info("\n=== 부하 테스트 결과 ===");
        log.info("총 요청 수: {}", TOTAL_REQUESTS);
        log.info("성공: {}", successCount.get());
        log.info("실패: {}", failureCount.get());
        log.info("재고 예약 성공: {}", stockReservedCount.get());
        log.info("재고 부족: {}", insufficientStockCount.get());
        log.info("기타 실패: {}", failureCount.get() - insufficientStockCount.get());
        
        double avgResponseTime = totalResponseTime.get() / (double) TOTAL_REQUESTS;
        log.info("평균 응답 시간: {:.2f}ms", avgResponseTime);
        
        double successRate = (successCount.get() / (double) TOTAL_REQUESTS) * 100;
        log.info("성공률: {:.2f}%", successRate);
        
        log.info("\n=== 예상 vs 실제 ===");
        log.info("예상 재고 예약 성공: {} (초기 재고)", INITIAL_STOCK);
        log.info("실제 재고 예약 성공: {}", stockReservedCount.get());
        log.info("예상 재고 부족: {}", TOTAL_REQUESTS - INITIAL_STOCK);
        log.info("실제 재고 부족: {}", insufficientStockCount.get());
        
        // 재고 상태 확인
        checkFinalStock();
    }
    
    private void checkFinalStock() {
        try {
            String stockInfo = inventoryClient.get()
                .uri("/api/v1/inventory/products/{productId}/stock", PRODUCT_ID)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            log.info("\n최종 재고 상태: {}", stockInfo);
        } catch (Exception e) {
            log.error("재고 상태 확인 실패", e);
        }
    }
    
    private record OrderResult(String orderId, boolean success, long responseTime, String response) {}
}