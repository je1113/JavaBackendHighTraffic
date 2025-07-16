# API Gateway

## Purpose
Serves as the single entry point for all client requests, providing routing, load balancing, and cross-cutting concerns.

## Key Features
- **Request Routing**: Route requests to appropriate microservices
- **Load Balancing**: Distribute requests across service instances
- **Authentication & Authorization**: Centralized security handling
- **Rate Limiting**: Prevent API abuse and ensure fair usage
- **Request/Response Transformation**: Protocol translation and data formatting
- **Monitoring**: Request tracking and performance metrics

## Architecture
- **Gateway Filter Chain**: Pre and post-processing of requests
- **Service Discovery Integration**: Dynamic service endpoint resolution
- **Circuit Breaker**: Fault tolerance and resilience patterns
- **Caching**: Response caching for improved performance

## Configuration
- Service routing rules and load balancing strategies
- Security policies and authentication providers
- Rate limiting rules and quotas
- Circuit breaker thresholds and fallback strategies

## Routing Rules
- `/api/inventory/**` → Inventory Service
- `/api/orders/**` → Order Service
- `/api/auth/**` → Authentication Service
- Health check endpoints for all services

## Security Features
- JWT token validation
- API key authentication
- CORS handling
- Request sanitization

## Development Commands
```bash
# Run gateway
./gradlew :api-gateway:bootRun

# Run tests
./gradlew :api-gateway:test
```

## Key Dependencies
- Spring Cloud Gateway
- Spring Security
- Spring Cloud LoadBalancer
- Service Discovery Client
- Micrometer (metrics)