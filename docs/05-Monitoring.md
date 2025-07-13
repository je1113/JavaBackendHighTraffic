
# 모니터링과 관찰성

## 📖 개요
대규모 시스템의 상태를 실시간으로 파악하고 문제를 신속하게 진단하기 위한 모니터링 및 관찰성 구현

## 🎯 학습 목표
- 효과적인 메트릭 수집 및 시각화
- 분산 추적 시스템 구축
- 로그 집계 및 분석 체계 구축

---

## 1. 메트릭 수집과 모니터링

### Micrometer를 이용한 메트릭 수집

#### 기본 메트릭 설정
```java
@Configuration
public class MetricsConfig {
    @Bean
    public MeterRegistry meterRegistry() {
        return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

@RestController
public class OrderController {
    private final MeterRegistry meterRegistry;
    private final Counter orderCounter;
    private final Timer orderTimer;
    
    public OrderController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.orderCounter = Counter.builder(\"orders.created\")
            .description(\"Total orders created\")
            .tag(\"type\", \"web\")
            .register(meterRegistry);
        
        this.orderTimer = Timer.builder(\"order.processing.time\")
            .description(\"Order processing time\")
            .register(meterRegistry);
    }
    
    @PostMapping(\"/orders\")
    public Order createOrder(@RequestBody OrderRequest request) {
        return orderTimer.recordCallable(() -> {
            Order order = orderService.createOrder(request);
            orderCounter.increment();
            
            // 커스텀 게이지
            meterRegistry.gauge(\"orders.pending\", 
                orderService.getPendingOrderCount());
            
            return order;
        });
    }
}
```

#### 비즈니스 메트릭
```java
@Component
public class BusinessMetrics {
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedDelay = 60000) // 1분마다
    public void recordBusinessMetrics() {
        // 수익 메트릭
        Gauge.builder(\"revenue.total\", revenueService, 
            RevenueService::getTotalRevenue)
            .tag(\"currency\", \"USD\")
            .register(meterRegistry);
        
        // 변환율 메트릭
        meterRegistry.gauge(\"conversion.rate\",
            Tags.of(\"funnel\", \"checkout\"),
            conversionService.getCheckoutConversionRate());
        
        // 사용자 활동 메트릭
        meterRegistry.gauge(\"users.active.count\",
            userService.getActiveUserCount());
    }
    
    @EventListener
    public void handleOrderEvent(OrderEvent event) {
        // 이벤트 기반 메트릭
        meterRegistry.counter(\"order.events\",
            \"type\", event.getType(),
            \"status\", event.getStatus()
        ).increment();
    }
}
```

### Prometheus & Grafana 설정

#### Prometheus 설정 (prometheus.yml)
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['app1:8080', 'app2:8080', 'app3:8080']
    
  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']
      
  - job_name: 'jmx-exporter'
    static_configs:
      - targets: ['jmx-exporter:9404']

# 알림 규칙
rule_files:
  - 'alerts.yml'

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

#### 알림 규칙 (alerts.yml)
```yaml
groups:
  - name: application
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: \"High error rate detected\"
          description: \"Error rate is {{ $value }} requests/sec\"
      
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes{area=\"heap\"} / jvm_memory_max_bytes{area=\"heap\"} > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: \"High heap memory usage\"
          description: \"Heap usage is {{ $value | humanizePercentage }}\"
```

---

## 2. 분산 추적 (Distributed Tracing)

### Spring Cloud Sleuth + Zipkin

