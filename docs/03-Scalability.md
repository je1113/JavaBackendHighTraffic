# 확장성 설계

## 📖 개요
시스템의 부하가 증가할 때 유연하게 대응할 수 있는 확장 가능한 아키텍처 설계

## 🎯 학습 목표
- 수평적 확장(Scale-out) 전략 이해
- 효과적인 로드 밸런싱 구현
- 상태 관리 및 세션 클러스터링 구현

---

## 1. 수평적 확장 (Horizontal Scaling)

### 무상태(Stateless) 설계 원칙

#### 상태 외부화
```java
@RestController
@RequestMapping("/api/cart")
public class CartController {
    @Autowired
    private RedisTemplate<String, Cart> redisTemplate;
    
    // 세션 대신 Redis에 카트 정보 저장
    @PostMapping("/items")
    public Cart addToCart(@RequestHeader("X-Session-Id") String sessionId,
                         @RequestBody CartItem item) {
        String key = "cart:" + sessionId;
        Cart cart = redisTemplate.opsForValue().get(key);
        
        if (cart == null) {
            cart = new Cart();
        }
        
        cart.addItem(item);
        redisTemplate.opsForValue().set(key, cart, Duration.ofHours(2));
        
        return cart;
    }
}
```

#### JWT 기반 인증
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) {
        String token = extractToken(request);
        
        if (token != null && validateToken(token)) {
            // 토큰에서 사용자 정보 추출 (DB 조회 없음)
            Claims claims = Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody();
            
            SecurityContextHolder.getContext()
                .setAuthentication(createAuthentication(claims));
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### Auto Scaling 구현

#### Kubernetes HPA (Horizontal Pod Autoscaler)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: app-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend-app
  minReplicas: 3
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "1000"
```

---

## 2. 로드 밸런싱

### 로드 밸런싱 알고리즘

#### Spring Cloud LoadBalancer
```java
@Configuration
public class LoadBalancerConfig {
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public ReactorLoadBalancer<ServiceInstance> customLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        
        return new WeightedResponseTimeLoadBalancer(
            loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
            name
        );
    }
}
```

### 헬스 체크 구현
```java
@RestController
public class HealthController {
    @Autowired
    private DatabaseHealthIndicator dbHealth;
    
    @Autowired
    private RedisHealthIndicator redisHealth;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        
        // 깊은 헬스 체크
        if (dbHealth.health().getStatus() == Status.UP &&
            redisHealth.health().getStatus() == Status.UP) {
            status.put("status", "UP");
            return ResponseEntity.ok(status);
        }
        
        status.put("status", "DOWN");
        return ResponseEntity.status(503).body(status);
    }
    
    @GetMapping("/health/liveness")
    public ResponseEntity<String> liveness() {
        // 간단한 생존 확인
        return ResponseEntity.ok("OK");
    }
    
    @GetMapping("/health/readiness")
    public ResponseEntity<String> readiness() {
        // 트래픽 수신 준비 확인
        if (applicationContext.isActive()) {
            return ResponseEntity.ok("READY");
        }
        return ResponseEntity.status(503).body("NOT_READY");
    }
}
```

### 가중치 기반 라우팅
```java
@Component
public class WeightedLoadBalancer {
    private final Map<String, Integer> serverWeights = new HashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);
    
    public WeightedLoadBalancer() {
        // 서버별 가중치 설정
        serverWeights.put("server1", 3);  // 30%
        serverWeights.put("server2", 3);  // 30%
        serverWeights.put("server3", 4);  // 40%
    }
    
    public String selectServer() {
        int totalWeight = serverWeights.values().stream()
            .mapToInt(Integer::intValue).sum();
        int position = counter.incrementAndGet() % totalWeight;
        
        int currentWeight = 0;
        for (Map.Entry<String, Integer> entry : serverWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (position < currentWeight) {
                return entry.getKey();
            }
        }
        
        return serverWeights.keySet().iterator().next();
    }
}
```

---

## 3. 세션 관리

### Redis 세션 클러스터링
```java
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class SessionConfig {
    @Bean
    public LettuceConnectionFactory connectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("redis-server", 6379)
        );
    }
    
    @Bean
    public HttpSessionIdResolver httpSessionIdResolver() {
        // 헤더 기반 세션 ID 전달
        return HeaderHttpSessionIdResolver.xAuthToken();
    }
}
```

### Hazelcast 분산 캐시
```java
@Configuration
public class HazelcastConfig {
    @Bean
    public Config hazelcastConfig() {
        Config config = new Config();
        config.setClusterName("session-cluster");
        
        // 네트워크 설정
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(5701).setPortAutoIncrement(true);
        
        // Kubernetes 환경에서 자동 디스커버리
        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getKubernetesConfig()
            .setEnabled(true)
            .setProperty("namespace", "default")
            .setProperty("service-name", "hazelcast-service");
        
        // 분산 맵 설정
        MapConfig sessionMapConfig = new MapConfig();
        sessionMapConfig.setName("sessions")
            .setMaxSizeConfig(new MaxSizeConfig(10000, MaxSizeConfig.MaxSizePolicy.PER_NODE))
            .setEvictionConfig(new EvictionConfig()
                .setEvictionPolicy(EvictionPolicy.LRU)
                .setMaxSizePolicy(MaxSizePolicy.PER_NODE))
            .setTimeToLiveSeconds(3600);
        
        config.addMapConfig(sessionMapConfig);
        
        return config;
    }
}
```

---

## 4. 데이터베이스 확장

### 읽기 복제본 (Read Replica)
```java
@Configuration
public class DataSourceConfig {
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.slave")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    @Bean
    public DataSource routingDataSource() {
        ReplicationRoutingDataSource routingDataSource = 
            new ReplicationRoutingDataSource();
        
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource());
        dataSourceMap.put("slave", slaveDataSource());
        
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource());
        
        return routingDataSource;
    }
}

