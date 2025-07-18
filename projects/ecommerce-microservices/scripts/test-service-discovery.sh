#!/bin/bash

echo "==========================================="
echo "Service Discovery Test Script"
echo "==========================================="

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Wait for Service Discovery to be ready
echo -e "${YELLOW}Waiting for Service Discovery to start...${NC}"
while ! curl -s -u admin:admin http://localhost:8761/actuator/health > /dev/null; do
    sleep 2
done
echo -e "${GREEN}Service Discovery is ready!${NC}"

# Check Eureka dashboard
echo -e "\n${YELLOW}Checking Eureka Dashboard...${NC}"
if curl -s -u admin:admin http://localhost:8761/ | grep -q "Eureka"; then
    echo -e "${GREEN}✓ Eureka Dashboard is accessible${NC}"
else
    echo -e "${RED}✗ Eureka Dashboard is not accessible${NC}"
fi

# Check registered services
echo -e "\n${YELLOW}Checking registered services...${NC}"
APPS=$(curl -s -u admin:admin http://localhost:8761/eureka/apps -H "Accept: application/json" | jq -r '.applications.application[]?.name' 2>/dev/null)

if [ -z "$APPS" ]; then
    echo -e "${YELLOW}No services registered yet${NC}"
else
    echo -e "${GREEN}Registered services:${NC}"
    echo "$APPS" | while read app; do
        echo "  - $app"
    done
fi

# Check health endpoints
echo -e "\n${YELLOW}Checking health endpoints...${NC}"
services=("service-discovery:8761" "api-gateway:8080" "inventory-service:8081" "order-service:8082")

for service in "${services[@]}"; do
    IFS=':' read -ra ADDR <<< "$service"
    name="${ADDR[0]}"
    port="${ADDR[1]}"
    
    if [ "$name" == "service-discovery" ]; then
        health_url="http://admin:admin@localhost:$port/actuator/health"
    else
        health_url="http://localhost:$port/actuator/health"
    fi
    
    if curl -s "$health_url" | jq -e '.status == "UP"' > /dev/null 2>&1; then
        echo -e "  ${GREEN}✓ $name is healthy${NC}"
    else
        echo -e "  ${RED}✗ $name is not healthy or not running${NC}"
    fi
done

echo -e "\n${YELLOW}Service Discovery URLs:${NC}"
echo "  Eureka Dashboard: http://localhost:8761"
echo "  Eureka Apps API: http://localhost:8761/eureka/apps"
echo "  Health Check: http://localhost:8761/actuator/health"

echo -e "\n${GREEN}Test completed!${NC}"