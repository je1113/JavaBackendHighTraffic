package com.hightraffic.ecommerce.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class SimpleStockLoadTest {
    
    private static final String ORDER_URL = "http://localhost:8081/api/v1/orders";
    private static final String STOCK_URL = "http://localhost:8081/api/v1/inventory/products/550e8400-e29b-41d4-a716-446655440001/stock";
    private static final int TOTAL_REQUESTS = 5000;
    private static final int INITIAL_STOCK = 100;
    private static final int THREAD_POOL_SIZE = 200;
    
    private static final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger insufficientStockCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount = new AtomicInteger(0);
    private static final AtomicLong totalResponseTime = new AtomicLong(0);
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== 재고 예약 부하 테스트 (Simplified) ===");
        System.out.println("상품 ID: 550e8400-e29b-41d4-a716-446655440001");
        System.out.println("초기 재고: " + INITIAL_STOCK);
        System.out.println("총 요청 수: " + TOTAL_REQUESTS);
        System.out.println("스레드 풀 크기: " + THREAD_POOL_SIZE);
        System.out.println();
        
        // Start mock server first
        Thread mockServerThread = new Thread(() -> {
            try {
                MockInventoryServer.main(new String[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        mockServerThread.start();
        Thread.sleep(2000); // Wait for server to start
        
        System.out.println("부하 테스트 시작...");
        long startTime = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            final int orderIndex = i;
            executor.submit(() -> {
                try {
                    createOrder(orderIndex);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        printResults(duration);
        checkFinalStock();
        
        System.exit(0);
    }
    
    private static void createOrder(int orderIndex) {
        long requestStartTime = System.currentTimeMillis();
        
        String orderJson = String.format("""
            {
                "customerId": "550e8400-e29b-41d4-a716-%012d",
                "items": [{
                    "productId": "550e8400-e29b-41d4-a716-446655440001",
                    "quantity": 1,
                    "unitPrice": 10000
                }]
            }
            """, orderIndex % 1000);
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ORDER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(orderJson))
                .timeout(Duration.ofSeconds(5))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            long responseTime = System.currentTimeMillis() - requestStartTime;
            totalResponseTime.addAndGet(responseTime);
            
            if (response.statusCode() == 201) {
                successCount.incrementAndGet();
            } else if (response.statusCode() == 409) {
                insufficientStockCount.incrementAndGet();
            } else {
                errorCount.incrementAndGet();
                System.err.println("Unexpected response: " + response.statusCode() + " - " + response.body());
            }
            
        } catch (Exception e) {
            errorCount.incrementAndGet();
            long responseTime = System.currentTimeMillis() - requestStartTime;
            totalResponseTime.addAndGet(responseTime);
        }
    }
    
    private static void checkFinalStock() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STOCK_URL))
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("\n최종 재고 상태: " + response.body());
        } catch (Exception e) {
            System.err.println("재고 상태 확인 실패: " + e.getMessage());
        }
    }
    
    private static void printResults(long duration) {
        System.out.println("\n=== 부하 테스트 결과 ===");
        System.out.println("테스트 소요 시간: " + duration + "ms (" + String.format("%.2f", duration / 1000.0) + "초)");
        System.out.println("초당 처리량(TPS): " + String.format("%.2f", TOTAL_REQUESTS / (duration / 1000.0)));
        System.out.println();
        System.out.println("총 요청 수: " + TOTAL_REQUESTS);
        System.out.println("성공: " + successCount.get());
        System.out.println("재고 부족: " + insufficientStockCount.get());
        System.out.println("에러: " + errorCount.get());
        
        double avgResponseTime = totalResponseTime.get() / (double) TOTAL_REQUESTS;
        System.out.println("평균 응답 시간: " + String.format("%.2f", avgResponseTime) + "ms");
        
        double successRate = (successCount.get() / (double) TOTAL_REQUESTS) * 100;
        System.out.println("성공률: " + String.format("%.2f", successRate) + "%");
        
        System.out.println("\n=== 예상 vs 실제 ===");
        System.out.println("예상 재고 예약 성공: " + INITIAL_STOCK);
        System.out.println("실제 재고 예약 성공: " + successCount.get());
        System.out.println("예상 재고 부족: " + (TOTAL_REQUESTS - INITIAL_STOCK));
        System.out.println("실제 재고 부족: " + insufficientStockCount.get());
        
        // Check for race condition
        if (successCount.get() > INITIAL_STOCK) {
            System.err.println("\n⚠️  경고: 재고보다 많은 주문이 성공했습니다! 동시성 문제가 있습니다.");
        } else if (successCount.get() == INITIAL_STOCK) {
            System.out.println("\n✅ 성공: 정확히 재고만큼 주문이 성공했습니다. 동시성 제어가 올바르게 작동합니다.");
        }
    }
}