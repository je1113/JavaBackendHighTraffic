# DDD Aggregate (애그리게이트) 완벽 가이드

## 📚 목차
1. [Aggregate란 무엇인가?](#1-aggregate란-무엇인가)
2. [핵심 개념과 특징](#2-핵심-개념과-특징)
3. [설계 규칙과 원칙](#3-설계-규칙과-원칙)
4. [실제 구현 예제](#4-실제-구현-예제)
5. [Aggregate 간 통신](#5-aggregate-간-통신)
6. [Best Practices](#6-best-practices)
7. [Anti-Patterns](#7-anti-patterns)

---

## 1. Aggregate란 무엇인가?

### 정의
**Aggregate(애그리게이트)**는 도메인 모델의 일관성 경계를 정의하는 객체들의 묶음입니다. 데이터 변경의 단위로 취급되며, 하나의 Root Entity와 연관된 Entity들, Value Object들로 구성됩니다.

### 핵심 목적
- **트랜잭션 일관성** 보장
- **비즈니스 불변식(Invariant)** 유지
- **복잡성 관리**를 위한 경계 설정

```java
// Aggregate 구조 예시
public class Order {  // Aggregate Root
    private OrderId id;                    // Value Object
    private List<OrderItem> items;         // Entity Collection
    private Money totalAmount;             // Value Object
    private OrderStatus status;            // Value Object
    
    // 모든 변경은 Root를 통해서만 가능
}
```

---

## 2. 핵심 개념과 특징

### 2.1 Aggregate Root (애그리게이트 루트)
- Aggregate의 **유일한 진입점**
- 외부에서는 Root를 통해서만 내부 객체에 접근
- Global Identity를 가짐

### 2.2 트랜잭션 일관성
```java
// ❌ 잘못된 예 - 일관성 경계 위반
@Transactional
public void updateOrder() {
    Order order = orderRepository.findById(orderId);
    OrderItem item = orderItemRepository.findById(itemId);  // 직접 접근
    item.updateQuantity(10);
    
    // totalAmount와 일치하지 않는 상태 발생 가능!
}

// ✅ 올바른 예 - Root를 통한 일관성 보장
@Transactional
public void updateOrder() {
    Order order = orderRepository.findById(orderId);
    order.updateItemQuantity(itemId, 10);  // Root를 통해 수정
    orderRepository.save(order);
    // Order가 내부적으로 totalAmount도 함께 업데이트
}
```

### 2.3 캡슐화
```java
public class Order {
    private List<OrderItem> items = new ArrayList<>();
    
    // 내부 구조를 숨기고 의도를 드러내는 메서드 제공
    public void addItem(ProductId productId, int quantity, Money price) {
        validateCanAddItem();
        OrderItem item = new OrderItem(productId, quantity, price);
        items.add(item);
        recalculateTotalAmount();
    }
    
    // 직접 컬렉션을 반환하지 않음
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
```

---

## 3. 설계 규칙과 원칙

### Rule 1: 작은 Aggregate 유지
```java
// ❌ 너무 큰 Aggregate
@Entity
public class Customer {
    private CustomerId id;
    private List<Order> allOrders;        // 수천 개가 될 수 있음
    private List<Address> addresses;
    private List<PaymentMethod> payments;
    private List<Review> reviews;
    // 메모리 문제, 성능 저하, 동시성 문제 발생
}

// ✅ 적절히 분리된 Aggregate
@Entity
public class Customer {  // Customer Aggregate
    private CustomerId id;
    private CustomerProfile profile;
    private ContactInfo contactInfo;
}

@Entity
public class Order {  // 별도의 Order Aggregate
    private OrderId id;
    private CustomerId customerId;  // ID로만 참조
    private List<OrderItem> items;
}
```

### Rule 2: ID를 통한 참조
```java
// ❌ 직접 참조 - 메모리 낭비, 일관성 문제
public class Order {
    private Customer customer;  // 전체 Customer 객체 로드
    private Product product;    // 전체 Product 객체 로드
}

// ✅ ID 참조 - 느슨한 결합, 성능 향상
public class Order {
    private CustomerId customerId;  // ID만 보관
    private ProductId productId;    // ID만 보관
    
    // 필요시 서비스 레이어에서 조합
}
```

### Rule 3: 하나의 트랜잭션 = 하나의 Aggregate
```java
@Service
public class OrderService {
    // ✅ 단일 Aggregate 수정
    @Transactional
    public void createOrder(CreateOrderCommand cmd) {
        Order order = Order.create(cmd.getCustomerId(), cmd.getItems());
        orderRepository.save(order);
        
        // 다른 Aggregate는 이벤트로 처리
        eventPublisher.publish(new OrderCreatedEvent(order));
    }
}

@EventHandler
public class InventoryEventHandler {
    // 별도 트랜잭션에서 처리 (결과적 일관성)
    @Transactional
    public void on(OrderCreatedEvent event) {
        for (OrderItem item : event.getItems()) {
            Inventory inventory = inventoryRepository.findByProductId(item.getProductId());
            inventory.reserve(event.getOrderId(), item.getQuantity());
            inventoryRepository.save(inventory);
        }
    }
}
```

### Rule 4: 비즈니스 불변식을 기준으로 경계 설정
```java
public class ShoppingCart {
    private static final int MAX_ITEMS = 100;
    private List<CartItem> items = new ArrayList<>();
    private Money totalAmount;
    
    // 불변식: 장바구니 아이템은 100개를 초과할 수 없다
    public void addItem(ProductId productId, int quantity, Money price) {
        if (items.size() >= MAX_ITEMS) {
            throw new CartFullException("장바구니가 가득 찼습니다");
        }
        
        // 불변식: 총액은 항상 개별 아이템의 합과 일치해야 한다
        CartItem item = new CartItem(productId, quantity, price);
        items.add(item);
        recalculateTotalAmount();
    }
}
```

---

## 4. 실제 구현 예제

### 4.1 Order Aggregate 구현
```java
@Entity
@Table(name = "orders")
public class Order extends AbstractAggregateRoot<Order> {
    
    @EmbeddedId
    private OrderId id;
    
    @Embedded
    private CustomerId customerId;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();
    
    @Embedded
    private Money totalAmount;
    
    @Enumerated(EnumType.STRING)
    private OrderStatus status;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    // Factory Method
    public static Order create(CustomerId customerId, List<CreateOrderItemCommand> itemCommands) {
        Order order = new Order();
        order.id = OrderId.generate();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = Money.ZERO("KRW");
        
        itemCommands.forEach(cmd -> 
            order.addItem(cmd.getProductId(), cmd.getQuantity(), cmd.getPrice())
        );
        
        // 도메인 이벤트 등록
        order.registerEvent(new OrderCreatedEvent(order));
        
        return order;
    }
    
    // 비즈니스 메서드
    public void addItem(ProductId productId, int quantity, Money unitPrice) {
        validateAddItem();
        
        OrderItem item = OrderItem.create(productId, quantity, unitPrice);
        items.add(item);
        recalculateTotalAmount();
    }
    
    public void removeItem(ProductId productId) {
        validateModification();
        
        items.removeIf(item -> item.getProductId().equals(productId));
        recalculateTotalAmount();
    }
    
    public void confirm(ReservationId reservationId) {
        if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
            throw new InvalidOrderStateException("주문을 확정할 수 없는 상태입니다");
        }
        
        this.status = OrderStatus.CONFIRMED;
        registerEvent(new OrderConfirmedEvent(this.id, reservationId));
    }
    
    public void cancel(String reason) {
        if (!status.isCancellable()) {
            throw new InvalidOrderStateException("취소할 수 없는 주문입니다");
        }
        
        this.status = OrderStatus.CANCELLED;
        registerEvent(new OrderCancelledEvent(this.id, reason));
    }
    
    // Private 메서드
    private void validateAddItem() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("대기 중인 주문만 수정할 수 있습니다");
        }
        
        if (items.size() >= 100) {
            throw new OrderLimitExceededException("주문 아이템은 100개를 초과할 수 없습니다");
        }
    }
    
    private void validateModification() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("대기 중인 주문만 수정할 수 있습니다");
        }
    }
    
    private void recalculateTotalAmount() {
        this.totalAmount = items.stream()
            .map(OrderItem::getSubtotal)
            .reduce(Money.ZERO("KRW"), Money::add);
    }
}
```

### 4.2 OrderItem Entity (Aggregate 내부)
```java
@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Local Identity
    
    @Embedded
    private ProductId productId;
    
    private int quantity;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price")),
        @AttributeOverride(name = "currency", column = @Column(name = "currency"))
    })
    private Money unitPrice;
    
    protected OrderItem() {}  // JPA용
    
    public static OrderItem create(ProductId productId, int quantity, Money unitPrice) {
        OrderItem item = new OrderItem();
        item.productId = productId;
        item.quantity = quantity;
        item.unitPrice = unitPrice;
        return item;
    }
    
    public Money getSubtotal() {
        return unitPrice.multiply(quantity);
    }
    
    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        this.quantity = newQuantity;
    }
}
```

### 4.3 Inventory Aggregate 구현
```java
@Entity
@Table(name = "inventories")
public class Inventory extends AbstractAggregateRoot<Inventory> {
    
    @EmbeddedId
    private InventoryId id;
    
    @Embedded
    private ProductId productId;
    
    @Embedded
    private WarehouseId warehouseId;
    
    private int availableQuantity;
    private int reservedQuantity;
    private int safetyStock;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "inventory_id")
    private List<StockReservation> reservations = new ArrayList<>();
    
    @Version
    private Long version;  // 낙관적 락
    
    // 재고 예약
    public ReservationId reserve(OrderId orderId, int quantity, Duration expiresIn) {
        if (getAvailableToPromise() < quantity) {
            throw new InsufficientStockException(
                String.format("재고 부족: 요청=%d, 가용=%d", quantity, getAvailableToPromise())
            );
        }
        
        StockReservation reservation = StockReservation.create(
            orderId, quantity, Instant.now().plus(expiresIn)
        );
        
        reservations.add(reservation);
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        
        // 낮은 재고 경고 체크
        checkLowStock();
        
        registerEvent(new StockReservedEvent(this, reservation));
        
        return reservation.getId();
    }
    
    // 예약 해제
    public void release(ReservationId reservationId, String reason) {
        StockReservation reservation = findReservation(reservationId);
        
        availableQuantity += reservation.getQuantity();
        reservedQuantity -= reservation.getQuantity();
        reservations.remove(reservation);
        
        registerEvent(new StockReleasedEvent(this, reservation, reason));
    }
    
    // 재고 조정
    public void adjust(int quantityChange, String reason, String adjustedBy) {
        int newQuantity = availableQuantity + quantityChange;
        
        if (newQuantity < 0) {
            throw new IllegalArgumentException("재고는 음수가 될 수 없습니다");
        }
        
        int before = availableQuantity;
        availableQuantity = newQuantity;
        
        registerEvent(new StockAdjustedEvent(
            this, before, availableQuantity, reason, adjustedBy
        ));
        
        checkLowStock();
    }
    
    // 가용 재고 (예약 가능한 수량)
    public int getAvailableToPromise() {
        return availableQuantity - safetyStock;
    }
    
    private StockReservation findReservation(ReservationId id) {
        return reservations.stream()
            .filter(r -> r.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new ReservationNotFoundException(id));
    }
    
    private void checkLowStock() {
        if (availableQuantity <= safetyStock) {
            registerEvent(new LowStockAlertEvent(this));
        }
    }
}
```

---

## 5. Aggregate 간 통신

### 5.1 도메인 이벤트 방식
```java
// Order Aggregate에서 이벤트 발행
public class Order {
    public void confirm(ReservationId reservationId) {
        this.status = OrderStatus.CONFIRMED;
        
        // 도메인 이벤트 발행
        registerEvent(new OrderConfirmedEvent(
            this.id, 
            this.customerId, 
            this.totalAmount
        ));
    }
}

// Payment Aggregate에서 이벤트 처리
@Component
public class PaymentEventHandler {
    
    @EventHandler
    @Transactional
    public void handle(OrderConfirmedEvent event) {
        Payment payment = Payment.create(
            event.getOrderId(),
            event.getCustomerId(),
            event.getTotalAmount()
        );
        
        paymentRepository.save(payment);
        
        // 결제 프로세스 시작
        payment.process();
    }
}
```

### 5.2 Saga 패턴
```java
@Saga
public class OrderSaga {
    
    @Autowired
    private transient CommandGateway commandGateway;
    
    private OrderId orderId;
    private List<ProductReservation> reservations = new ArrayList<>();
    
    @StartSaga
    @SagaEventHandler
    public void handle(OrderCreatedEvent event) {
        this.orderId = event.getOrderId();
        
        // 각 상품에 대해 재고 예약 명령 전송
        event.getItems().forEach(item -> {
            ReserveStockCommand command = new ReserveStockCommand(
                item.getProductId(),
                event.getOrderId(),
                item.getQuantity()
            );
            commandGateway.send(command);
        });
    }
    
    @SagaEventHandler
    public void handle(StockReservedEvent event) {
        reservations.add(new ProductReservation(
            event.getProductId(), 
            event.getReservationId()
        ));
        
        // 모든 상품 예약 완료 확인
        if (allItemsReserved()) {
            commandGateway.send(new ConfirmOrderCommand(orderId));
        }
    }
    
    @SagaEventHandler
    public void handle(StockReservationFailedEvent event) {
        // 보상 트랜잭션 - 이미 예약된 재고 해제
        reservations.forEach(reservation -> {
            commandGateway.send(new ReleaseStockCommand(
                reservation.getProductId(),
                reservation.getReservationId()
            ));
        });
        
        // 주문 취소
        commandGateway.send(new CancelOrderCommand(
            orderId, 
            "재고 부족으로 인한 주문 취소"
        ));
    }
}
```

### 5.3 Domain Service를 통한 조정
```java
@DomainService
public class OrderPricingService {
    
    private final ProductRepository productRepository;
    private final DiscountPolicyRepository policyRepository;
    
    public Money calculateOrderTotal(Order order, CustomerId customerId) {
        // 여러 Aggregate의 정보를 조합하여 계산
        
        Money subtotal = order.getItems().stream()
            .map(item -> {
                Product product = productRepository.findById(item.getProductId());
                return product.getPrice().multiply(item.getQuantity());
            })
            .reduce(Money.ZERO("KRW"), Money::add);
        
        // 고객별 할인 정책 적용
        DiscountPolicy policy = policyRepository.findByCustomerId(customerId);
        Money discount = policy.calculateDiscount(subtotal);
        
        return subtotal.subtract(discount);
    }
}
```

---

## 6. Best Practices

### 6.1 Repository는 Aggregate 단위로
```java
// ✅ Aggregate Root를 위한 Repository
public interface OrderRepository {
    Order findById(OrderId id);
    List<Order> findByCustomerId(CustomerId customerId);
    void save(Order order);
    void delete(Order order);
}

// ❌ 내부 Entity를 위한 별도 Repository (안티패턴)
public interface OrderItemRepository {
    OrderItem findById(Long id);
    void save(OrderItem item);
}
```

### 6.2 Factory 패턴 활용
```java
public class OrderFactory {
    
    private final ProductRepository productRepository;
    private final PricingService pricingService;
    
    public Order createOrder(CustomerId customerId, List<OrderItemRequest> requests) {
        Order order = Order.create(customerId);
        
        requests.forEach(request -> {
            Product product = productRepository.findById(request.getProductId());
            Money price = pricingService.calculatePrice(product, request.getQuantity());
            
            order.addItem(product.getId(), request.getQuantity(), price);
        });
        
        return order;
    }
}
```

### 6.3 명확한 경계와 책임
```java
// Order Aggregate - 주문 관련 비즈니스 로직
public class Order {
    // 주문 확정, 취소, 배송 상태 관리
    
    public void markAsShipped(TrackingNumber trackingNumber) {
        if (status != OrderStatus.PAID) {
            throw new InvalidOrderStateException("결제 완료된 주문만 배송 가능합니다");
        }
        
        this.status = OrderStatus.SHIPPED;
        this.trackingNumber = trackingNumber;
        
        registerEvent(new OrderShippedEvent(this.id, trackingNumber));
    }
}

// Shipping Aggregate - 배송 관련 비즈니스 로직
public class Shipment {
    // 배송 경로, 배송사, 실제 배송 추적
    
    public void updateLocation(Location location) {
        this.currentLocation = location;
        this.lastUpdated = Instant.now();
        
        trackingHistory.add(new TrackingEntry(location, lastUpdated));
        
        if (location.equals(destination)) {
            markAsDelivered();
        }
    }
}
```

### 6.4 성능 최적화
```java
// Lazy Loading 활용
@Entity
public class Order {
    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<OrderItem> items;
    
    // 필요한 경우에만 조회
    @Transactional(readOnly = true)
    public OrderSummary getSummary() {
        return new OrderSummary(id, customerId, totalAmount, status);
    }
    
    // 상세 정보가 필요한 경우
    @Transactional(readOnly = true)
    public OrderDetail getDetail() {
        // items 컬렉션 초기화
        items.size();  // Lazy loading trigger
        return new OrderDetail(this);
    }
}
```

---

## 7. Anti-Patterns

### 7.1 ❌ Anemic Domain Model
```java
// 안티패턴 - 로직이 없는 도메인 모델
@Entity
public class Order {
    private Long id;
    private String customerId;
    private BigDecimal totalAmount;
    private String status;
    
    // Getter/Setter만 존재
    // 비즈니스 로직이 Service 레이어에 분산
}

@Service
public class OrderService {
    public void cancelOrder(Order order) {
        if (!"PENDING".equals(order.getStatus()) && 
            !"CONFIRMED".equals(order.getStatus())) {
            throw new IllegalStateException("취소 불가");
        }
        order.setStatus("CANCELLED");
        // 도메인 로직이 서비스에 있음
    }
}
```

### 7.2 ❌ 과도한 Aggregate
```java
// 안티패턴 - 너무 큰 Aggregate
@Entity
public class MarketPlace {
    private List<Shop> shops;           // 수천 개
    private List<Product> products;     // 수만 개
    private List<Customer> customers;   // 수십만 명
    private List<Order> orders;         // 수백만 개
    
    // 동시성 문제, 메모리 문제, 성능 저하
}
```

### 7.3 ❌ 양방향 참조
```java
// 안티패턴 - Aggregate 간 양방향 참조
@Entity
public class Order {
    @ManyToOne
    private Customer customer;  // 다른 Aggregate 직접 참조
}

@Entity
public class Customer {
    @OneToMany
    private List<Order> orders;  // 양방향 참조
}
```

### 7.4 ❌ Repository 남용
```java
// 안티패턴 - 내부 Entity에 직접 접근
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByProductId(String productId);
    
    @Modifying
    @Query("UPDATE OrderItem SET quantity = ?2 WHERE id = ?1")
    void updateQuantity(Long id, int quantity);  // Aggregate 우회
}
```

---

## 🎯 핵심 정리

1. **Aggregate는 트랜잭션 일관성의 경계**
2. **작게 유지하고 Root를 통해서만 접근**
3. **ID로 다른 Aggregate 참조**
4. **도메인 이벤트로 Aggregate 간 통신**
5. **비즈니스 불변식을 기준으로 설계**

Aggregate를 잘 설계하면 복잡한 도메인 로직을 관리하기 쉽고, 시스템의 확장성과 유지보수성이 크게 향상됩니다.