// 읽기/쓰기 라우팅
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() 
            ? "slave" : "master";
    }
}
```

### 샤딩 (Sharding)
```java
@Component
public class ShardingStrategy {
    private static final int SHARD_COUNT = 4;
    
    public int getShardId(Long userId) {
        // 일관된 해싱을 사용한 샤드 결정
        return Math.abs(userId.hashCode()) % SHARD_COUNT;
    }
    
    @Bean
    public DataSource shardedDataSource() {
        ShardingRuleConfiguration shardingRuleConfig = 
            new ShardingRuleConfiguration();
        
        // 테이블 샤딩 규칙
        TableRuleConfiguration orderTableRule = new TableRuleConfiguration(
            "orders", "ds${0..3}.orders"
        );
        
        orderTableRule.setDatabaseShardingStrategyConfig(
            new InlineShardingStrategyConfiguration(
                "user_id", "ds${user_id % 4}"
            )
        );
        
        shardingRuleConfig.getTableRuleConfigs().add(orderTableRule);
        
        return ShardingDataSourceFactory.createDataSource(
            createDataSourceMap(), shardingRuleConfig, new Properties()
        );
    }
}
```

---

## 5. 상태 동기화

### 분산 이벤트 버스
```java
@Component
public class DistributedEventBus {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void publishEvent(String topic, Object event) {
        redisTemplate.convertAndSend(topic, event);
    }
    
    @Component
    public static class EventListener {
        @EventListener
        public void handleCacheInvalidation(CacheInvalidationEvent event) {
            // 모든 인스턴스에서 캐시 무효화
            cacheManager.getCache(event.getCacheName())
                .evict(event.getKey());
        }
    }
}
```

---

## 📚 참고 자료
- [Designing Distributed Systems - Brendan Burns]
- [Site Reliability Engineering - Google]
- [Kubernetes Patterns - Bilgin Ibryam]

## ✅ 체크포인트
- [ ] 무상태 API 설계 및 구현
- [ ] Kubernetes HPA 설정
- [ ] Redis 세션 클러스터링 구현
- [ ] 읽기/쓰기 분리 구현
- [ ] 샤딩 전략 설계

## 🔗 다음 학습
[[04-Concurrency|동시성 처리]] →
