#!/bin/bash

echo "🚀 Starting E-commerce Microservices..."
echo

# Build all services first
echo "📦 Building services..."
./gradlew clean build -x test

# Start infrastructure services first
echo "🏗️ Starting infrastructure services..."
docker-compose up -d postgres redis kafka zookeeper

# Wait for infrastructure to be ready
echo "⏳ Waiting for infrastructure services to be ready..."
sleep 30

# Start microservices
echo "🎯 Starting microservices..."
docker-compose up -d service-discovery
sleep 20

docker-compose up -d order-service inventory-service
sleep 10

docker-compose up -d api-gateway

# Show service status
echo
echo "✅ All services started!"
echo
echo "📊 Service Status:"
docker-compose ps

echo
echo "🌐 Service URLs:"
echo "  - Service Discovery: http://localhost:8761"
echo "  - API Gateway: http://localhost:8888"
echo "  - Order Service: http://localhost:8081"
echo "  - Inventory Service: http://localhost:8082"
echo "  - Kafka UI: http://localhost:8090"
echo "  - Prometheus: http://localhost:9090"
echo "  - Grafana: http://localhost:3000 (admin/admin123!)"
echo "  - Zipkin: http://localhost:9411"
echo
echo "📋 Logs: docker-compose logs -f [service-name]"
echo "🛑 Stop: docker-compose down"