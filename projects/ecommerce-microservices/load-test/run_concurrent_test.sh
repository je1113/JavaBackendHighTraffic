#!/bin/bash

echo "=== 재고 예약 동시성 부하 테스트 ==="
echo "초기 재고: 100"
echo "총 요청 수: 5000"
echo "동시 실행: 200개 프로세스"
echo

# Reset counters
SUCCESS=0
FAILED=0

# Function to send order request
send_order() {
    local order_id=$1
    local response=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/orders \
        -H "Content-Type: application/json" \
        -d "{
            \"customerId\": \"550e8400-e29b-41d4-a716-446655440000\",
            \"items\": [{
                \"productId\": \"550e8400-e29b-41d4-a716-446655440001\",
                \"quantity\": 1,
                \"unitPrice\": 10000
            }]
        }" 2>/dev/null)
    
    local status_code=$(echo "$response" | tail -n1)
    
    if [ "$status_code" = "201" ]; then
        echo -n "."
    else
        echo -n "x"
    fi
}

export -f send_order

# Start timing
START_TIME=$(date +%s.%N)

# Run requests in parallel using xargs
echo "테스트 시작..."
seq 1 5000 | xargs -P 200 -I {} bash -c 'send_order {}'

# End timing
END_TIME=$(date +%s.%N)
DURATION=$(echo "$END_TIME - $START_TIME" | bc)

echo
echo
echo "=== 테스트 완료 ==="
echo "소요 시간: ${DURATION}초"

# Check final stock status
echo
echo "최종 재고 상태:"
curl -s http://localhost:8081/api/v1/inventory/products/test/stock | python3 -m json.tool

# Kill mock server
pkill -f "python3 mock_server.py"