#### Sleuth 설정
```java
@Configuration
public class TracingConfig {
    @Bean
    public Sampler defaultSampler() {
        return Sampler.ALWAYS_SAMPLE; // 프로덕션에서는 확률적 샘플링 사용
    }
    
    @Bean
    public SpanHandler zipkinSpanHandler() {
        return ZipkinSpanHandler.newBuilder(sender())
            .build();
    }
    
    @Bean
    public Sender sender() {
        return KafkaSender.newBuilder()
            .bootstrapServers(\"kafka:9092\")
            .topic(\"zipkin\")
            .build();
    }
}

// 커스텀 Span 생성
@Service
public class PaymentService {
    @Autowired
    private Tracer tracer;
    
    public Payment processPayment(Order order) {
        Span span = tracer.nextSpan()
            .name(\"payment-processing\")
            .tag(\"order.id\", order.getId())
            .tag(\"amount\", order.getTotalAmount().toString())
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // 외부 결제 게이트웨이 호출
            Payment payment = paymentGateway.charge(order);
            
            span.tag(\"payment.status\", payment.getStatus());
            span.event(\"payment.completed\");
            
            return payment;
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### OpenTelemetry 통합
```java
@Configuration
public class OpenTelemetryConfig {
    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
            .merge(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, \"order-service\",
                ResourceAttributes.SERVICE_VERSION, \"1.0.0\"
            )));
        
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(
                OtlpGrpcSpanExporter.builder()
                    .setEndpoint(\"http://collector:4317\")
                    .build()
            ).build())
            .setResource(resource)
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}
```

---

## 3. 로그 관리

### 구조화된 로깅

#### Logback JSON 설정
```xml
<configuration>
    <appender name=\"JSON\" class=\"ch.qos.logback.core.ConsoleAppender\">
        <encoder class=\"net.logstash.logback.encoder.LogstashEncoder\">
            <providers>
                <timestamp>
                    <timeZone>UTC</timeZone>
                </timestamp>
                <pattern>
                    <pattern>
                        {
                            \"app\": \"order-service\",
                            \"env\": \"${ENVIRONMENT}\",
                            \"trace_id\": \"%X{traceId}\",
                            \"span_id\": \"%X{spanId}\",
                            \"user_id\": \"%X{userId}\"
                        }
                    </pattern>
                </pattern>
                <stackTrace>
                    <throwableConverter class=\"net.logstash.logback.stacktrace.ShortenedThrowableConverter\">
                        <maxDepthPerThrowable>30</maxDepthPerThrowable>
                        <maxLength>2048</maxLength>
                    </throwableConverter>
                </stackTrace>
            </providers>
        </encoder>
    </appender>
    
    <root level=\"INFO\">
        <appender-ref ref=\"JSON\"/>
    </root>
