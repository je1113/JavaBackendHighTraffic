# 📊 모니터링 도구 사용 가이드

## 개요
이 프로젝트는 Prometheus, Grafana, Zipkin을 사용하여 마이크로서비스의 메트릭, 로그, 분산 추적을 모니터링합니다. 이 가이드는 각 도구의 접속 방법과 주요 기능 사용법을 설명합니다.

## 🚀 모니터링 환경 시작

```bash
# 모든 인프라 시작 (모니터링 도구 포함)
./docker/start-infrastructure.sh

# 또는 Docker Compose로 직접 시작
docker-compose up -d prometheus grafana zipkin
```

## 📈 Grafana (메트릭 시각화)

### 접속 정보
- **URL**: http://localhost:3000
- **기본 계정**: admin / admin
- **첫 로그인 시 비밀번호 변경 필요**

### 주요 대시보드

#### 1. JVM 메트릭 대시보드
- **메모리 사용량**: Heap/Non-Heap 메모리 추적
- **GC 활동**: GC 횟수 및 소요 시간
- **스레드 상태**: 활성/대기 스레드 수
- **CPU 사용률**: 프로세스별 CPU 사용량

#### 2. 비즈니스 메트릭 대시보드
- **주문 처리율**: 분당 주문 생성/완료 수
- **재고 조회 성능**: 캐시 히트율, 응답 시간
- **결제 성공률**: 결제 성공/실패 비율
- **이벤트 발행 현황**: Kafka 이벤트 처리 통계

#### 3. API 게이트웨이 대시보드
- **요청 처리량**: 초당 요청 수 (RPS)
- **응답 시간 분포**: P50, P90, P99 레이턴시
- **에러율**: HTTP 상태 코드별 분포
- **Rate Limiting**: 클라이언트별 제한 현황

### 대시보드 추가 방법

1. **Prometheus 데이터 소스 설정**
   ```
   Configuration → Data Sources → Add data source
   - Type: Prometheus
   - URL: http://prometheus:9090
   - Access: Server (Default)
   ```

2. **커스텀 대시보드 생성**
   ```
   Create → Dashboard → Add new panel
   ```

3. **주요 쿼리 예시**
   ```promql
   # 주문 생성 속도
   rate(order_created_total[5m])
   
   # 캐시 히트율
   rate(cache_hits_total[5m]) / rate(cache_requests_total[5m])
   
   # API 응답 시간 (P99)
   histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))
   
   # Kafka 이벤트 발행 실패율
   rate(kafka_event_publish_failures_total[5m])
   ```

### 알림 설정

1. **Alerting → Alert rules → New alert rule**
2. **주요 알림 규칙**:
   - CPU 사용률 > 80%
   - 메모리 사용률 > 90%
   - API 에러율 > 5%
   - Kafka 이벤트 실패율 > 1%

## 🔍 Prometheus (메트릭 수집)

### 접속 정보
- **URL**: http://localhost:9090
- **인증**: 없음 (개발 환경)

### 주요 기능

#### 1. 메트릭 탐색
```
Graph → Insert metric at cursor
```

#### 2. 타겟 상태 확인
```
Status → Targets
```
- 모든 서비스의 스크래핑 상태 확인
- UP/DOWN 상태 모니터링

#### 3. 유용한 PromQL 쿼리

```promql
# 서비스별 메모리 사용량
jvm_memory_used_bytes{area="heap",service=~"inventory-service|order-service"}

# HTTP 요청 처리 시간 히스토그램
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri))

# 데이터베이스 연결 풀 상태
hikaricp_connections_active{pool="HikariPool-1"}

# Redis 연결 상태
redis_connected_clients

# Kafka 컨슈머 랙
kafka_consumer_lag_records
```

### 메트릭 스크래핑 설정
`prometheus/prometheus.yml`:
```yaml
scrape_configs:
  - job_name: 'inventory-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['inventory-service:8081']
      
  - job_name: 'order-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['order-service:8082']
```

## 🔗 Zipkin (분산 추적)

### 접속 정보
- **URL**: http://localhost:9411
- **인증**: 없음

