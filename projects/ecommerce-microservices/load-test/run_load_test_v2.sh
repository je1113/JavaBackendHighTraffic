#!/bin/bash

echo "🚀 Advanced Load Test v2.0"
echo "=========================="
echo

# Configuration
INITIAL_STOCK=500
TOTAL_REQUESTS=2000
CONCURRENT_USERS=100
URL="http://localhost:8083/api/v1/orders"

# Start mock server
echo "📦 Starting mock server with $INITIAL_STOCK initial stock..."
pkill -f "python3.*mock_server" 2>/dev/null
python3 enhanced_mock_server.py &
MOCK_PID=$!
echo "Mock server PID: $MOCK_PID"
sleep 3

# Check if server is running
if ! curl -s http://localhost:8083/metrics > /dev/null; then
    echo "❌ Failed to start mock server"
    exit 1
fi

echo "✅ Mock server is ready"
echo

# Function to send order request
send_order() {
    local order_num=$1
    local start_time=$(date +%s.%N)
    
    response=$(curl -s -w "\n%{http_code}" -X POST $URL \
        -H "Content-Type: application/json" \
        -d "{
            \"customerId\": \"550e8400-e29b-41d4-a716-446655440000\",
            \"items\": [{
                \"productId\": \"550e8400-e29b-41d4-a716-446655440001\",
                \"quantity\": 1,
                \"unitPrice\": 10000
            }],
            \"orderNumber\": $order_num
        }" 2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local response_time=$(echo "$end_time - $start_time" | bc)
    
    local http_code=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n-1)
    
    echo "$order_num,$http_code,$response_time" >> load_test_results.csv
    
    # Progress indicator
    if [ $((order_num % 100)) -eq 0 ]; then
        echo -n "."
    fi
}

export -f send_order
export URL

# Initialize results file
echo "order_num,http_code,response_time" > load_test_results.csv

# Run load test
echo "🔥 Starting load test..."
echo "  - Total requests: $TOTAL_REQUESTS"
echo "  - Concurrent users: $CONCURRENT_USERS"
echo "  - Target URL: $URL"
echo

START_TIME=$(date +%s.%N)

# Use GNU parallel or xargs for concurrent execution
if command -v parallel &> /dev/null; then
    echo "Using GNU parallel..."
    seq 1 $TOTAL_REQUESTS | parallel -j $CONCURRENT_USERS send_order {}
else
    echo "Using xargs..."
    seq 1 $TOTAL_REQUESTS | xargs -P $CONCURRENT_USERS -I {} bash -c 'send_order {}'
fi

END_TIME=$(date +%s.%N)
TOTAL_TIME=$(echo "$END_TIME - $START_TIME" | bc)

echo
echo

# Get final metrics
echo "📊 Fetching final metrics..."
METRICS=$(curl -s http://localhost:8083/metrics)
echo "$METRICS" | python3 -m json.tool > final_metrics.json

# Analyze results
echo "📈 Analyzing results..."
SUCCESS_COUNT=$(grep ",201," load_test_results.csv | wc -l)
FAILED_COUNT=$(grep ",409," load_test_results.csv | wc -l)
ERROR_COUNT=$(grep -v ",201,\|,409,\|order_num" load_test_results.csv | wc -l)

AVG_RESPONSE_TIME=$(awk -F',' 'NR>1 {sum+=$3; count++} END {print sum/count}' load_test_results.csv)
TPS=$(echo "scale=2; $TOTAL_REQUESTS / $TOTAL_TIME" | bc)

# Display results
echo
echo "🏁 Load Test Complete!"
echo "====================="
echo
echo "📊 Summary:"
echo "  - Test duration: ${TOTAL_TIME}s"
echo "  - Total requests: $TOTAL_REQUESTS"
echo "  - Successful orders: $SUCCESS_COUNT"
echo "  - Stock insufficient: $FAILED_COUNT"
echo "  - Errors: $ERROR_COUNT"
echo "  - Average TPS: $TPS requests/second"
echo "  - Average response time: ${AVG_RESPONSE_TIME}s"
echo
echo "📈 Final Stock Status:"
curl -s http://localhost:8083/api/v1/inventory/products/test/stock | python3 -m json.tool

# Cleanup
echo
echo "🧹 Cleaning up..."
kill $MOCK_PID 2>/dev/null

echo
echo "✅ Test complete! Results saved to:"
echo "  - load_test_results.csv"
echo "  - final_metrics.json"