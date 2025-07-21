#!/bin/bash

echo "=== Starting Inventory Reservation Load Test ==="
echo

# Kill any existing mock server
pkill -f MockInventoryServer || true

# Compile and build if needed
echo "Building load test module..."
cd .. && ./gradlew :load-test:clean :load-test:build -x test && cd load-test

# Start mock server
echo "Starting mock inventory server..."
java -cp build/libs/load-test-0.0.1-SNAPSHOT.jar com.hightraffic.ecommerce.loadtest.MockInventoryServer &
MOCK_PID=$!
echo "Mock server PID: $MOCK_PID"

# Wait for server to start
sleep 3

# Run load test
echo
echo "Starting load test..."
java -Xms1g -Xmx2g -cp build/libs/load-test-0.0.1-SNAPSHOT.jar com.hightraffic.ecommerce.loadtest.StockReservationLoadTest

# Kill mock server
echo
echo "Cleaning up..."
kill $MOCK_PID 2>/dev/null

echo "Load test completed!"