# 주문 생애주기 다이어그램

## 1. 주문 상태 전이 다이어그램

```mermaid
stateDiagram-v2
    [*] --> PENDING: 주문 생성
    
    PENDING --> CONFIRMED: 재고 예약 성공
    PENDING --> CANCELLED: 고객 취소
    PENDING --> FAILED: 재고 부족
    
    CONFIRMED --> PAYMENT_PENDING: 결제 대기
    CONFIRMED --> CANCELLED: 고객 취소
    
    PAYMENT_PENDING --> PAYMENT_PROCESSING: 결제 시작
    PAYMENT_PENDING --> CANCELLED: 타임아웃/결제 포기
    PAYMENT_PENDING --> FAILED: 시스템 오류
    
    PAYMENT_PROCESSING --> PAID: 결제 성공
    PAYMENT_PROCESSING --> FAILED: 결제 실패
    PAYMENT_PROCESSING --> CANCELLED: 고객 취소
    
    PAID --> PREPARING: 상품 준비
    PAID --> REFUNDING: 환불 요청
    
    PREPARING --> SHIPPED: 배송 시작
    PREPARING --> CANCELLED: 특수 사유 취소
    
    SHIPPED --> DELIVERED: 배송 완료
    
    DELIVERED --> COMPLETED: 구매 확정
    DELIVERED --> REFUNDING: 반품 요청
    
    COMPLETED --> REFUNDING: 환불 요청
    
    REFUNDING --> REFUNDED: 환불 완료
    
    CANCELLED --> [*]: 주문 취소
    FAILED --> [*]: 주문 실패
    REFUNDED --> [*]: 환불 완료
    COMPLETED --> [*]: 정상 완료
    
    note right of PENDING
        초기 상태
        재고 확인 중
    end note
    
    note right of PAYMENT_PENDING
        결제 대기 중
        취소 불가능 상태
    end note
    
    note right of PAYMENT_PROCESSING
        결제 진행 중
        취소 가능 상태
    end note
    
    note right of PAID
        결제 완료
        환불 가능 시작
    end note
    
    note right of CANCELLED
        최종 상태
        추가 전이 불가
    end note
    
    note right of FAILED
        최종 상태
        비즈니스/기술 실패
    end note
    
    note right of REFUNDED
        최종 상태
        환불 완료
    end note
```

## 2. 주문 생성 및 재고 예약 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant Customer as 고객
    participant Gateway as API Gateway
    participant OrderService as 주문 서비스
    participant InventoryService as 재고 서비스
    participant PaymentService as 결제 서비스
    participant EventBus as 이벤트 버스 (Kafka)
    participant DB as 데이터베이스
    
    Customer->>Gateway: POST /orders (주문 요청)
    Gateway->>OrderService: 주문 생성 요청
    
    OrderService->>OrderService: 주문 유효성 검증
    OrderService->>DB: 주문 저장 (PENDING)
    OrderService->>EventBus: OrderCreatedEvent 발행
    OrderService-->>Customer: 주문 접수 응답 (주문 ID)
    
    EventBus->>InventoryService: OrderCreatedEvent 수신
    InventoryService->>InventoryService: 재고 확인
    
    alt 재고 충분
        InventoryService->>DB: 재고 예약 생성
        InventoryService->>EventBus: StockReservedEvent 발행
        
        EventBus->>OrderService: StockReservedEvent 수신
        OrderService->>DB: 주문 상태 업데이트 (STOCK_RESERVED)
        OrderService->>PaymentService: 결제 요청
        
    else 재고 부족
        InventoryService->>EventBus: InsufficientStockEvent 발행
        
        EventBus->>OrderService: InsufficientStockEvent 수신
        OrderService->>DB: 주문 상태 업데이트 (CANCELLED)
        OrderService->>Customer: 재고 부족 알림
    end
