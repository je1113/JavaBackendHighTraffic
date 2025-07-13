# 성능 최적화

## 📖 개요
Java 애플리케이션의 성능을 최적화하기 위한 JVM 튜닝, 데이터베이스 최적화, 캐싱 전략

## 🎯 학습 목표
- JVM 메모리 구조와 GC 알고리즘 이해
- 데이터베이스 쿼리 최적화 기법 습득
- 효과적인 캐싱 전략 수립

---

## 1. JVM 튜닝

### JVM 메모리 구조
```
Heap Memory
├── Young Generation
│   ├── Eden Space
│   ├── Survivor Space 0
│   └── Survivor Space 1
└── Old Generation

Non-Heap Memory
├── Metaspace (Java 8+)
├── Code Cache
└── Direct Memory
```

### Garbage Collection 알고리즘

#### G1GC (Default in Java 9+)
```bash
# G1GC 설정 예시
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:InitiatingHeapOccupancyPercent=45
```

#### ZGC (Low Latency GC)
```bash
# ZGC 설정 (Java 15+)
-XX:+UseZGC
-XX:ZCollectionInterval=120
-XX:ZAllocationSpikeTolerance=5
```

### JVM 모니터링
```java
// JVM 메트릭 수집
@Component
public class JvmMetricsCollector {
    private final MeterRegistry meterRegistry;
    
    @PostConstruct
    public void init() {
        // Heap 메모리 모니터링
        Gauge.builder("jvm.memory.heap.used", () -> {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            return memoryBean.getHeapMemoryUsage().getUsed();
        }).register(meterRegistry);
        
        // GC 모니터링
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            Gauge.builder("jvm.gc.count", gcBean, GarbageCollectorMXBean::getCollectionCount)
                .tag("gc", gcBean.getName())
                .register(meterRegistry);
        }
    }
}
```

---

## 2. 데이터베이스 최적화

### 인덱싱 전략

#### 복합 인덱스 설계
```sql
-- 검색 조건 순서를 고려한 복합 인덱스
CREATE INDEX idx_user_status_created 
ON users(status, created_at) 
WHERE status = 'ACTIVE';

-- Covering Index
CREATE INDEX idx_order_covering 
ON orders(user_id, status, created_at) 
INCLUDE (total_amount, shipping_address);
```

### 쿼리 최적화

#### N+1 문제 해결
```java
// Fetch Join 사용
@Query("SELECT o FROM Order o " +
       "JOIN FETCH o.orderItems " +
       "WHERE o.userId = :userId")
List<Order> findOrdersWithItems(@Param("userId") Long userId);

// EntityGraph 사용
@EntityGraph(attributePaths = {"orderItems", "payment"})
List<Order> findByUserId(Long userId);
```

#### 벌크 연산
```java
@Modifying
@Query("UPDATE User u SET u.lastLoginAt = :now " +
       "WHERE u.id IN :userIds")
void updateLastLoginBulk(@Param("userIds") List<Long> userIds, 
                        @Param("now") LocalDateTime now);
```

### 읽기/쓰기 분리
```java
@Configuration
public class DataSourceConfig {
    @Bean
    @Primary
    public DataSource routingDataSource() {
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource());
        dataSourceMap.put("slave", slaveDataSource());
        
        RoutingDataSource routingDataSource = new RoutingDataSource();
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource());
        
        return routingDataSource;
    }
}
```

---

## 3. 캐싱 전략

### 로컬 캐시 (Caffeine)
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}

@Service
public class UserService {
    @Cacheable(value = "users", key = "#userId")
    public User getUser(Long userId) {
        return userRepository.findById(userId);
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public void updateUser(User user) {
        userRepository.save(user);
    }
}
```

### 분산 캐시 (Redis)
```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 직렬화 설정
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        return template;
    }
}

// 캐시 어사이드 패턴
@Service
public class ProductService {
    @Autowired
    private RedisTemplate<String, Product> redisTemplate;
    
    public Product getProduct(Long productId) {
        String key = "product:" + productId;
        
        // 캐시 확인
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        
        // DB 조회
        Product product = productRepository.findById(productId);
        
        // 캐시 저장
        redisTemplate.opsForValue().set(key, product, 
            Duration.ofMinutes(10));
        
        return product;
    }
}
```

### 캐시 워밍
```java
@Component
public class CacheWarmer {
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpCache() {
        // 자주 사용되는 데이터 미리 로드
        List<Product> popularProducts = productService.getPopularProducts();
        popularProducts.forEach(product -> 
            cacheManager.getCache("products")
                .put(product.getId(), product)
        );
    }
}
```

---

## 4. 성능 측정 및 프로파일링

### JMH (Java Microbenchmark Harness)
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class CacheBenchmark {
    @Benchmark
    public User testDirectDbAccess() {
        return userRepository.findById(1L);
    }
    
    @Benchmark
    public User testCachedAccess() {
        return userService.getUser(1L); // @Cacheable
    }
}
```

### APM 통합
```java
// Micrometer를 통한 커스텀 메트릭
@RestController
public class OrderController {
    private final MeterRegistry meterRegistry;
    
    @PostMapping("/orders")
    @Timed(value = "order.creation.time")
    public Order createOrder(@RequestBody OrderRequest request) {
        return Metrics.timer("order.processing.time")
            .record(() -> orderService.createOrder(request));
    }
}
```

---

## 📚 참고 자료
- [Java Performance: The Definitive Guide - Scott Oaks]
- [High Performance MySQL - Baron Schwartz]
- [JVM Performance Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/)

## ✅ 체크포인트
- [ ] JVM 힙 덤프 분석
- [ ] 슬로우 쿼리 최적화
- [ ] 캐시 히트율 모니터링 구현
- [ ] JMH 벤치마크 작성
- [ ] GC 로그 분석

## 🔗 다음 학습
[[03-Scalability|확장성 설계]] →
