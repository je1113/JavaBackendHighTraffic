# Common Package

## Purpose
Shared components and utilities used across all microservices in the e-commerce system.

## Key Components

### Event Publishing Infrastructure
- **KafkaEventPublisher**: Core event publishing with metrics and error handling
- **EventPublishingMetrics**: Prometheus metrics for event publishing
- **EventPublishingProducerListener**: Kafka producer callback handling
- **EventSerializer**: Custom event serialization logic
- **KafkaMetricsReporter**: Kafka metrics integration

### Domain Events
Located in `src/main/java/com/hightraffic/ecommerce/common/event/`

#### Base Events
- **DomainEvent**: Base interface for all domain events

#### Inventory Events
- **StockReservedEvent**: Stock successfully reserved
- **StockDeductedEvent**: Stock deducted from inventory
- **StockReleasedEvent**: Reserved stock released
- **StockAdjustedEvent**: Manual stock adjustment
- **InsufficientStockEvent**: Not enough stock available
- **LowStockAlertEvent**: Stock below threshold

#### Order Events
- **OrderCreatedEvent**: New order created
- **OrderConfirmedEvent**: Order confirmed by customer
- **OrderPaidEvent**: Payment completed
- **OrderCompletedEvent**: Order fulfillment completed
- **OrderCancelledEvent**: Order cancelled

#### Payment Events
- **PaymentCompletedEvent**: Payment processing completed

## Configuration
- Kafka producer configuration with optimization for high throughput
- Metrics and monitoring integration
- Error handling and retry mechanisms

## Usage
Import this package in other services to use shared events and Kafka publishing infrastructure.

## Dependencies
- Spring Kafka
- Micrometer (metrics)
- Jackson (JSON serialization)