```

## 3. 주문 완료까지의 전체 흐름 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant C as 고객
    participant OS as 주문 서비스
    participant IS as 재고 서비스
    participant PS as 결제 서비스
    participant EB as 이벤트 버스
    
    rect rgb(200, 230, 255)
        note right of C: 1. 주문 생성 단계
        C->>OS: 주문 생성
        OS->>OS: 주문 저장 (PENDING)
        OS->>EB: OrderCreatedEvent
        OS-->>C: 주문 ID 반환
    end
    
    rect rgb(230, 255, 200)
        note right of IS: 2. 재고 예약 단계
        EB->>IS: OrderCreatedEvent
        IS->>IS: 재고 확인 & 예약
        IS->>EB: StockReservedEvent
        
        EB->>OS: StockReservedEvent
        OS->>OS: 상태 변경 (CONFIRMED)
    end
    
    rect rgb(255, 230, 200)
        note right of PS: 3. 결제 처리 단계
        OS->>OS: 상태 변경 (PAYMENT_PENDING)
        OS->>PS: 결제 요청
        OS->>OS: 상태 변경 (PAYMENT_PROCESSING)
        PS->>PS: 결제 처리
        PS->>EB: PaymentCompletedEvent
        
        EB->>OS: PaymentCompletedEvent
        OS->>OS: 상태 변경 (PAID)
        OS->>EB: OrderPaidEvent
    end
    
    rect rgb(255, 200, 230)
        note right of IS: 4. 재고 차감 단계
        EB->>IS: OrderPaidEvent
        IS->>IS: 예약 재고 -> 실제 차감
        IS->>EB: StockDeductedEvent
        
        EB->>OS: StockDeductedEvent
        OS->>OS: 상태 변경 (CONFIRMED)
        OS->>EB: OrderConfirmedEvent
    end
    
    rect rgb(200, 200, 255)
        note right of C: 5. 주문 완료 단계
        OS->>C: 주문 확정 알림
        OS->>OS: 배송 준비 (PREPARING)
        OS->>OS: 배송 시작 (SHIPPING)
        OS->>OS: 배송 완료 (DELIVERED)
        C->>OS: 구매 확정
        OS->>OS: 주문 완료 (COMPLETED)
        OS->>EB: OrderCompletedEvent
    end
```

## 4. 주문 취소 및 보상 트랜잭션 시퀀스 다이어그램

```mermaid
sequenceDiagram
    participant C as 고객
    participant OS as 주문 서비스
    participant IS as 재고 서비스
    participant PS as 결제 서비스
    participant EB as 이벤트 버스
    
    C->>OS: 주문 취소 요청
    OS->>OS: 취소 가능 여부 확인
    
    alt 취소 가능
        OS->>OS: 상태 변경 (CANCELLED)
        OS->>EB: OrderCancelledEvent
        OS-->>C: 취소 접수 응답
        
        par 재고 복원
            EB->>IS: OrderCancelledEvent
            IS->>IS: 예약/차감 재고 복원
            IS->>EB: StockReleasedEvent
        and 결제 취소
            EB->>PS: OrderCancelledEvent
            PS->>PS: 결제 취소/환불 처리
            PS->>EB: PaymentRefundedEvent
        end
        
        EB->>OS: StockReleasedEvent
        EB->>OS: PaymentRefundedEvent
        OS->>OS: 취소 완료 처리
        OS->>C: 취소 완료 알림
        
    else 취소 불가
        OS-->>C: 취소 불가 응답
    end
```

## 5. 이벤트 기반 아키텍처 장점

### 5.1 느슨한 결합 (Loose Coupling)
- 서비스 간 직접적인 의존성 제거
- 각 서비스는 이벤트를 통해서만 통신
- 서비스 독립적 배포 및 확장 가능

### 5.2 확장성 (Scalability)
- 각 서비스 독립적으로 스케일 아웃 가능
- 이벤트 버스를 통한 비동기 처리로 처리량 향상
- 부하가 높은 서비스만 선택적 확장

### 5.3 복원력 (Resilience)
- 일시적 장애에 대한 자동 재시도
- 서비스 장애 시 이벤트는 큐에 보관
- 장애 복구 후 자동으로 처리 재개

### 5.4 감사 추적 (Audit Trail)
- 모든 상태 변경이 이벤트로 기록
- 완벽한 감사 로그 제공
- 이벤트 소싱을 통한 상태 재구성 가능

## 6. 주요 타임아웃 및 정책

### 6.1 타임아웃 정책
| 단계 | 타임아웃 | 정책 |
|------|----------|------|
| 재고 예약 | 30분 | 예약 후 30분 내 미결제 시 자동 해제 |
| 결제 대기 | 10분 | 결제 페이지 진입 후 10분 내 미완료 시 취소 |
| 주문 확정 | 24시간 | 결제 후 24시간 내 확정 필요 |
| 배송 준비 | 48시간 | 확정 후 48시간 내 배송 시작 |
| 구매 확정 | 7일 | 배송 완료 후 7일 내 자동 구매 확정 |

