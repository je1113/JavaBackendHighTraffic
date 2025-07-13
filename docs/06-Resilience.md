# 복원력과 안정성

## 📖 개요
시스템 장애에 대한 복원력을 갖추고 안정적인 서비스를 제공하기 위한 패턴과 기술

## 🎯 학습 목표
- 장애 처리 패턴 구현
- 효과적인 레이트 리미팅 전략
- Circuit Breaker 패턴 활용

---

## 1. Circuit Breaker 패턴

### Resilience4j 구현

#### 기본 설정
```java
@Configuration
public class CircuitBreakerConfig {
    @Bean
    public CircuitBreaker externalApiCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50) // 50% 실패율
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slidingWindowSize(100)
            .permittedNumberOfCallsInHalfOpenState(10)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .build();
        
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        return registry.circuitBreaker("external-api");
    }
}

@Service
public class ExternalApiService {
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @Autowired
    private RestTemplate restTemplate;
    
    public String callExternalApi(String endpoint) {
        return circuitBreaker.executeSupplier(() -> 
            restTemplate.getForObject(endpoint, String.class),
            throwable -> fallbackResponse()
        );
    }
    
    private String fallbackResponse() {
        return "Service temporarily unavailable. Please try again later.";
    }
}
```

#### 상태 모니터링
```java
@Component
public class CircuitBreakerMonitor {
    @Autowired
    private CircuitBreaker circuitBreaker;
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @PostConstruct
    public void setupMetrics() {
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                meterRegistry.counter("circuit_breaker_state_transition",
                    "from", event.getStateTransition().getFromState().toString(),
                    "to", event.getStateTransition().getToState().toString()
                ).increment()
            );
        
        circuitBreaker.getEventPublisher()
            .onFailureRateExceeded(event ->
                log.warn("Circuit breaker failure rate exceeded: {}%", 
                    event.getFailureRate())
            );
    }
    
    @Scheduled(fixedDelay = 10000)
    public void logCircuitBreakerStatus() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        
        log.info("Circuit Breaker Status: {}, " +
                "Failure Rate: {}%, " +
                "Slow Call Rate: {}%, " +
                "Buffered Calls: {}",
            circuitBreaker.getState(),
            metrics.getFailureRate(),
            metrics.getSlowCallRate(),
            metrics.getNumberOfBufferedCalls()
        );
    }
}
```

---

## 2. 재시도 패턴 (Retry Pattern)

### Exponential Backoff
```java
@Configuration
public class RetryConfig {
    @Bean
    public Retry apiRetry() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .intervalFunction(IntervalFunction.ofExponentialBackoff(500, 2))
            .retryExceptions(IOException.class, TimeoutException.class)
            .ignoreExceptions(BusinessException.class)
            .retryOnResult(response -> !response.isSuccessful())
            .build();
        
        return Retry.of("api-retry", config);
    }
}

@Service
public class ResilientApiClient {
    @Autowired
    private Retry apiRetry;
    
    public ApiResponse callApiWithRetry(ApiRequest request) {
        Supplier<ApiResponse> decoratedSupplier = Retry
            .decorateSupplier(apiRetry, () -> makeApiCall(request));
        
        // 재시도 이벤트 로깅
        apiRetry.getEventPublisher()
            .onRetry(event -> 
                log.warn("Retry attempt {} for request {}", 
                    event.getNumberOfRetryAttempts(), 
                    request.getId())
            );
        
        return Try.ofSupplier(decoratedSupplier)
            .recover(throwable -> {
                log.error("All retry attempts failed", throwable);
                return ApiResponse.failure("Service unavailable");
            }).get();
    }
}
```

