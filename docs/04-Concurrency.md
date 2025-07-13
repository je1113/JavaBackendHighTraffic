# 동시성 처리

## 📖 개요
대규모 트래픽 환경에서 동시에 발생하는 요청들을 효율적으로 처리하기 위한 동시성 프로그래밍 기법

## 🎯 학습 목표
- Java의 동시성 프로그래밍 모델 이해
- 분산 환경에서의 동시성 제어 구현
- Reactive Programming 패러다임 활용

---

## 1. Java 동시성 기초

### Thread-Safe 컬렉션

#### ConcurrentHashMap
```java
@Component
public class RateLimiter {
    private final ConcurrentHashMap<String, AtomicInteger> requestCounts = 
        new ConcurrentHashMap<>();
    
    public boolean allowRequest(String clientId) {
        AtomicInteger count = requestCounts.computeIfAbsent(
            clientId, k -> new AtomicInteger(0)
        );
        
        // 분당 100개 요청 제한
        return count.incrementAndGet() <= 100;
    }
    
    @Scheduled(fixedRate = 60000) // 1분마다 초기화
    public void resetCounts() {
        requestCounts.clear();
    }
}
```

#### CopyOnWriteArrayList
```java
@Service
public class EventListenerRegistry {
    private final CopyOnWriteArrayList<EventListener> listeners = 
        new CopyOnWriteArrayList<>();
    
    public void registerListener(EventListener listener) {
        listeners.add(listener);
    }
    
    public void publishEvent(Event event) {
        // 안전한 반복
        listeners.forEach(listener -> {
            CompletableFuture.runAsync(() -> 
                listener.handleEvent(event)
            );
        });
    }
}
```

### CompletableFuture와 비동기 프로그래밍

#### 비동기 API 조합
```java
@Service
public class OrderService {
    @Autowired
    private UserService userService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private PaymentService paymentService;
    
    public CompletableFuture<Order> createOrder(OrderRequest request) {
        // 병렬 실행
        CompletableFuture<User> userFuture = 
            CompletableFuture.supplyAsync(() -> 
                userService.getUser(request.getUserId())
            );
        
        CompletableFuture<Boolean> inventoryFuture = 
            CompletableFuture.supplyAsync(() -> 
                inventoryService.checkAvailability(request.getItems())
            );
        
        // 조합 및 체인
        return userFuture.thenCombine(inventoryFuture, (user, available) -> {
            if (!available) {
                throw new OutOfStockException();
            }
            return new Order(user, request.getItems());
        })
        .thenCompose(order -> 
            paymentService.processPayment(order)
                .thenApply(payment -> {
                    order.setPayment(payment);
                    return order;
                })
        )
        .exceptionally(ex -> {
            log.error("Order creation failed", ex);
            throw new OrderCreationException(ex);
        });
    }
}
```

### Virtual Threads (Java 21+)
```java
@Configuration
public class VirtualThreadConfig {
    @Bean
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

@RestController
public class VirtualThreadController {
    @Autowired
    private ExecutorService virtualThreadExecutor;
    
    @GetMapping("/process")
    public CompletableFuture<String> process() {
        return CompletableFuture.supplyAsync(() -> {
            // Virtual Thread에서 실행
            try {
                // 블로킹 I/O도 효율적으로 처리
                Thread.sleep(1000);
                return externalApiCall();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }, virtualThreadExecutor);
    }
}
```

---

## 2. 분산 환경의 동시성

### 분산 락 (Distributed Lock)

#### Redis 기반 분산 락
```java
@Component
public class RedisDistributedLock {
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    public boolean acquireLock(String key, String value, long timeout) {
        return redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(timeout));
    }
    
    public void releaseLock(String key, String value) {
        String script = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del', KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";
        
        redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(key),
            value
        );
    }
}

// 사용 예시
@Service
public class InventoryService {
    @Autowired
    private RedisDistributedLock distributedLock;
    
    public boolean decreaseStock(Long productId, int quantity) {
        String lockKey = "lock:product:" + productId;
        String lockValue = UUID.randomUUID().toString();
        
        try {
            // 락 획득 시도 (최대 5초 대기)
            if (!distributedLock.acquireLock(lockKey, lockValue, 10)) {
                throw new LockAcquisitionException();
            }
            
            // 임계 영역
            Product product = productRepository.findById(productId);
            if (product.getStock() >= quantity) {
                product.setStock(product.getStock() - quantity);
                productRepository.save(product);
                return true;
            }
            return false;
            
        } finally {
            distributedLock.releaseLock(lockKey, lockValue);
        }
    }
}
```

#### Redisson을 이용한 고급 분산 락
```java
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://localhost:6379");
        return Redisson.create(config);
    }
}

@Service
public class DistributedService {
    @Autowired
    private RedissonClient redissonClient;
    
    public void processWithLock(String resourceId) {
        RLock lock = redissonClient.getLock("lock:" + resourceId);
        
        try {
            // 최대 10초 대기, 5초 후 자동 해제
            if (lock.tryLock(10, 5, TimeUnit.SECONDS)) {
                try {
                    // 임계 영역 처리
                    performCriticalOperation();
                } finally {
                    lock.unlock();
                }
            } else {
                throw new LockTimeoutException();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
```