### 주요 기능

#### 1. 트레이스 검색
- **Service Name**: 특정 서비스 선택
- **Span Name**: 특정 작업 필터링
- **Tags**: 커스텀 태그로 검색
  - `http.method=POST`
  - `error=true`
  - `customer.id=12345`

#### 2. 의존성 분석
```
Dependencies 탭 → 서비스 간 호출 관계 시각화
```

#### 3. 트레이스 분석
- **Timeline View**: 각 스팬의 시간 분포
- **Span Details**: 상세 메타데이터
- **Error Analysis**: 에러 발생 지점 추적

### 유용한 검색 팁

```
# 느린 요청 찾기
minDuration: 1000ms

# 에러 요청만 보기
Tags: error=true

# 특정 주문 ID 추적
Tags: order.id=550e8400-e29b-41d4-a716-446655440001

# 특정 API 엔드포인트
Span Name: POST /api/v1/orders
```

## 🎯 모니터링 시나리오

### 1. 주문 생성 플로우 모니터링

**Zipkin에서 트레이스 확인**:
1. Service: `api-gateway` 선택
2. Span Name: `POST /api/orders`
3. 트레이스 선택 → 전체 플로우 확인

**Grafana에서 메트릭 확인**:
- 주문 생성 성공률
- 평균 응답 시간
- 재고 예약 성공률

### 2. 성능 이슈 진단

**느린 API 찾기**:
1. Grafana → API 대시보드 → P99 레이턴시 확인
2. Zipkin → 해당 시간대 느린 트레이스 검색
3. 병목 구간 식별

**데이터베이스 성능**:
```promql
# 쿼리 실행 시간
histogram_quantile(0.99, rate(spring_data_repository_invocations_seconds_bucket[5m]))

# 커넥션 풀 대기 시간
hikaricp_connections_pending
```

### 3. 시스템 상태 모니터링

**서비스 헬스체크**:
```bash
# 개별 서비스 헬스 확인
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health

# Prometheus에서 UP 타겟 확인
up{job=~"inventory-service|order-service"}
```

## 📋 모니터링 체크리스트

### 일일 점검 사항
- [ ] 모든 서비스가 UP 상태인지 확인
- [ ] 에러율이 임계치 이하인지 확인
- [ ] 메모리/CPU 사용률 확인
- [ ] Kafka 컨슈머 랙 확인

### 주간 점검 사항
- [ ] 느린 쿼리 분석
- [ ] 캐시 히트율 최적화
- [ ] 알림 규칙 검토
- [ ] 대시보드 업데이트

### 트러블슈팅 가이드
1. **메트릭이 수집되지 않을 때**:
   - Prometheus targets 확인
   - 서비스 actuator 엔드포인트 확인
   - 네트워크 연결 확인

2. **트레이스가 보이지 않을 때**:
   - Zipkin 서비스 상태 확인
   - 애플리케이션 Sleuth 설정 확인
   - 샘플링 비율 확인

3. **Grafana 대시보드 에러**:
   - 데이터 소스 연결 테스트
   - PromQL 쿼리 문법 확인
   - 시간 범위 조정

## 🔧 고급 설정

### 커스텀 메트릭 추가
```java
// Micrometer 커스텀 메트릭 예시
@Component
public class CustomMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordOrderProcessingTime(long duration) {
        meterRegistry.timer("order.processing.time")
            .record(duration, TimeUnit.MILLISECONDS);
    }
}
```

### 분산 추적 커스텀 태그
```java
@Component
public class TracingConfig {
    @Bean
    public SpanCustomizer spanCustomizer(Tracer tracer) {
        return tracer.currentSpan()
            .tag("service.version", "1.0.0")
            .tag("environment", "development");
    }
}
```

## 📚 참고 자료

- [Prometheus 공식 문서](https://prometheus.io/docs/)
- [Grafana 튜토리얼](https://grafana.com/tutorials/)
- [Zipkin 문서](https://zipkin.io/pages/documentation.html)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer 문서](https://micrometer.io/docs)