### 지능형 재시도
```java
@Component
public class SmartRetryStrategy {
    private final Map<String, AtomicInteger> failureCountMap = 
        new ConcurrentHashMap<>();
    
    public boolean shouldRetry(String endpoint, Exception exception) {
        // 일시적 오류만 재시도
        if (!isTransientError(exception)) {
            return false;
        }
        
        // 엔드포인트별 실패 횟수 추적
        AtomicInteger failureCount = failureCountMap.computeIfAbsent(
            endpoint, k -> new AtomicInteger(0)
        );
        
        int failures = failureCount.incrementAndGet();
        
        // 연속 실패가 많으면 재시도 중단
        if (failures > 10) {
            log.error("Too many failures for endpoint: {}", endpoint);
            return false;
        }
        
        return true;
    }
    
    private boolean isTransientError(Exception exception) {
        return exception instanceof SocketTimeoutException ||
               exception instanceof ConnectException ||
               (exception instanceof HttpServerErrorException &&
                ((HttpServerErrorException) exception).getStatusCode().is5xxServerError());
    }
    
    @Scheduled(fixedDelay = 300000) // 5분마다 리셋
    public void resetFailureCounts() {
        failureCountMap.clear();
    }
}
```

---

## 3. 레이트 리미팅

### Token Bucket 알고리즘
```java
@Component
public class TokenBucketRateLimiter {
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    
    @Value("${rate.limit.requests-per-second:100}")
    private int requestsPerSecond;
    
    public boolean allowRequest(String clientId) {
        TokenBucket bucket = buckets.computeIfAbsent(clientId, 
            k -> new TokenBucket(requestsPerSecond, requestsPerSecond)
        );
        
        return bucket.tryConsume(1);
    }
    
    private static class TokenBucket {
        private final int capacity;
        private final int refillRate;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;
        
        public TokenBucket(int capacity, int refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = new AtomicLong(System.nanoTime());
        }
        
        public synchronized boolean tryConsume(int tokensToConsume) {
            refill();
            
            if (tokens.get() >= tokensToConsume) {
                tokens.addAndGet(-tokensToConsume);
                return true;
            }
            
            return false;
        }
        
        private void refill() {
            long now = System.nanoTime();
            long timeSinceLastRefill = now - lastRefillTime.get();
            
            int tokensToAdd = (int) (timeSinceLastRefill * refillRate / 1_000_000_000);
            
            if (tokensToAdd > 0) {
                int newTokens = Math.min(capacity, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTime.set(now);
            }
        }
    }
}
```

### 분산 레이트 리미팅 (Redis)
```java
@Component
public class DistributedRateLimiter {
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    public boolean allowRequest(String clientId, int limit, Duration window) {
        String key = "rate_limit:" + clientId;
        String windowKey = key + ":" + getWindow(window);
        
        Long count = redisTemplate.opsForValue().increment(windowKey);
        
        if (count == 1) {
            redisTemplate.expire(windowKey, window);
        }
        
        return count <= limit;
    }
    
    // Sliding Window Log 알고리즘
    public boolean allowRequestSlidingWindow(String clientId, int limit) {
        String key = "rate_limit_sliding:" + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - 60000; // 1분 윈도우
        
        // Lua 스크립트로 원자적 실행
        String script = 
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]) " +
            "local count = redis.call('ZCARD', KEYS[1]) " +
            "if count < tonumber(ARGV[2]) then " +
            "   redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3]) " +
            "   redis.call('EXPIRE', KEYS[1], 60) " +
            "   return 1 " +
            "else " +
            "   return 0 " +
            "end";
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(key),
            String.valueOf(windowStart),
            String.valueOf(limit),
            String.valueOf(now)
        );
        
        return result == 1;
    }
}
```

---

## 4. Bulkhead 패턴

### 스레드 풀 격리
```java
@Configuration
public class BulkheadConfig {
    @Bean
    public ExecutorService orderProcessingExecutor() {
        return new ThreadPoolExecutor(
            10, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("order-processing-" + counter.incrementAndGet());
                    thread.setUncaughtExceptionHandler((t, e) -> 
                        log.error("Uncaught exception in thread " + t.getName(), e)
                    );
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    @Bean
    public ExecutorService paymentProcessingExecutor() {
        return new ThreadPoolExecutor(
            5, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            new CustomThreadFactory("payment-processing"),
            new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    log.warn("Payment processing task rejected");
                    throw new RejectedExecutionException("Payment queue full");
                }
            }
        );
    }
}
```