</configuration>
```

#### MDC를 활용한 컨텍스트 로깅
```java
@Component
public class LoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) {
        try {
            // 요청 컨텍스트 정보 MDC에 추가
            MDC.put(\"request_id\", UUID.randomUUID().toString());
            MDC.put(\"user_id\", extractUserId(request));
            MDC.put(\"client_ip\", request.getRemoteAddr());
            MDC.put(\"request_path\", request.getRequestURI());
            
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

@Slf4j
@Service
public class OrderService {
    public Order createOrder(OrderRequest request) {
        log.info(\"Creating order for user\", 
            kv(\"product_ids\", request.getProductIds()),
            kv(\"total_amount\", request.getTotalAmount()));
        
        try {
            Order order = processOrder(request);
            log.info(\"Order created successfully\",
                kv(\"order_id\", order.getId()),
                kv(\"status\", order.getStatus()));
            return order;
        } catch (Exception e) {
            log.error(\"Order creation failed\",
                kv(\"error_type\", e.getClass().getSimpleName()),
                kv(\"error_message\", e.getMessage()), e);
            throw e;
        }
    }
}
```

### ELK Stack 설정

#### Logstash Pipeline
```ruby
input {
  kafka {
    bootstrap_servers => \"kafka:9092\"
    topics => [\"app-logs\"]
    codec => json
  }
}

filter {
  # 로그 레벨별 분류
  if [level] == \"ERROR\" {
    mutate {
      add_tag => [\"error\"]
    }
  }
  
  # 지리적 정보 추가
  geoip {
    source => \"client_ip\"
    target => \"geoip\"
  }
  
  # 메트릭 추출
  if [message] =~ /Order created/ {
    grok {
      match => { 
        \"message\" => \"Order created.*order_id=(?<order_id>[^ ]+).*total_amount=(?<total_amount>[0-9.]+)\"
      }
    }
    
    mutate {
      convert => { \"total_amount\" => \"float\" }
    }
  }
}

output {
  elasticsearch {
    hosts => [\"elasticsearch:9200\"]
    index => \"apps-%{app}-%{+YYYY.MM.dd}\"
  }
  
  # 에러 로그는 별도 처리
  if \"error\" in [tags] {
    email {
      to => \"ops-team@company.com\"
      subject => \"Error in %{app}\"
      body => \"%{message}\"
    }
  }
}
```

---

## 4. APM (Application Performance Monitoring)

### Custom APM Integration
```java
@Component
@Aspect
public class PerformanceMonitoringAspect {
    private final MeterRegistry meterRegistry;
    
    @Around(\"@annotation(monitorPerformance)\")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint,
                                   MonitorPerformance monitorPerformance) throws Throwable {
        String methodName = joinPoint.getSignature().toShortString();
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            Object result = joinPoint.proceed();
            
            sample.stop(Timer.builder(monitorPerformance.value())
                .description(monitorPerformance.description())
                .tag(\"method\", methodName)
                .tag(\"status\", \"success\")
                .register(meterRegistry));
            
            return result;
        } catch (Exception e) {
            sample.stop(Timer.builder(monitorPerformance.value())
                .tag(\"method\", methodName)
                .tag(\"status\", \"error\")
                .tag(\"exception\", e.getClass().getSimpleName())
                .register(meterRegistry));
            
            throw e;
        }
    }
}

// 사용 예시
@Service
public class DatabaseService {
    @MonitorPerformance(
        value = \"db.query.time\",
        description = \"Database query execution time\"
    )
    public List<User> findActiveUsers() {
        return userRepository.findByStatus(\"ACTIVE\");
    }
}
```

### 실시간 대시보드
```java
@RestController
@RequestMapping(\"/metrics\")
public class MetricsDashboardController {
    @Autowired
    private MeterRegistry meterRegistry;
    
    @GetMapping(value = \"/stream\", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<MetricsSnapshot> streamMetrics() {
        return Flux.interval(Duration.ofSeconds(1))
            .map(tick -> {
                MetricsSnapshot snapshot = new MetricsSnapshot();
                
                // 현재 메트릭 수집
                snapshot.setRequestRate(
                    meterRegistry.counter(\"http.requests\").count()
                );
                
                snapshot.setErrorRate(
                    meterRegistry.counter(\"http.errors\").count()
                );
                
                snapshot.setActiveConnections(
                    meterRegistry.gauge(\"connections.active\").value()
                );
                
                snapshot.setHeapUsage(
                    meterRegistry.gauge(\"jvm.memory.heap.used\").value() /
                    meterRegistry.gauge(\"jvm.memory.heap.max\").value()
                );
                
                return snapshot;
            });
    }
}
```

---

## 5. 알림 및 인시던트 관리

### AlertManager 통합
```java
@Component
public class AlertingService {
    @Autowired
    private RestTemplate restTemplate;
    
    @Value(\"${alertmanager.url}\")
    private String alertManagerUrl;
    
    public void sendAlert(Alert alert) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        List<Alert> alerts = Collections.singletonList(alert);
        HttpEntity<List<Alert>> request = new HttpEntity<>(alerts, headers);
        
        restTemplate.postForObject(
            alertManagerUrl + "/api/v1/alerts",
            request,
            String.class
        );
    }
    
    @EventListener
    public void handleCriticalEvent(CriticalEvent event) {
        Alert alert = Alert.builder()
            .labels(Map.of(
                "alertname", event.getType(),
                "severity", "critical",
                "service", "order-service"
            ))
            .annotations(Map.of(
                "summary", event.getSummary(),
                "description", event.getDescription()
            ))
            .startsAt(Instant.now())
            .endsAt(Instant.now().plus(Duration.ofHours(1)))
            .build();
        
        sendAlert(alert);
    }
}
```

### 인시던트 자동화
```java
@Component
public class IncidentManager {
    @Autowired
    private PagerDutyClient pagerDutyClient;
    
    @Autowired
    private SlackClient slackClient;
    
    @EventListener
    public void handleHighErrorRate(HighErrorRateEvent event) {
        // PagerDuty 인시던트 생성
        Incident incident = pagerDutyClient.createIncident(
            IncidentRequest.builder()
                .title("High Error Rate: " + event.getErrorRate() + "%")
                .urgency(Urgency.HIGH)
                .service("order-service")
                .details(event.getDetails())
                .build()
        );
        
        // Slack 알림
        slackClient.postMessage(
            SlackMessage.builder()
                .channel("#incidents")
                .text("🚨 High error rate detected!")
                .attachments(List.of(
                    SlackAttachment.builder()
                        .color("danger")
                        .fields(List.of(
                            new SlackField("Error Rate", event.getErrorRate() + "%"),
                            new SlackField("Service", "order-service"),
                            new SlackField("Incident ID", incident.getId())
                        ))
                        .build()
                ))
                .build()
        );
        
        // 자동 복구 시도
        attemptAutoRecovery(event);
    }
    
    private void attemptAutoRecovery(HighErrorRateEvent event) {
        // Circuit breaker 활성화
        if (event.getErrorRate() > 50) {
            circuitBreakerRegistry.circuitBreaker("external-api")
                .transitionToOpenState();
            
            log.info("Circuit breaker opened due to high error rate");
        }
        
        // Auto-scaling 트리거
        if (event.getCause().contains("timeout")) {
            kubernetesClient.apps().deployments()
                .inNamespace("default")
                .withName("order-service")
                .scale(10); // Scale up
        }
    }
}
```

---

## 6. 성능 분석 도구

### 힙 덤프 분석
```java
@RestController
@RequestMapping("/admin")
public class DiagnosticsController {
    @PostMapping("/heap-dump")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> captureHeapDump() throws IOException {
        String fileName = "heapdump-" + System.currentTimeMillis() + ".hprof";
        File heapDumpFile = new File("/tmp/" + fileName);
        
        // 힙 덤프 생성
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            HotSpotDiagnosticMXBean.class
        );
        
        mxBean.dumpHeap(heapDumpFile.getAbsolutePath(), true);
        
        // S3에 업로드
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket("diagnostics")
                .key("heapdumps/" + fileName)
                .build(),
            heapDumpFile.toPath()
        );
        
        return ResponseEntity.ok()
            .header("X-Heap-Dump-Location", "s3://diagnostics/heapdumps/" + fileName)
            .build();
    }
}
```

### 커스텀 프로파일러
```java
@Component
public class MethodProfiler {
    private final ConcurrentHashMap<String, MethodStats> stats = 
        new ConcurrentHashMap<>();
    
    @Scheduled(fixedDelay = 60000)
    public void reportStats() {
        stats.forEach((method, stat) -> {
            log.info("Method performance stats",
                kv("method", method),
                kv("count", stat.getCount()),
                kv("avg_time_ms", stat.getAverageTime()),
                kv("max_time_ms", stat.getMaxTime()),
                kv("p99_time_ms", stat.getPercentile(99))
            );
        });
    }
    
    public <T> T profile(String methodName, Supplier<T> supplier) {
        long start = System.nanoTime();
        
        try {
            return supplier.get();
        } finally {
            long duration = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start
            );
            
            stats.computeIfAbsent(methodName, k -> new MethodStats())
                .record(duration);
        }
    }
}
```

---

## 7. 모니터링 Best Practices

### SLI/SLO 정의
```java
@Component
public class SLOMonitor {
    @Scheduled(fixedDelay = 60000)
    public void checkSLOs() {
        // 가용성 SLO: 99.9%
        double availability = calculateAvailability();
        if (availability < 0.999) {
            alertingService.sendAlert(
                Alert.builder()
                    .name("SLO_Availability_Breach")
                    .severity("high")
                    .value(availability)
                    .threshold(0.999)
                    .build()
            );
        }
        
        // 응답 시간 SLO: P95 < 200ms
        double p95Latency = getPercentileLatency(95);
        if (p95Latency > 200) {
            alertingService.sendAlert(
                Alert.builder()
                    .name("SLO_Latency_Breach")
                    .severity("medium")
                    .value(p95Latency)
                    .threshold(200)
                    .build()
            );
        }
    }
}
```

### 모니터링 체크리스트
```yaml
Infrastructure:
  - CPU, Memory, Disk, Network 사용률
  - Container/Pod 상태 및 재시작 횟수
  - 노드 상태 및 가용성

Application:
  - Request rate, Error rate, Duration (RED)
  - 비즈니스 메트릭 (주문, 결제, 가입 등)
  - 외부 의존성 상태

Database:
  - 연결 풀 사용률
  - 쿼리 응답 시간
  - 복제 지연

Message Queue:
  - 큐 깊이 및 처리 지연
  - Consumer lag
  - 메시지 처리 실패율
```

---

## 📚 참고 자료
- [Distributed Systems Observability - Cindy Sridharan]
- [Site Reliability Engineering - Google]
- [Prometheus: Up & Running - Brian Brazil]

## ✅ 체크포인트
- [ ] Prometheus + Grafana 대시보드 구성
- [ ] 분산 추적 시스템 구현
- [ ] 구조화된 로깅 및 로그 집계
- [ ] 커스텀 메트릭 및 비즈니스 KPI 모니터링
- [ ] 알림 규칙 및 인시던트 대응 자동화

## 🔗 다음 학습
[[06-Resilience|복원력과 안정성]] →
