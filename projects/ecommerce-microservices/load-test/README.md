# 재고 예약 부하 테스트

## 개요
이 모듈은 재고 관리 시스템의 동시성 처리 능력을 테스트하기 위한 부하 테스트 도구입니다.

### 테스트 시나리오
- **초기 재고**: 100개
- **동시 요청**: 5,000개 (1초 내)
- **예상 결과**: 
  - 100개 주문 성공 (재고 예약)
  - 4,900개 주문 실패 (재고 부족)

## 실행 방법

### 1. 인프라 준비
```bash
# Docker 인프라 시작
./docker/start-infrastructure.sh

# 서비스 실행
./gradlew :inventory-service:bootRun &
./gradlew :order-service:bootRun &
./gradlew :api-gateway:bootRun &
./gradlew :service-discovery:bootRun &
```

### 2. 테스트 데이터 초기화
```bash
cd load-test
./src/main/resources/scripts/init-test-data.sh
```

### 3. 부하 테스트 실행

#### Spring Boot 기반 테스트
```bash
./gradlew :load-test:runLoadTest
```

#### Gatling 시뮬레이션
```bash
# 기본 시뮬레이션 (5000 동시 요청)
./gradlew :load-test:gatlingRun -Dgatling.simulationClass=gatling.StockReservationSimulation

# 점진적 부하 증가 테스트
./gradlew :load-test:gatlingRun -Dgatling.simulationClass=gatling.StockReservationRampUpSimulation

# 동시성 정확도 테스트
./gradlew :load-test:gatlingRun -Dgatling.simulationClass=gatling.StockReservationConcurrencyTest
```

### 4. 결과 확인

#### Spring Boot 테스트 결과
콘솔에 다음과 같은 결과가 출력됩니다:
```
=== 부하 테스트 결과 ===
총 요청 수: 5000
성공: 100
실패: 4900
재고 예약 성공: 100
재고 부족: 4900
평균 응답 시간: XX.XXms
성공률: 2.00%
```

#### Gatling 결과
- HTML 리포트: `build/reports/gatling/*/index.html`
- 주요 메트릭:
  - Request/sec
  - Response time percentiles (P50, P95, P99)
  - Success/Failure rate

## 모니터링

### Grafana 대시보드
- URL: http://localhost:3000
- 주요 메트릭:
  - 락 획득 성공/실패율
  - 캐시 히트율
  - 응답 시간 분포
  - 재고 예약 처리량

### Prometheus 메트릭
- URL: http://localhost:9090
- 주요 쿼리:
```promql
# 락 획득 성공률
rate(lock_acquisition_success_total[1m])

# 재고 예약 성공률
rate(stock_reserved_total[1m])

# P99 응답 시간
histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[1m]))
```

## 설정 커스터마이징

`application.yml`에서 테스트 파라미터 수정 가능:
```yaml
load-test:
  scenario:
    product-id: 550e8400-e29b-41d4-a716-446655440001
    initial-stock: 100         # 초기 재고
    total-requests: 5000       # 총 요청 수
    concurrent-users: 5000     # 동시 사용자 수
```

## 주요 관찰 포인트

1. **동시성 제어**
   - 분산 락이 정확히 100개의 요청만 성공시키는지
   - 데드락이 발생하지 않는지

2. **성능**
   - 평균 응답 시간
   - P99 레이턴시
   - 처리량 (TPS)

3. **시스템 안정성**
   - 메모리 사용량
   - CPU 사용률
   - 에러율

## 트러블슈팅

### 서비스 연결 실패
```bash
# 서비스 상태 확인
curl http://localhost:8082/actuator/health
curl http://localhost:8081/actuator/health
```

### 재고 초기화 실패
```bash
# 수동으로 재고 조정
curl -X POST http://localhost:8082/api/v1/inventory/products/{productId}/stock/adjust \
  -H "Content-Type: application/json" \
  -d '{"newTotalQuantity": 100, "reason": "Manual reset"}'
```

### Gatling 실행 오류
```bash
# Gatling 플러그인 재설치
./gradlew clean build
```