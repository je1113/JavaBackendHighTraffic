# 시스템 아키텍처 패턴

## 📖 개요
대규모 트래픽을 처리하기 위한 시스템 아키텍처 설계 패턴과 원칙

## 🎯 학습 목표
- 마이크로서비스 아키텍처의 장단점 이해
- 이벤트 기반 아키텍처 설계 능력
- 분산 시스템의 핵심 개념 습득

---

## 1. 마이크로서비스 아키텍처

### 핵심 개념
- **서비스 분해**: 비즈니스 도메인 기반 서비스 분리
- **독립적 배포**: 각 서비스의 독립적인 생명주기
- **기술 다양성**: 서비스별 최적 기술 스택 선택

### 주요 패턴
```yaml
API Gateway Pattern:
  - 단일 진입점
  - 인증/인가 중앙화
  - 라우팅 및 로드밸런싱
  
Service Discovery:
  - Eureka
  - Consul
  - Kubernetes Service

Circuit Breaker:
  - Hystrix
  - Resilience4j
  - 장애 전파 방지
```

### 실습 예제
```java
// Spring Cloud Gateway 설정
@Configuration
public class GatewayConfig {
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("user-service", r -> r.path("/api/users/**")
                .filters(f -> f.circuitBreaker(c -> c.setName("userServiceCB")))
                .uri("lb://USER-SERVICE"))
            .build();
    }
}
```

---

## 2. 이벤트 기반 아키텍처

### Event Sourcing
- 모든 상태 변경을 이벤트로 저장
- 감사 추적 용이
- 시점별 상태 복원 가능

### CQRS (Command Query Responsibility Segregation)
- 명령과 조회 분리
- 읽기 최적화 모델
- 최종 일관성

### 메시지 브로커
#### Apache Kafka
```java
@Component
public class OrderEventProducer {
    @Autowired
    private KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public void publishOrderCreated(Order order) {
        OrderEvent event = new OrderEvent(order.getId(), "CREATED", order);
        kafkaTemplate.send("order-events", event);
    }
}
```

#### RabbitMQ
- Topic Exchange
- Direct Exchange
- Fanout Exchange

---

## 3. 분산 시스템 설계 원칙

### CAP 이론
- **Consistency**: 모든 노드가 동일한 데이터 보유
- **Availability**: 시스템 항상 응답
- **Partition Tolerance**: 네트워크 분할 허용

### 분산 트랜잭션
- **2PC (Two-Phase Commit)**
- **Saga Pattern**
  - Choreography Saga
  - Orchestration Saga

### 예제: Saga Pattern 구현
```java
@Component
public class OrderSaga {
    @Autowired
    private PaymentService paymentService;
    
    @Autowired
    private InventoryService inventoryService;
    
    @Transactional
    public void processOrder(Order order) {
        try {
            // 1. 재고 예약
            inventoryService.reserveItems(order.getItems());
            
            // 2. 결제 처리
            paymentService.processPayment(order.getPayment());
            
            // 3. 주문 확정
            order.confirm();
        } catch (Exception e) {
            // 보상 트랜잭션
            compensateOrder(order);
        }
    }
}
```

---

## 4. 실전 고려사항

### 서비스 간 통신
- **동기 통신**: REST, gRPC
- **비동기 통신**: Message Queue, Event Stream

### 데이터 일관성
- **강한 일관성**: 분산 락, 2PC
- **최종 일관성**: Event Sourcing, CQRS

### 모니터링
- 분산 추적 (Distributed Tracing)
- 중앙집중식 로깅
- 서비스 메시 관찰성

---

## 📚 참고 자료
- [Building Microservices - Sam Newman]
- [Designing Data-Intensive Applications - Martin Kleppmann]
- [Spring Cloud Documentation](https://spring.io/projects/spring-cloud)

## ✅ 체크포인트
- [ ] API Gateway 패턴 구현
- [ ] 서비스 디스커버리 설정
- [ ] Kafka를 이용한 이벤트 발행/구독
- [ ] Saga 패턴 구현
- [ ] 분산 추적 설정

## 🔗 다음 학습
[[02-Performance-Optimization|성능 최적화]] →
