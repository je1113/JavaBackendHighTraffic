# E-commerce Microservices Project - Comprehensive Documentation

## 🎯 Project Overview
A high-traffic e-commerce microservices system built with Java 17 and Spring Boot 3.2.0, implementing Domain-Driven Design (DDD) and Hexagonal Architecture patterns. The system is designed to handle millions of concurrent users with robust scalability, resilience, and monitoring capabilities.

## 🏗️ System Architecture

### Core Design Patterns
- **Domain-Driven Design (DDD)**: Clear bounded contexts with aggregates, entities, and value objects
- **Hexagonal Architecture**: Ports and adapters pattern for clean separation of concerns
- **Event-Driven Architecture**: Asynchronous communication via Apache Kafka
- **CQRS**: Separate read and write paths for optimized performance
- **Saga Pattern**: Distributed transaction management across services

### Microservices Overview
1. **inventory-service**: Stock management with distributed locking and caching
2. **order-service**: Order lifecycle management and payment orchestration
3. **api-gateway**: Request routing, rate limiting, and authentication
4. **service-discovery**: Service registration and discovery (Eureka)
5. **common**: Shared events, utilities, and Kafka infrastructure

## 📦 Technology Stack

### Core Technologies
- **Java 17** with Spring Boot 3.2.0
- **Spring Cloud 2023.0.0** (latest version)
- **Gradle 8.5** multi-module build system

### Data Layer
- **PostgreSQL 15**: Primary database with separate schemas per service
- **Redis 7.2**: Distributed caching and locking
- **MongoDB 6**: Event store for event sourcing (optional)
- **Flyway**: Database version control

### Messaging & Events
- **Apache Kafka**: Event streaming platform
- **Spring Kafka**: Kafka integration with retry and DLQ support

### Infrastructure
- **Docker & Docker Compose**: Containerization
- **Prometheus & Grafana**: Metrics and monitoring
- **Zipkin**: Distributed tracing
- **Redisson**: Distributed locking framework

## 🔧 Development Environment

### Prerequisites
- Java 17 JDK
- Docker & Docker Compose
- Gradle 8.5+
- Git

### Quick Start
```bash
# Clone repository
git clone <repository-url>
cd ecommerce-microservices

# Copy environment template
cp .env.example .env

# Start infrastructure
./docker/start-infrastructure.sh

# Build all services
./gradlew clean build

# Run individual services
./gradlew :inventory-service:bootRun
./gradlew :order-service:bootRun
./gradlew :api-gateway:bootRun
./gradlew :service-discovery:bootRun
```

### Testing Commands
```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Run integration tests
./gradlew integrationTest
```

## 🏛️ Service Architecture Details

### Common Module
The foundation for cross-service communication:

**Domain Events**:
- Base `DomainEvent` class with metadata (eventId, timestamp, correlationId)
- Event categories: Inventory, Order, Payment
- Jackson-based serialization with type information

**Kafka Infrastructure**:
- `KafkaEventPublisher`: Centralized event publishing with retry mechanism
- Batch publishing support
- Comprehensive metrics (success/failure rates, latency)
- Dead Letter Queue (DLQ) for failed messages

### Inventory Service
Manages product inventory with high-concurrency support:

**Domain Model**:
- `Product` (Aggregate Root): Orchestrates stock operations
- `Stock` (Entity): Manages available/reserved quantities
- `StockReservation` (Value Object): Time-limited reservations (30 min default)

**Key Features**:
- **Distributed Locking**: Redisson-based locks for concurrent stock operations
- **Two-Phase Commit**: Reserve → Confirm/Release pattern
- **Caching Strategy**: Redis with TTL (Product: 10min, Stock: 5min)
- **Event Publishing**: StockReserved, StockDeducted, LowStockAlert events

**API Endpoints**:
```
GET  /api/v1/inventory/products/{productId}/stock
POST /api/v1/inventory/products/{productId}/reservations
POST /api/v1/inventory/reservations/batch
POST /api/v1/inventory/products/{productId}/reservations/{reservationId}/deduct
DELETE /api/v1/inventory/products/{productId}/reservations/{reservationId}
```

### Order Service
Manages order lifecycle from creation to completion:

