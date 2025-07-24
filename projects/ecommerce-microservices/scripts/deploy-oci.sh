#!/bin/bash

# OCI Deployment Script
# Usage: ./deploy-oci.sh <docker-username> <git-sha>

set -e

DOCKER_USERNAME=$1
GIT_SHA=$2

if [ -z "$DOCKER_USERNAME" ] || [ -z "$GIT_SHA" ]; then
    echo "Usage: $0 <docker-username> <git-sha>"
    exit 1
fi

echo "🚀 Starting deployment to OCI..."

# Navigate to application directory
cd ~/ecommerce-app

# Pull latest images
echo "📦 Pulling latest Docker images..."
docker pull ${DOCKER_USERNAME}/ecommerce-api-gateway:latest
docker pull ${DOCKER_USERNAME}/ecommerce-order-service:latest
docker pull ${DOCKER_USERNAME}/ecommerce-inventory-service:latest
docker pull ${DOCKER_USERNAME}/ecommerce-service-discovery:latest

# Stop current containers
echo "🛑 Stopping current containers..."
docker-compose -f docker-compose.prod.yml down

# Clean up old images to save space
echo "🧹 Cleaning up old images..."
docker image prune -f

# Start services with new images
echo "🔄 Starting services..."
docker-compose -f docker-compose.prod.yml up -d

# Wait for services to be healthy
echo "⏳ Waiting for services to be healthy..."
sleep 30

# Check service health
echo "🏥 Checking service health..."
services=("service-discovery" "api-gateway" "order-service" "inventory-service")
for service in "${services[@]}"; do
    if docker-compose -f docker-compose.prod.yml ps | grep $service | grep -q "Up"; then
        echo "✅ $service is running"
    else
        echo "❌ $service is not running properly"
        docker-compose -f docker-compose.prod.yml logs $service
        exit 1
    fi
done

# Run database migrations
echo "🗄️ Running database migrations..."
docker-compose -f docker-compose.prod.yml exec -T order-service ./gradlew flywayMigrate || true
docker-compose -f docker-compose.prod.yml exec -T inventory-service ./gradlew flywayMigrate || true

# Final health check
echo "🔍 Final health check..."
curl -f http://localhost:8761/actuator/health || { echo "Service Discovery health check failed"; exit 1; }
curl -f http://localhost:8888/actuator/health || { echo "API Gateway health check failed"; exit 1; }

echo "✅ Deployment completed successfully!"
echo "📊 Services deployed with version: ${GIT_SHA}"

# Show running containers
docker-compose -f docker-compose.prod.yml ps