# Docker Compose 환경에서 부하 테스트 모니터링 가이드

## 🚀 시작하기

### 1. 서비스 시작
```bash
# 전체 서비스 시작
./docker-start.sh

# 또는 개별적으로 시작
docker-compose up -d
```

### 2. 서비스 포트 매핑
- **API Gateway**: `localhost:8888` (기존 8080에서 변경)
- **Order Service**: `localhost:8081`
- **Inventory Service**: `localhost:8082`
- **Service Discovery (Eureka)**: `localhost:8761`

## 📊 실시간 모니터링 방법

### 1. Docker 로그 모니터링
```bash
# 모든 서비스 로그
docker-compose logs -f

# 특정 서비스 로그
docker-compose logs -f inventory-service
docker-compose logs -f order-service

# 여러 서비스 동시 모니터링
docker-compose logs -f inventory-service order-service
```

### 2. 서비스 상태 확인
```bash
# 서비스 상태 확인
docker-compose ps

# 자원 사용량 모니터링
docker stats
```

### 3. Prometheus 메트릭 (http://localhost:9090)
```promql
# 요청 처리량
rate(http_server_requests_seconds_count[1m])

# 응답 시간
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# 재고 예약 성공률
rate(inventory_reservation_success_total[1m])
```

### 4. Grafana 대시보드 (http://localhost:3000)
- Username: `admin`
- Password: `admin123!`

#### 주요 대시보드
1. **Application Metrics**: 요청 처리량, 응답 시간
2. **JVM Metrics**: 메모리 사용량, GC 활동
3. **Business Metrics**: 주문 수, 재고 현황

### 5. Kafka UI (http://localhost:8090)
- 이벤트 플로우 모니터링
- 토픽별 메시지 확인
- Consumer lag 모니터링

## 🧪 부하 테스트 실행

### 1. API Gateway를 통한 테스트
```bash
# 재고 조회
curl http://localhost:8888/api/inventory/products/550e8400-e29b-41d4-a716-446655440001/stock

# 주문 생성
curl -X POST http://localhost:8888/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [{
      "productId": "550e8400-e29b-41d4-a716-446655440001",
      "quantity": 1,
      "unitPrice": 10000
    }]
  }'
```

### 2. JMeter 또는 K6를 사용한 부하 테스트
```javascript
// k6 스크립트 예제
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  vus: 200,        // 200 가상 사용자
  duration: '30s', // 30초 동안
};

export default function() {
  let response = http.post('http://localhost:8888/api/orders', JSON.stringify({
    customerId: "550e8400-e29b-41d4-a716-446655440000",
    items: [{
      productId: "550e8400-e29b-41d4-a716-446655440001",
      quantity: 1,
      unitPrice: 10000
    }]
  }), {
    headers: { 'Content-Type': 'application/json' },
  });
  
  check(response, {
    'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
  });
}
```

## 📈 모니터링 포인트

### 1. 동시성 제어 확인
- Redis 분산 락 모니터링
- 재고 일관성 확인
- Race condition 발생 여부

### 2. 성능 지표
- **TPS**: 초당 트랜잭션 수
- **응답 시간**: P50, P95, P99
- **에러율**: 5xx 에러 비율

### 3. 시스템 리소스
- **CPU 사용률**: 각 서비스별
- **메모리 사용량**: Heap 메모리
- **네트워크 I/O**: 서비스 간 통신량

## 🔍 문제 해결

### 1. 서비스가 시작되지 않을 때
```bash
# 로그 확인
docker-compose logs service-name

# 헬스체크 상태
docker inspect container-name | grep -A 10 Health

# 포트 충돌 확인
netstat -tulpn | grep 포트번호
```

### 2. 데이터베이스 연결 문제
```bash
# PostgreSQL 접속 테스트
docker exec -it ecommerce-postgres psql -U postgres

# 데이터베이스 목록 확인
\l

# 사용자 확인
\du
```

### 3. Kafka 이벤트 확인
```bash
# Kafka 토픽 목록
docker exec -it ecommerce-kafka kafka-topics --list --bootstrap-server localhost:9092

# 이벤트 소비
docker exec -it ecommerce-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning
```

## 📊 예상 결과

### 정상 동작 시
- 재고 100개에 대해 정확히 100개의 주문만 성공
- 나머지 요청은 409 Conflict 응답
- 데이터베이스에 음수 재고 없음

### 모니터링 화면 예시
```
[Grafana Dashboard]
- Request Rate: 4,500 req/s
- Success Rate: 2% (100/5000)
- P95 Latency: 150ms
- Active Locks: 10-20
- Cache Hit Rate: 85%
```

## 🛑 종료 방법
```bash
# 모든 서비스 종료
docker-compose down

# 볼륨까지 삭제 (데이터 초기화)
docker-compose down -v
```

---

이제 Docker Compose로 전체 시스템을 실행하고 실시간으로 모니터링할 수 있습니다!