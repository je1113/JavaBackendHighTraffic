# DDD 유비쿼터스 언어 (Ubiquitous Language) 가이드

## 📚 목차
1. [유비쿼터스 언어란?](#1-유비쿼터스-언어란)
2. [핵심 원칙과 특징](#2-핵심-원칙과-특징)
3. [우리 프로젝트의 유비쿼터스 언어](#3-우리-프로젝트의-유비쿼터스-언어)
4. [도메인별 용어 정의](#4-도메인별-용어-정의)
5. [실제 코드에서의 적용](#5-실제-코드에서의-적용)
6. [언어 관리 방법](#6-언어-관리-방법)
7. [Best Practices](#7-best-practices)

---

## 1. 유비쿼터스 언어란?

### 정의
**유비쿼터스 언어(Ubiquitous Language)**는 개발팀과 도메인 전문가들이 공통으로 사용하는 언어입니다. 이 언어는 코드, 문서, 대화에서 일관되게 사용되어야 합니다.

### 목적
- **의사소통 개선**: 도메인 전문가와 개발자 간 원활한 소통
- **이해도 향상**: 비즈니스 로직의 명확한 이해
- **코드 품질**: 도메인을 직접적으로 반영하는 코드
- **유지보수성**: 일관된 언어로 인한 코드 가독성 향상

### 특징
```
✅ 도메인 전문가가 사용하는 용어
✅ 개발자가 코드에서 사용하는 용어
✅ 문서에서 사용하는 용어
✅ 회의에서 사용하는 용어

= 모두 동일한 용어 사용
```

---

## 2. 핵심 원칙과 특징

### 2.1 일관성 (Consistency)
```java
// ❌ 잘못된 예 - 용어 불일치
public class OrderInfo {           // 코드: OrderInfo
    private CustomerData customer; // 코드: CustomerData
    private PaymentDetail payment; // 코드: PaymentDetail
}

// 회의: "주문 정보를 확인해주세요"
// 문서: "Order Management System"
// 코드: "OrderInfo", "CustomerData"

// ✅ 올바른 예 - 용어 일치
public class Order {              // 코드: Order
    private Customer customer;    // 코드: Customer
    private Payment payment;      // 코드: Payment
}

// 회의: "주문(Order)을 확인해주세요"
// 문서: "Order Domain"
// 코드: "Order", "Customer"
```

### 2.2 명확성 (Clarity)
```java
// ❌ 모호한 용어
public class Data {
    private String status;  // 무엇의 상태?
    private int count;      // 무엇의 개수?
}

// ✅ 명확한 용어
public class Order {
    private OrderStatus status;     // 주문 상태
    private int itemCount;          // 주문 아이템 개수
}
```

### 2.3 도메인 중심 (Domain-Centric)
```java
// ❌ 기술 중심 용어
public class OrderProcessor {
    public void processData(OrderDTO dto) {
        // 기술적 용어 중심
    }
}

// ✅ 도메인 중심 용어
public class OrderService {
    public void confirmOrder(Order order) {
        // 비즈니스 용어 중심
    }
}
```

---

## 3. 우리 프로젝트의 유비쿼터스 언어

### 3.1 핵심 개념 (Core Concepts)

| 한국어 | 영어 | 정의 | 예시 |
|--------|------|------|------|
| 주문 | Order | 고객이 상품을 구매하기 위해 생성하는 요청 | 주문을 생성하다, 주문을 확정하다 |
| 고객 | Customer | 상품을 구매하는 사용자 | 고객이 주문을 생성하다 |
| 상품 | Product | 판매되는 물건 | 상품을 장바구니에 담다 |
| 재고 | Inventory/Stock | 창고에 보관된 상품의 수량 | 재고를 예약하다, 재고가 부족하다 |
| 예약 | Reservation | 주문을 위해 임시로 확보한 재고 | 재고를 예약하다, 예약을 해제하다 |
| 결제 | Payment | 주문에 대한 금전적 거래 | 결제를 처리하다, 결제가 완료되다 |
| 배송 | Shipping/Delivery | 주문된 상품을 고객에게 전달 | 배송을 시작하다, 배송이 완료되다 |

### 3.2 주문 도메인 용어

| 용어 | 영어 | 설명 |
|------|------|------|
| 주문 대기 | Pending | 생성되었지만 아직 확정되지 않은 주문 |
| 주문 확정 | Confirmed | 재고 예약이 완료되어 확정된 주문 |
| 주문 취소 | Cancelled | 고객이나 시스템에 의해 취소된 주문 |
| 주문 완료 | Completed | 모든 프로세스가 끝난 주문 |
| 주문 아이템 | Order Item | 주문에 포함된 개별 상품과 수량 |
| 총 금액 | Total Amount | 주문의 전체 결제 금액 |

### 3.3 재고 도메인 용어

| 용어 | 영어 | 설명 |
|------|------|------|
| 가용 재고 | Available Stock | 판매 가능한 재고 수량 |
| 예약 재고 | Reserved Stock | 주문을 위해 임시 확보된 재고 |
| 안전 재고 | Safety Stock | 최소 유지해야 하는 재고 수량 |
| 재고 조정 | Stock Adjustment | 입고, 출고, 손실 등으로 인한 재고 변경 |
| 재고 부족 | Out of Stock | 판매 가능한 재고가 없는 상태 |
| 재고 경고 | Low Stock Alert | 재고가 임계값 이하로 떨어진 경고 |

---

## 4. 도메인별 용어 정의

### 4.1 Order Domain (주문 도메인)

#### 엔티티 (Entities)
```java
/**
 * 주문 (Order)
 * - 고객이 상품을 구매하기 위해 생성하는 요청
 * - 주문 아이템들과 총 금액을 포함
 */
public class Order {
    private OrderId orderId;           // 주문 식별자
    private CustomerId customerId;     // 고객 식별자
    private List<OrderItem> items;     // 주문 아이템 목록
    private Money totalAmount;         // 총 주문 금액
    private OrderStatus status;        // 주문 상태
}

/**
 * 주문 아이템 (Order Item)
 * - 주문에 포함된 개별 상품과 수량 정보
 */
public class OrderItem {
    private ProductId productId;       // 상품 식별자
    private int quantity;              // 주문 수량
    private Money unitPrice;           // 단가
}
```

#### 값 객체 (Value Objects)
```java
/**
 * 주문 상태 (Order Status)
 * - 주문의 현재 처리 단계
 */
public enum OrderStatus {
    PENDING,        // 주문 대기
    CONFIRMED,      // 주문 확정
    PAID,          // 결제 완료
    SHIPPED,       // 배송 중
    DELIVERED,     // 배송 완료
    COMPLETED,     // 주문 완료
    CANCELLED      // 주문 취소
}

/**
 * 금액 (Money)
 * - 통화와 금액을 함께 표현하는 값 객체
 */
public class Money {
    private BigDecimal amount;         // 금액
    private Currency currency;         // 통화
}
```

#### 도메인 서비스 (Domain Services)
```java
/**
 * 주문 가격 계산 서비스
 * - 할인, 쿠폰 등을 적용한 최종 가격 계산
 */
public class OrderPricingService {
    public Money calculateTotalPrice(Order order, DiscountPolicy policy);
}
```

### 4.2 Inventory Domain (재고 도메인)

#### 엔티티 (Entities)
```java
/**
 * 재고 (Inventory)
 * - 특정 창고의 특정 상품 재고 정보
 */
public class Inventory {
    private InventoryId inventoryId;   // 재고 식별자
    private ProductId productId;       // 상품 식별자
    private WarehouseId warehouseId;   // 창고 식별자
    private int availableQuantity;     // 가용 수량
    private int reservedQuantity;      // 예약 수량
    private int safetyStock;           // 안전 재고
}

/**
 * 재고 예약 (Stock Reservation)
 * - 주문을 위해 임시로 확보된 재고
 */
public class StockReservation {
    private ReservationId reservationId;  // 예약 식별자
    private OrderId orderId;              // 주문 식별자
    private int quantity;                 // 예약 수량
    private Instant expiresAt;            // 예약 만료 시간
}
```

#### 도메인 서비스 (Domain Services)
```java
/**
 * 재고 관리 서비스
 * - 재고 예약, 해제, 조정 등의 비즈니스 로직
 */
public class StockManagementService {
    public ReservationId reserveStock(ProductId productId, int quantity);
    public void releaseReservation(ReservationId reservationId);
    public void adjustStock(ProductId productId, int quantity, String reason);
}
```

### 4.3 공통 용어 (Common Terms)

#### 식별자 (Identifiers)
```java
// 모든 식별자는 UUID 기반
public class OrderId { }        // 주문 식별자
public class CustomerId { }     // 고객 식별자
public class ProductId { }      // 상품 식별자
public class InventoryId { }    // 재고 식별자
public class ReservationId { }  // 예약 식별자
```

#### 이벤트 (Events)
```java
// 도메인 이벤트 명명 규칙: [도메인][동작]Event
public class OrderCreatedEvent { }    // 주문 생성됨
public class OrderConfirmedEvent { }  // 주문 확정됨
public class OrderCancelledEvent { }  // 주문 취소됨
public class StockReservedEvent { }   // 재고 예약됨
public class StockReleasedEvent { }   // 재고 해제됨
```

---

## 5. 실제 코드에서의 적용

### 5.1 메서드 명명
```java
// ❌ 기술적/모호한 명명
public class OrderService {
    public void processOrder(Order order) { }
    public void updateStatus(Order order, String status) { }
    public void handlePayment(Order order) { }
}

// ✅ 도메인 중심 명명
public class OrderService {
    public void confirmOrder(Order order) { }           // 주문 확정
    public void cancelOrder(Order order, String reason) { }  // 주문 취소
    public void completePayment(Order order) { }        // 결제 완료
}
```

### 5.2 클래스와 패키지 구조
```
src/main/java/com/hightraffic/ecommerce/
├── order/                    # 주문 도메인
│   ├── domain/
│   │   ├── model/
│   │   │   ├── Order.java           # 주문
│   │   │   ├── OrderItem.java       # 주문 아이템
│   │   │   └── vo/
│   │   │       ├── OrderId.java     # 주문 식별자
│   │   │       ├── OrderStatus.java # 주문 상태
│   │   │       └── Money.java       # 금액
│   │   ├── repository/
│   │   │   └── OrderRepository.java # 주문 저장소
│   │   └── service/
│   │       └── OrderPricingService.java # 주문 가격 계산
│   └── application/
│       ├── command/
│       │   ├── CreateOrderCommand.java   # 주문 생성 명령
│       │   ├── ConfirmOrderCommand.java  # 주문 확정 명령
│       │   └── CancelOrderCommand.java   # 주문 취소 명령
│       └── service/
│           └── OrderApplicationService.java
└── inventory/                # 재고 도메인
    ├── domain/
    │   ├── model/
    │   │   ├── Inventory.java           # 재고
    │   │   ├── StockReservation.java    # 재고 예약
    │   │   └── vo/
    │   │       ├── InventoryId.java     # 재고 식별자
    │   │       └── ReservationId.java   # 예약 식별자
    │   └── service/
    │       └── StockManagementService.java # 재고 관리
    └── application/
        ├── command/
        │   ├── ReserveStockCommand.java     # 재고 예약 명령
        │   └── ReleaseStockCommand.java     # 재고 해제 명령
        └── service/
            └── InventoryApplicationService.java
```

### 5.3 도메인 이벤트
```java
// 이벤트 명명 규칙: [주체][동작]Event
public class OrderCreatedEvent extends DomainEvent {
    private final OrderId orderId;
    private final CustomerId customerId;
    private final List<OrderItem> orderItems;
    
    // 비즈니스 언어로 메서드 명명
    public boolean hasMultipleItems() { }
    public Money getTotalAmount() { }
    public boolean isHighValueOrder() { }
}

public class StockReservedEvent extends DomainEvent {
    private final ReservationId reservationId;
    private final ProductId productId;
    private final int reservedQuantity;
    
    // 비즈니스 언어로 메서드 명명
    public boolean isLargeQuantityReservation() { }
    public boolean willCauseLowStock() { }
}
```

### 5.4 비즈니스 규칙 표현
```java
public class Order {
    
    // 비즈니스 규칙을 명확한 언어로 표현
    public boolean canBeModified() {
        return status == OrderStatus.PENDING;
    }
    
    public boolean canBeCancelled() {
        return status.isCancellable();
    }
    
    public boolean requiresPayment() {
        return status == OrderStatus.CONFIRMED && !isPaid();
    }
    
    public boolean isEligibleForShipping() {
        return status == OrderStatus.PAID && hasStockReservation();
    }
    
    // 도메인 로직을 비즈니스 언어로 메서드화
    public void addItem(ProductId productId, int quantity, Money unitPrice) {
        validateCanAddItem();
        OrderItem item = OrderItem.create(productId, quantity, unitPrice);
        items.add(item);
        recalculateTotal();
    }
    
    public void confirmOrder(ReservationId reservationId) {
        validateCanConfirm();
        this.status = OrderStatus.CONFIRMED;
        this.stockReservationId = reservationId;
        registerEvent(new OrderConfirmedEvent(this));
    }
}
```

---

## 6. 언어 관리 방법

### 6.1 용어집 (Glossary) 관리
```markdown
# 프로젝트 용어집

## A
- **Available Stock (가용 재고)**: 현재 판매 가능한 재고 수량
- **Aggregate Root (애그리게이트 루트)**: 애그리게이트의 진입점이 되는 엔티티

## C
- **Customer (고객)**: 상품을 구매하는 사용자
- **Confirmed Order (확정 주문)**: 재고 예약이 완료되어 확정된 주문

## O
- **Order (주문)**: 고객이 상품을 구매하기 위해 생성하는 요청
- **Order Item (주문 아이템)**: 주문에 포함된 개별 상품과 수량
- **Out of Stock (재고 부족)**: 판매 가능한 재고가 없는 상태
```

### 6.2 모델링 세션
```java
// 도메인 전문가와의 대화를 코드로 직접 반영
public class Customer {
    
    // "고객이 주문을 생성한다"
    public Order createOrder(List<OrderItem> items) {
        return Order.create(this.id, items);
    }
    
    // "고객이 주문을 취소할 수 있다"
    public void cancelOrder(Order order, String reason) {
        if (!order.belongsTo(this.id)) {
            throw new UnauthorizedOrderCancellationException();
        }
        order.cancel(reason);
    }
}
```

### 6.3 언어 진화 관리
```java
// 용어가 변경될 때의 대응
// Before: "주문 처리" -> After: "주문 확정"
@Deprecated
public void processOrder(Order order) {
    confirmOrder(order);
}

public void confirmOrder(Order order) {
    // 새로운 비즈니스 언어로 구현
}
```

---

## 7. Best Practices

### 7.1 DO (해야 할 것)

#### ✅ 도메인 전문가와 협업
```java
// 도메인 전문가: "주문이 확정되면 재고를 예약해야 해요"
public class Order {
    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        // 도메인 이벤트로 재고 예약 요청
        registerEvent(new OrderConfirmedEvent(this.id, this.items));
    }
}
```

#### ✅ 비즈니스 로직을 언어로 표현
```java
public class Inventory {
    
    // "재고가 부족한지 확인한다"
    public boolean isOutOfStock() {
        return availableQuantity <= 0;
    }
    
    // "안전 재고 이하인지 확인한다"
    public boolean isBelowSafetyStock() {
        return availableQuantity <= safetyStock;
    }
    
    // "예약 가능한 수량을 계산한다"
    public int getAvailableToPromise() {
        return availableQuantity - safetyStock;
    }
}
```

#### ✅ 명확한 의도 표현
```java
public class OrderService {
    
    // "주문을 확정한다" - 명확한 비즈니스 의도
    public void confirmOrder(OrderId orderId, ReservationId reservationId) {
        Order order = orderRepository.findById(orderId);
        order.confirm(reservationId);
        orderRepository.save(order);
    }
    
    // "주문을 취소한다" - 명확한 비즈니스 의도
    public void cancelOrder(OrderId orderId, String reason) {
        Order order = orderRepository.findById(orderId);
        order.cancel(reason);
        orderRepository.save(order);
    }
}
```

### 7.2 DON'T (하지 말아야 할 것)

#### ❌ 기술적 용어 사용
```java
// 잘못된 예
public class OrderProcessor {
    public void processData(OrderDTO dto) { }
    public void executeBusinessLogic(Map<String, Object> params) { }
    public void handleRequest(HttpRequest request) { }
}
```

#### ❌ 모호한 용어 사용
```java
// 잘못된 예
public class OrderManager {
    public void doSomething(Order order) { }
    public void handleOrder(Order order) { }
    public void updateOrder(Order order, String data) { }
}
```

#### ❌ 일관성 없는 용어
```java
// 잘못된 예 - 같은 개념을 다른 용어로 표현
public class Order {
    private OrderStatus state;     // 'status'와 'state' 혼용
}

public class OrderService {
    public void processOrder(Order order) { }    // 'process'
    public void handleOrder(Order order) { }     // 'handle'
    public void executeOrder(Order order) { }    // 'execute'
    // 모두 같은 의미인데 다른 용어 사용
}
```

---

## 🎯 핵심 정리

### 유비쿼터스 언어의 5가지 핵심 원칙

1. **일관성 (Consistency)**: 모든 곳에서 동일한 용어 사용
2. **명확성 (Clarity)**: 모호하지 않은 명확한 의미
3. **도메인 중심 (Domain-Centric)**: 비즈니스 관점의 용어
4. **진화 (Evolution)**: 이해가 깊어질수록 언어도 개선
5. **협업 (Collaboration)**: 도메인 전문가와 개발자 간 공통 언어

### 실천 방법

- **용어집 유지**: 프로젝트 용어를 문서화하고 지속적으로 업데이트
- **코드 리뷰**: 도메인 언어 관점에서 코드 검토
- **모델링 세션**: 정기적인 도메인 전문가와의 협업 세션
- **테스트 코드**: 비즈니스 시나리오를 자연어에 가깝게 표현

유비쿼터스 언어를 잘 구축하면 코드가 곧 문서가 되고, 비즈니스 로직의 이해도와 유지보수성이 크게 향상됩니다.