**Domain Model**:
- `Order` (Aggregate Root): 13 state transitions
- `OrderItem` (Entity): Line items within order
- Value Objects: `OrderId`, `Money`, `OrderStatus`

**Order States Flow**:
```
PENDING → CONFIRMED → PAYMENT_PENDING → PAID → PREPARING → SHIPPED → DELIVERED → COMPLETED
        ↘ CANCELLED ↗              ↘ FAILED ↗    ↘ REFUNDING → REFUNDED
```

**Business Rules**:
- Maximum 100 items per order
- Duplicate order prevention (5-minute window)
- Configurable cancellation window (24 hours)
- VIP discounts and bulk pricing

**External Integrations**:
- Payment Gateway (sync/async modes)
- Stock Validation Adapter
- Event-driven saga orchestration

**API Endpoints**:
```
POST /api/v1/orders
GET  /api/v1/orders/{orderId}
GET  /api/v1/orders/customer/{customerId}
POST /api/v1/orders/{orderId}/confirm
POST /api/v1/orders/{orderId}/cancel
```

### API Gateway
Spring Cloud Gateway providing:

**Features**:
- **Request Routing**: Dynamic routing to microservices
- **Load Balancing**: Client-side load balancing via Eureka
- **Rate Limiting**: Redis-based rate limiting per client
- **Circuit Breaker**: Resilience4j integration
- **Security**: JWT validation and API key authentication
- **CORS Handling**: Configurable CORS policies

**Route Configuration**:
- `/api/inventory/**` → Inventory Service
- `/api/orders/**` → Order Service
- `/api/auth/**` → Authentication Service

### Service Discovery
Eureka server for service registration:

**Features**:
- Automatic service registration
- Health monitoring
- Load balancer integration
- High availability setup support

## 🚀 Performance Optimizations

### High-Traffic Design
1. **Caching Layer**: Multi-level caching with Redis
2. **Connection Pooling**: Optimized for database and Redis
3. **Batch Processing**: Kafka batch consumption
4. **Async Processing**: Non-blocking I/O where applicable
5. **Database Indexes**: Optimized for common query patterns

### Kafka Optimizations
```yaml
Configuration highlights:
- acks: all (durability)
- compression.type: snappy
- batch.size: 16384
- linger.ms: 10
- enable.idempotence: true
```

### JVM Tuning
- G1GC for Kafka brokers
- Heap sizing based on service requirements
- JVM metrics exported to Prometheus

## 📊 Monitoring & Observability

### Metrics Collection
- **Micrometer**: Business and technical metrics
- **Prometheus**: Time-series data storage
- **Grafana**: Visualization dashboards

### Key Metrics
- Request rate and latency
- Event publishing success/failure rates
- Cache hit/miss ratios
- Lock acquisition times
- Business metrics (orders/minute, stock levels)

### Health Indicators
- Database connectivity
- Redis availability
- Kafka broker status
- Custom business health checks

### Distributed Tracing
- Zipkin integration
- Correlation ID propagation
- Request flow visualization

## 🔒 Security Features

### API Security
- JWT token validation
- API key authentication
- Rate limiting per client
- Request sanitization

### Data Security
- Encrypted passwords in database
- Secure configuration management
- Network isolation via Docker networks

## 🐳 Infrastructure Setup

### Docker Services
```yaml
Services:
- PostgreSQL 15 (2 databases)
- Redis 7.2 (caching & locking)
- Kafka & Zookeeper (messaging)
- MongoDB 6 (event store)
- Prometheus & Grafana (monitoring)
- Zipkin (tracing)
- Kafka UI (management)
```

### Infrastructure Commands
```bash
# Start all infrastructure
./docker/start-infrastructure.sh

# Stop all services
docker-compose down

# View logs
docker-compose logs -f [service-name]

# Access services
# Kafka UI: http://localhost:8090
# Grafana: http://localhost:3000
# Zipkin: http://localhost:9411
```

## 📚 Documentation Structure

### Service-Specific Documentation
- `/inventory-service/CLAUDE.md`: Inventory service details
- `/order-service/CLAUDE.md`: Order service details
- `/api-gateway/CLAUDE.md`: Gateway configuration
- `/service-discovery/CLAUDE.md`: Discovery service setup

