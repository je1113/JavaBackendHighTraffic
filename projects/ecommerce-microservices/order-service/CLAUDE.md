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

# Database migration
./gradlew :order-service:flywayMigrate
```

## Key Dependencies
- Spring Boot Web
- Spring Data JPA
- Spring Kafka
- WebClient (for external API calls)
- PostgreSQL
- Flyway (database migrations)
- Micrometer (metrics)