### 낙관적/비관적 락킹

#### 낙관적 락 (Optimistic Locking)
```java
@Entity
public class Product {
    @Id
    private Long id;
    
    @Version
    private Long version;
    
    private String name;
    private Integer stock;
}

@Service
@Transactional
public class OptimisticLockService {
    @Retryable(
        value = {OptimisticLockingFailureException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100)
    )
    public void updateStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
            .orElseThrow();
        
        product.setStock(product.getStock() - quantity);
        productRepository.save(product); // 버전 충돌 시 예외 발생
    }
}
```

#### 비관적 락 (Pessimistic Locking)
```java
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);
}
```

---

## 3. Reactive Programming

### Spring WebFlux
```java
@RestController
@RequestMapping("/api/reactive")
public class ReactiveController {
    @Autowired
    private ReactiveUserService userService;
    
    @GetMapping(value = "/users", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<User> streamUsers() {
        return userService.findAll()
            .delayElements(Duration.ofSeconds(1));
    }
    
    @GetMapping("/user/{id}")
    public Mono<ResponseEntity<User>> getUser(@PathVariable String id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
```

### Reactive 데이터베이스 액세스
```java
@Configuration
@EnableR2dbcRepositories
public class R2dbcConfig {
    @Bean
    public ConnectionFactory connectionFactory() {
        return ConnectionFactories.get(
            "r2dbc:postgresql://localhost:5432/database"
        );
    }
}

@Repository
public interface ReactiveUserRepository 
    extends ReactiveCrudRepository<User, Long> {
    
    @Query("SELECT * FROM users WHERE status = :status")
    Flux<User> findByStatus(String status);
}

@Service
public class ReactiveUserService {
    @Autowired
    private ReactiveUserRepository repository;
    
    public Flux<User> processUsers() {
        return repository.findByStatus("ACTIVE")
            .parallel()
            .runOn(Schedulers.parallel())
            .map(this::enrichUser)
            .sequential()
            .onErrorResume(error -> {
                log.error("Error processing user", error);
                return Flux.empty();
            });
    }
}
```

### 백프레셔 (Backpressure) 처리
```java
@Component
public class DataProcessor {
    public Flux<ProcessedData> processDataStream(Flux<RawData> input) {
        return input
            .onBackpressureBuffer(1000, // 버퍼 크기
                dropped -> log.warn("Dropped: {}", dropped))
            .flatMap(data -> 
                Mono.fromCallable(() -> processData(data))
                    .subscribeOn(Schedulers.boundedElastic()),
                10 // 동시 처리 수 제한
            )
            .retry(3)
            .doOnError(error -> log.error("Processing failed", error));
    }
    
    // 적응형 배치 처리
    public Flux<List<Result>> adaptiveBatchProcess(Flux<Item> items) {
        return items
            .window(Duration.ofSeconds(1), 100) // 1초 또는 100개
            .flatMap(window -> 
                window.collectList()
                    .filter(list -> !list.isEmpty())
                    .map(this::batchProcess)
            );
    }
}
```

---

## 4. 동시성 패턴

### Producer-Consumer 패턴
```java
@Component
public class AsyncTaskProcessor {
    private final BlockingQueue<Task> taskQueue = 
        new LinkedBlockingQueue<>(10000);
    
    @PostConstruct
    public void startConsumers() {
        int consumerCount = Runtime.getRuntime().availableProcessors();
        
        for (int i = 0; i < consumerCount; i++) {
            CompletableFuture.runAsync(this::consumeTasks);
        }
    }
    
    public void submitTask(Task task) {
        if (!taskQueue.offer(task)) {
            // 큐가 가득 찬 경우 처리
            throw new TaskQueueFullException();
        }
    }
    
    private void consumeTasks() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = taskQueue.take();
                processTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

### Bulkhead 패턴
```java
@Configuration
public class BulkheadConfig {
    @Bean
    public ThreadPoolBulkhead userServiceBulkhead() {
        return ThreadPoolBulkhead.of("userService",
            ThreadPoolBulkheadConfig.custom()
                .maxThreadPoolSize(10)
                .coreThreadPoolSize(5)
                .queueCapacity(100)
                .build()
        );
    }
}

@Service
public class ResilientService {
    @Autowired
    private ThreadPoolBulkhead userServiceBulkhead;
    
    public CompletableFuture<User> getUser(Long userId) {
        return Decorators.ofSupplier(() -> userRepository.findById(userId))
            .withThreadPoolBulkhead(userServiceBulkhead)
            .withTimeLimiter(timeLimiter)
            .withFallback(Arrays.asList(TimeoutException.class),
                e -> createDefaultUser())
            .get()
            .toCompletableFuture();
    }
}
```

---

## 📚 참고 자료
- [Java Concurrency in Practice - Brian Goetz]
- [Reactive Programming with Spring - Josh Long]
- [Project Loom Documentation](https://openjdk.org/projects/loom/)

## ✅ 체크포인트
- [ ] CompletableFuture를 이용한 비동기 처리
- [ ] Redis 분산 락 구현
- [ ] WebFlux 애플리케이션 구현
- [ ] Virtual Threads 활용
- [ ] 백프레셔 처리 구현

## 🔗 다음 학습
[[05-Monitoring|모니터링과 관찰성]] →