### Architecture Documentation
- `/docs/DDD-*.md`: Domain-Driven Design concepts
- `/docs/Event-Publishing-Architecture.md`: Event flow details
- `/docs/*-Persistence-*.md`: Database design patterns
- `/docs/Kafka-Guide.md`: Kafka setup and usage

## 🧪 Testing Strategy

### Test Types
1. **Unit Tests**: Domain logic and services
2. **Integration Tests**: Database and Kafka interactions
3. **Contract Tests**: API contracts between services
4. **Performance Tests**: JMH benchmarks for critical paths

### Test Infrastructure
- Testcontainers for integration tests
- Embedded Kafka for event testing
- H2 database for fast unit tests
- MockWebServer for external API mocking

## 🚦 CI/CD Considerations

### Build Pipeline
```gradle
tasks:
- clean
- compile
- test
- integrationTest
- jacocoTestReport
- build
- dockerBuildImage
```

### Deployment Strategy
- Blue-green deployments
- Rolling updates for zero downtime
- Health check validation
- Automated rollback on failures

## 🔄 Event Flow Examples

### Order Creation Flow
1. Client → API Gateway → Order Service
2. Order Service creates order → publishes `OrderCreatedEvent`
3. Inventory Service receives event → reserves stock
4. Inventory publishes `StockReservedEvent`
5. Order Service confirms order → triggers payment
6. Payment completes → `PaymentCompletedEvent`
7. Inventory deducts stock → order marked as paid

### Stock Management Flow
1. Stock reservation with distributed lock
2. Time-limited reservation (30 minutes)
3. Confirmation converts to permanent deduction
4. Cancellation releases reservation
5. Low stock alerts trigger notifications

## 🛠️ Troubleshooting

### Common Issues
1. **Service Discovery**: Ensure Eureka is running first
2. **Kafka Connection**: Check Zookeeper and Kafka startup order
3. **Database Migration**: Flyway migrations must complete
4. **Redis Connection**: Verify Redis password in .env

### Debug Commands
```bash
# Check service health
curl http://localhost:8080/actuator/health

# View Kafka topics
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092

# Redis CLI
docker exec -it redis redis-cli -a yourpassword

# Database access
docker exec -it postgres psql -U inventory_user -d inventory_service
```

## 📈 Performance Benchmarks

### Expected Capacity
- Order creation: 10,000 TPS
- Stock queries: 50,000 TPS (with caching)
- Event publishing: 100,000 events/second
- API Gateway: 100,000 requests/second

### Scaling Strategy
- Horizontal scaling for stateless services
- Kafka partition increase for throughput
- Redis cluster for cache scaling
- Read replicas for database scaling

## 🔗 External Integrations

### Payment Gateway
- Configurable timeout and retry
- Circuit breaker for resilience
- Async and sync processing modes

### Notification Service
- Event-driven notifications
- Email and SMS support
- Template-based messaging

### Analytics Pipeline
- Event streaming to data lake
- Real-time analytics support
- Business intelligence integration

## 🎓 Best Practices

### Code Organization
- Clear package structure by feature
- Separation of concerns via ports/adapters
- Immutable domain events
- Value objects for type safety

### Configuration Management
- Environment-specific profiles
- Externalized configuration
- Secure credential storage
- Feature toggles support

### Error Handling
- Global exception handlers
- Detailed error responses
- Correlation ID tracking
- Comprehensive logging

## 🚀 Future Enhancements

### Planned Features
1. GraphQL API Gateway
2. Event Sourcing implementation
3. CQRS read model optimization
4. Multi-region deployment
5. Advanced analytics dashboard

### Technical Debt
1. Migrate to native Kubernetes
2. Implement service mesh (Istio)
3. Advanced caching strategies
4. Performance testing automation

## 🧪 테스트 작성 규칙

### Value Object 생성 규칙
모든 Value Object들은 private 생성자를 가지며, 정적 팩토리 메서드를 사용해야 합니다.

#### 필수 사용 패턴:
```java
// ❌ 잘못된 사용법 - 컴파일 에러 발생
new ProductId("PROD-001")
new OrderId(UUID.randomUUID())
new CustomerId("CUST-001")

// ✅ 올바른 사용법 - 정적 팩토리 메서드 사용
ProductId.of("550e8400-e29b-41d4-a716-446655440001")
OrderId.of(UUID.randomUUID().toString())
CustomerId.of("550e8400-e29b-41d4-a716-446655440000")
```

