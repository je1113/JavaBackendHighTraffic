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

# Database migration
./gradlew :inventory-service:flywayMigrate
```

## Key Dependencies
- Spring Boot Web
- Spring Data JPA
- Spring Kafka
- Redis/Redisson
- PostgreSQL
- Flyway (database migrations)
- Micrometer (metrics)