### Resilience4j Bulkhead
```java
@Service
public class IsolatedService {
    private final Bulkhead orderBulkhead;
    private final Bulkhead paymentBulkhead;
    
    public IsolatedService() {
        // 동시 실행 제한
        this.orderBulkhead = Bulkhead.of("orders", 
            BulkheadConfig.custom()
                .maxConcurrentCalls(25)
                .maxWaitDuration(Duration.ofMillis(500))
                .build()
        );
        
        this.paymentBulkhead = Bulkhead.of("payments",
            BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofMillis(100))
                .build()
        );
    }
    
    public Order processOrder(OrderRequest request) {
        return Bulkhead.decorateSupplier(orderBulkhead, () -> {
            // 주문 처리 로직
            return orderService.createOrder(request);
        }).get();
    }
    
    public Payment processPayment(PaymentRequest request) {
        return Bulkhead.decorateSupplier(paymentBulkhead, () -> {
            // 결제 처리 로직
            return paymentService.processPayment(request);
        }).get();
    }
}
```

---

## 5. 타임아웃 관리

### 계층별 타임아웃
```java
@Configuration
public class TimeoutConfig {
    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        
        // 연결 타임아웃
        factory.setConnectTimeout(3000);
        // 읽기 타임아웃
        factory.setReadTimeout(5000);
        // 연결 풀에서 연결 가져오기 타임아웃
        factory.setConnectionRequestTimeout(1000);
        
        return new RestTemplate(factory);
    }
    
    @Bean
    public WebClient webClient() {
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
            .responseTimeout(Duration.ofSeconds(5))
            .doOnConnected(conn -> 
                conn.addHandlerLast(new ReadTimeoutHandler(5))
                    .addHandlerLast(new WriteTimeoutHandler(5))
            );
        
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
```

### 동적 타임아웃
```java
@Component
public class AdaptiveTimeout {
    private final Map<String, Stats> endpointStats = new ConcurrentHashMap<>();
    
    public Duration calculateTimeout(String endpoint) {
        Stats stats = endpointStats.get(endpoint);
        
        if (stats == null || stats.getCount() < 100) {
            return Duration.ofSeconds(5); // 기본값
        }
        
        // P99 기준으로 타임아웃 설정
        double p99 = stats.getPercentile(99);
        double timeout = p99 * 1.5; // 여유 50%
        
        // 최소/최대 제한
        return Duration.ofMillis(
            Math.max(1000, Math.min(10000, (long) timeout))
        );
    }
    
    public void recordLatency(String endpoint, long latencyMs) {
        endpointStats.computeIfAbsent(endpoint, k -> new Stats())
            .record(latencyMs);
    }
}
```

---

## 6. 건강 상태 체크

### 종합적인 헬스 체크
```java
@Component
public class CompositeHealthIndicator implements HealthIndicator {
    @Autowired
    private List<HealthCheck> healthChecks;
    
    @Override
    public Health health() {
        Map<String, Health> healths = new ConcurrentHashMap<>();
        
        // 병렬 헬스 체크
        CompletableFuture<?>[] futures = healthChecks.stream()
            .map(check -> CompletableFuture.runAsync(() -> {
                try {
                    Health health = check.check();
                    healths.put(check.getName(), health);
                } catch (Exception e) {
                    healths.put(check.getName(), 
                        Health.down().withException(e).build());
                }
            }))
            .toArray(CompletableFuture[]::new);
        
        // 타임아웃 설정
        try {
            CompletableFuture.allOf(futures)
                .get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return Health.down()
                .withDetail("error", "Health check timeout")
                .build();
        }
        
        // 전체 상태 결정
        boolean allHealthy = healths.values().stream()
            .allMatch(h -> h.getStatus() == Status.UP);
        
        Health.Builder builder = allHealthy ? Health.up() : Health.down();
        healths.forEach(builder::withDetail);
        
        return builder.build();
    }
}
```

---

## 📚 참고 자료
- [Release It! - Michael T. Nygard]
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Hystrix → Resilience4j Migration Guide]

## ✅ 체크포인트
- [ ] Circuit Breaker 구현 및 모니터링
- [ ] 지능형 재시도 로직 구현
- [ ] 분산 레이트 리미팅 구현
- [ ] Bulkhead 패턴으로 리소스 격리
- [ ] 적응형 타임아웃 구현

## 🔗 다음 학습
[[07-Deployment|배포와 인프라]] →