#### UUID 형식 요구사항:
모든 ID Value Object들은 유효한 UUID 형식을 요구합니다.

**테스트용 UUID 패턴:**
```java
// Customer IDs
CustomerId.of("550e8400-e29b-41d4-a716-446655440000")

// Product IDs (순차적)
ProductId.of("550e8400-e29b-41d4-a716-446655440001")
ProductId.of("550e8400-e29b-41d4-a716-446655440002")
ProductId.of("550e8400-e29b-41d4-a716-446655440003")

// 동적 생성 (루프 등)
String.format("550e8400-e29b-41d4-a716-4466554400%02d", i)
```

### 이벤트 클래스 메서드 네이밍
Event 클래스들은 서로 다른 메서드 네이밍을 사용합니다:

```java
// OrderCreatedEvent
event.getOrderItems()  // ❌ getItems() 아님

// OrderPaidEvent  
event.getOrderItems()  // ❌ getItems() 아님
event.getTransactionId()  // ❌ getPaymentTransactionId() 아님

// OrderCancelledEvent
event.getCancelReason()  // ❌ getCancellationReason() 아님
event.getCompensationActions().get(0).getActionType()  // ❌ getType() 아님

// PaymentCompletedEvent 생성자 파라미터 순서
new PaymentCompletedEvent(
    paymentId,      // 1st
    orderId,        // 2nd  
    customerId,     // 3rd
    amount,         // 4th
    currency,       // 5th
    paymentMethod,  // 6th
    transactionId,  // 7th
    paidAt          // 8th
)
```

### 테스트 작성 시 주의사항
1. **모든 Value Object는 정적 팩토리 메서드 사용**
2. **UUID 형식의 유효한 문자열 사용**
3. **이벤트 클래스의 올바른 메서드명 확인**
4. **생성자 파라미터 순서와 개수 확인**
5. **관련 assertion도 새로운 값에 맞게 업데이트**

## 🎯 도메인 규칙 및 설계 원칙

### 주문 상태 전이 규칙
주문 상태는 명확한 비즈니스 규칙에 따라 전이됩니다:

1. **취소 가능한 상태**
   - `PENDING`, `CONFIRMED`, `PAYMENT_PROCESSING`, `PAID`, `PREPARING`
   - 주의: `PAYMENT_PENDING`은 취소 불가 (결제 프로세스가 시작되지 않은 상태)

2. **환불 가능한 상태**
   - `PAID`, `PREPARING`, `SHIPPED`, `DELIVERED`, `COMPLETED`

3. **최종 상태**
   - `CANCELLED`, `REFUNDED`, `FAILED`
   - 이 상태에서는 더 이상 상태 전이 불가

### 이벤트 처리 원칙

1. **비즈니스 규칙 위반 vs 기술적 실패**
   ```java
   // 비즈니스 규칙 위반 - 재시도하지 않음
   - OrderNotFoundException
   - InvalidOrderStateException
   - DuplicateOrderItemException
   
   // 기술적 실패 - 재시도 가능
   - Database connection errors
   - Network timeouts
   - External service failures
   ```

2. **중복 이벤트 처리**
   - 이미 처리된 상태로의 전이 시도는 무시 (멱등성 보장)
   - 중복은 분산 시스템에서 정상적인 상황으로 간주

3. **보상 트랜잭션**
   - 취소된 주문에 대한 결제 완료 이벤트 → 환불 프로세스 트리거 고려
   - 재고 부족으로 인한 주문 실패 → 결제 취소 프로세스 실행

### 테스트 작성 시 주의사항

1. **상태 전이 테스트**
   - 항상 유효한 상태 전이 경로를 따라야 함
   - 예: PAYMENT_PENDING → PAYMENT_PROCESSING → PAID

2. **도메인 이벤트 테스트**
   - 이벤트 발행 순서가 비즈니스 로직과 일치하는지 확인
   - 이벤트 내용이 도메인 상태를 정확히 반영하는지 검증

3. **예외 상황 테스트**
   - 비즈니스 규칙 위반 시나리오 검증
   - 재시도 가능/불가능한 상황 구분하여 테스트

---

This documentation provides a comprehensive overview of the e-commerce microservices system. For specific implementation details, refer to individual service documentation and code comments.