### 6.2 취소 가능 상태
| 상태 | 취소 가능 여부 | 비고 |
|------|----------------|------|
| PENDING | ✅ 가능 | 재고 예약 전 자유 취소 |
| CONFIRMED | ✅ 가능 | 결제 전 자유 취소 |
| PAYMENT_PENDING | ❌ 불가능 | 결제 프로세스 시작 전 |
| PAYMENT_PROCESSING | ✅ 가능 | 결제 진행 중 취소 가능 |
| PAID | ✅ 가능 | 배송 준비 전까지 취소 가능 |
| PREPARING | ✅ 가능 | 특수 사유만 취소 가능 |
| SHIPPED | ❌ 불가능 | 배송 시작 후 취소 불가 |
| DELIVERED | ❌ 불가능 | 반품으로만 처리 |
| COMPLETED | ❌ 불가능 | 환불로만 처리 |

### 6.3 환불 가능 상태
| 상태 | 환불 가능 여부 | 비고 |
|------|----------------|------|
| PAID | ✅ 가능 | 결제 완료 후 환불 가능 |
| PREPARING | ✅ 가능 | 상품 준비 중 환불 가능 |
| SHIPPED | ✅ 가능 | 배송 중 반품 후 환불 |
| DELIVERED | ✅ 가능 | 배송 완료 후 반품 환불 |
| COMPLETED | ✅ 가능 | 구매 확정 후도 특정 기간 내 환불 |

## 7. 보상 트랜잭션 패턴

```mermaid
graph TB
    subgraph "정상 흐름"
        A[주문 생성] --> B[재고 예약]
        B --> C[결제 처리]
        C --> D[재고 차감]
        D --> E[주문 완료]
    end
    
    subgraph "보상 트랜잭션"
        B -.실패.-> B1[예약 취소]
        C -.실패.-> C1[예약 해제]
        D -.실패.-> D1[결제 취소 & 예약 해제]
        E -.취소.-> E1[재고 복원 & 환불]
    end
    
    style A fill:#e1f5fe
    style E fill:#c8e6c9
    style B1 fill:#ffcdd2
    style C1 fill:#ffcdd2
    style D1 fill:#ffcdd2
    style E1 fill:#ffcdd2
```

## 8. 예외 처리 및 재시도 정책

### 8.1 비즈니스 규칙 위반 (재시도 불가)
- **OrderNotFoundException**: 주문이 존재하지 않음
- **InvalidOrderStateException**: 잘못된 상태 전이 시도
- **InsufficientStockException**: 재고 부족
- **DuplicateOrderException**: 중복 주문
- **PaymentLimitExceededException**: 결제 한도 초과

이러한 예외들은 재시도해도 동일한 결과가 나오므로 재시도하지 않고 즉시 실패 처리합니다.

### 8.2 기술적 실패 (재시도 가능)
- **DatabaseConnectionException**: DB 연결 실패
- **NetworkTimeoutException**: 네트워크 타임아웃
- **ServiceUnavailableException**: 서비스 일시 중단
- **OptimisticLockException**: 동시성 충돌

이러한 예외들은 일시적인 문제일 가능성이 높으므로 지수 백오프를 사용하여 재시도합니다.

### 8.3 재시도 정책
```yaml
retry:
  maxAttempts: 3
  backoff:
    initialInterval: 1000ms
    multiplier: 2.0
    maxInterval: 10000ms
  retryableExceptions:
    - DatabaseConnectionException
    - NetworkTimeoutException
    - ServiceUnavailableException
    - OptimisticLockException
```

## 9. 모니터링 포인트

### 9.1 비즈니스 메트릭
- 주문 생성률
- 재고 예약 성공률
- 결제 성공률
- 주문 완료율
- 평균 주문 처리 시간

### 9.2 기술 메트릭
- 이벤트 처리 지연 시간
- 이벤트 처리 실패율
- 서비스 응답 시간
- 데이터베이스 쿼리 성능
- 메시지 큐 크기

### 9.3 알림 설정
- 재고 예약 실패율 > 10%
- 결제 실패율 > 5%
- 이벤트 처리 지연 > 30초
- 서비스 응답 시간 > 1초
- 메시지 큐 크기 > 10,000