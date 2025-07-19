# Order Service

## Purpose
Manages the complete order lifecycle from creation to completion, handling payments and coordinating with inventory.

## Architecture
Implements **Hexagonal Architecture** with Domain-Driven Design:
- **Domain Layer**: Order business logic and rules
- **Application Layer**: Order use cases and workflows
- **Adapter Layer**: Web API, persistence, external services

## Key Features
- **Order Lifecycle Management**: Complete order processing workflow
- **Payment Integration**: External payment service integration
- **Stock Validation**: Coordination with inventory service
- **Event-Driven Processing**: Kafka-based event handling
- **Business Rules**: Configurable order validation rules

## Domain Model
- **Order**: Main order aggregate with items and status
- **OrderItem**: Individual items within an order
- **Value Objects**: OrderId, CustomerId, ProductId, Money, OrderStatus

## Order States
- **PENDING**: Order created, awaiting stock reservation
- **CONFIRMED**: Stock reserved, awaiting payment
- **PAID**: Payment completed, awaiting fulfillment
- **COMPLETED**: Order fulfilled
- **CANCELLED**: Order cancelled at any stage

## Use Cases
- **CreateOrderUseCase**: Create new orders with validation
- **ConfirmOrderUseCase**: Confirm orders after stock reservation
- **CancelOrderUseCase**: Cancel orders and release resources
- **GetOrderUseCase**: Retrieve order information

## API Endpoints
- `POST /api/orders` - Create new order
- `GET /api/orders/{orderId}` - Get order details
- `GET /api/orders/customer/{customerId}` - Get customer orders
- `POST /api/orders/{orderId}/cancel` - Cancel order

## Event Handling
- **StockReservedEvent**: Proceed with payment processing
- **StockDeductedEvent**: Mark order as ready for fulfillment
- **PaymentCompletedEvent**: Update order status after payment
- **InsufficientStockEvent**: Handle stock unavailability

## External Integrations
- **Payment Service**: Process payments through external API
- **Inventory Service**: Validate stock availability
- **Notification Service**: Send order updates to customers

## Business Rules
- Maximum order value limits
- Customer order frequency limits
- Product availability validation
- Payment processing requirements

## Configuration
- Payment service endpoints and credentials
- Stock validation service configuration
- Kafka event handling configuration
- Database connection settings

## Database Schema
- `orders` table: Main order information
- `order_items` table: Order line items
- Audit fields for tracking changes

## Development Commands
```bash
# Run service
./gradlew :order-service:bootRun

# Run tests
./gradlew :order-service:test

# Run architecture tests only
./gradlew :order-service:test --tests "*.ArchitectureTestSuite"

# Database migration
./gradlew :order-service:flywayMigrate
```

## Architecture Tests
ArchUnit 기반의 자동화된 아키텍처 검증 테스트:

### Test Suites
- **ArchitectureTestSuite**: 모든 아키텍처 테스트를 한 번에 실행하는 테스트 스위트

### Test Categories
1. **DomainLayerArchitectureTest**
   - 도메인 레이어의 순수성 검증
   - 프레임워크 독립성 확인
   - 외부 레이어 의존성 차단
   - Value Object equals/hashCode 구현 검증

2. **HexagonalArchitectureTest**
   - 포트와 어댑터 패턴 준수 검증
   - 어댑터가 포트를 통해서만 접근하는지 확인
   - 인바운드/아웃바운드 포트 분리 검증
   - JPA 엔티티 캡슐화 확인

3. **PackageStructureTest**
   - DDD 기반 패키지 구조 검증
   - 각 계층별 올바른 하위 패키지 구성 확인
   - 예외 클래스 위치 규칙 검증 (각 계층이 자체 예외 보유 가능)

4. **NamingConventionTest**
   - 클래스 네이밍 규칙 검증 (Controller, Service, UseCase, Port 등)
   - JPA 엔티티와 리포지토리 네이밍 규칙
   - DTO 클래스 접미사 규칙
   - 상수 네이밍 규칙 (대문자와 밑줄)

### Known Issues
테스트 실행 시 일부 실패하는 항목들:
- Value Object의 equals/hashCode 미구현
- 일부 도메인 클래스의 프레임워크 의존성
- 내부 클래스로 인한 네이밍 규칙 위반

### 실행 방법
```bash
# 특정 테스트만 실행
./gradlew :order-service:test --tests "*.PackageStructureTest"

# JaCoCo 커버리지 검증 제외하고 실행
./gradlew :order-service:test -x jacocoTestCoverageVerification
```

## Key Dependencies
- Spring Boot Web
- Spring Data JPA
- Spring Kafka
- WebClient (for external API calls)
- PostgreSQL
- Flyway (database migrations)
- Micrometer (metrics)
- ArchUnit (architecture tests)