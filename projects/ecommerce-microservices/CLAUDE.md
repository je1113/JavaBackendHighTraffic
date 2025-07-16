# E-commerce Microservices Project

## Project Overview
This is a high-traffic e-commerce microservices system built with Java and Spring Boot, implementing Domain-Driven Design (DDD) and Hexagonal Architecture patterns.

## Architecture
- **Microservices**: inventory-service, order-service, api-gateway, service-discovery
- **Event-Driven**: Kafka for asynchronous communication
- **Caching**: Redis for performance optimization
- **Database**: PostgreSQL with JPA/Hibernate
- **Monitoring**: Prometheus metrics integration

## Key Services
1. **Inventory Service**: Stock management, reservations, distributed locking
2. **Order Service**: Order lifecycle management, payment processing
3. **API Gateway**: Request routing and load balancing
4. **Service Discovery**: Service registration and discovery
5. **Common**: Shared events, Kafka publishers, metrics

## Development Commands
```bash
# Build all services
./gradlew build

# Run tests
./gradlew test

# Start infrastructure (Docker)
./docker/start-infrastructure.sh

# Run specific service
./gradlew :inventory-service:bootRun
./gradlew :order-service:bootRun
```

## Key Technologies
- Java 17
- Spring Boot 3.x
- Spring Data JPA
- Apache Kafka
- Redis/Redisson
- PostgreSQL
- Docker & Docker Compose
- Prometheus

## Documentation
- See `/docs/` directory for detailed architecture documentation
- Individual service documentation in each service's CLAUDE.md file