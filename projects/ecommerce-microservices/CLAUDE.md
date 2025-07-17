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

---

This documentation provides a comprehensive overview of the e-commerce microservices system. For specific implementation details, refer to individual service documentation and code comments.