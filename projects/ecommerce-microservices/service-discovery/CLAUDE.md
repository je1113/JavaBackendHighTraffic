# Service Discovery

## Purpose
Provides service registration and discovery capabilities for the microservices ecosystem.

## Key Features
- **Service Registration**: Services register themselves on startup
- **Service Discovery**: Services can discover and communicate with each other
- **Health Monitoring**: Monitor service health and availability
- **Load Balancing Support**: Provide service instance information for load balancing
- **Configuration Management**: Centralized configuration distribution

## Architecture
- **Registry Server**: Central service registry
- **Client Libraries**: Service registration and discovery clients
- **Health Checks**: Regular health status monitoring
- **Configuration Server**: Centralized configuration management

## Service Registration
Services register with the following information:
- Service name and version
- Network location (host and port)
- Health check endpoints
- Metadata (tags, environment, etc.)

## Configuration
- Registry server settings
- Health check intervals
- Service discovery timeouts
- Failover and retry policies

## Integration
- All microservices register with this service
- API Gateway uses service discovery for routing
- Services use discovery for inter-service communication

## Development Commands
```bash
# Run service discovery
./gradlew :service-discovery:bootRun

# Run tests
./gradlew :service-discovery:test
```

## Key Dependencies
- Netflix Eureka (or Consul)
- Spring Cloud Config
- Spring Boot Actuator
- Micrometer (metrics)