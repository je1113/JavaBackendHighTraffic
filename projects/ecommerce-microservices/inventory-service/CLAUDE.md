# Inventory Service

## Purpose
Manages product inventory, stock reservations, and stock movements with high-concurrency support.

## Architecture
Implements **Hexagonal Architecture** with clear separation of concerns:
- **Domain Layer**: Business logic and rules
- **Application Layer**: Use cases and service orchestration
- **Adapter Layer**: External integrations (web, persistence, messaging)

## Key Features
- **Stock Management**: Real-time stock tracking and updates
- **Stock Reservations**: Temporary stock holds for orders
- **Distributed Locking**: Redis-based locking for concurrency control
- **Caching**: Redis caching for performance optimization
- **Event Publishing**: Kafka integration for event-driven architecture
- **Monitoring**: Comprehensive metrics and health checks

## Domain Model
- **Product**: Product entity with stock information
- **Stock**: Stock quantity and availability
- **StockReservation**: Temporary stock reservations
- **Value Objects**: ProductId, ReservationId, StockQuantity

## Use Cases
- **GetStockUseCase**: Retrieve current stock levels
- **ReserveStockUseCase**: Reserve stock for orders
- **DeductStockUseCase**: Deduct stock after payment
- **RestoreStockUseCase**: Restore stock from cancelled reservations

## API Endpoints
- `GET /api/inventory/stock/{productId}` - Get stock level
- `POST /api/inventory/reserve` - Reserve stock
- `POST /api/inventory/adjust` - Adjust stock manually

## Event Handling
- **OrderCreatedEvent**: Reserve stock for new orders
- **OrderCancelledEvent**: Release reserved stock
- **PaymentCompletedEvent**: Deduct reserved stock

## Configuration
- Redis configuration for caching and distributed locking
- Kafka configuration for event publishing
- Database configuration with connection pooling

## Database Schema
- `products` table: Product information and stock levels
- `stock_reservations` table: Active stock reservations
- `stock_movements` table: Stock movement history

## Development Commands
```bash
# Run service
./gradlew :inventory-service:bootRun

# Run tests
./gradlew :inventory-service:test

# Run architecture tests only
./gradlew :inventory-service:test --tests "*.ArchitectureTestSuite"

# Database migration
./gradlew :inventory-service:flywayMigrate
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

### 실행 방법
```bash
# 특정 테스트만 실행
./gradlew :inventory-service:test --tests "*.DomainLayerArchitectureTest"

# JaCoCo 커버리지 검증 제외하고 실행
./gradlew :inventory-service:test -x jacocoTestCoverageVerification
```

## Key Dependencies
- Spring Boot Web
- Spring Data JPA
- Spring Kafka
- Redis/Redisson
- PostgreSQL
- Flyway (database migrations)
- Micrometer (metrics)
- ArchUnit (architecture tests)