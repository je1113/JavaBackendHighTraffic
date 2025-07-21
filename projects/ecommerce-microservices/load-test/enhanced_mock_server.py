#!/usr/bin/env python3
import json
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime
import sys

# Configuration
INITIAL_STOCK = 500  # 더 많은 재고로 테스트
PORT = 8083

# Global state
available_stock = INITIAL_STOCK
stock_lock = threading.Lock()
order_count = 0
successful_orders = 0
failed_orders = 0
concurrent_requests = 0
max_concurrent = 0

# Metrics
start_time = time.time()
response_times = []
order_timestamps = []

class MockHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        global available_stock, order_count, successful_orders, failed_orders
        global concurrent_requests, max_concurrent
        
        request_start = time.time()
        
        # Track concurrent requests
        with stock_lock:
            concurrent_requests += 1
            if concurrent_requests > max_concurrent:
                max_concurrent = concurrent_requests
        
        try:
            if self.path == '/api/v1/orders':
                # Simulate some processing time
                time.sleep(0.001)  # 1ms processing
                
                with stock_lock:
                    order_count += 1
                    order_id = f"ORDER-{order_count:05d}"
                    timestamp = datetime.now()
                    
                    if available_stock > 0:
                        available_stock -= 1
                        successful_orders += 1
                        order_timestamps.append(timestamp)
                        
                        response = {
                            "orderId": order_id,
                            "status": "CONFIRMED",
                            "message": "Order created successfully",
                            "remainingStock": available_stock
                        }
                        status_code = 201
                        
                        # Print progress every 100 orders
                        if successful_orders % 100 == 0:
                            elapsed = time.time() - start_time
                            rps = order_count / elapsed if elapsed > 0 else 0
                            print(f"\r[{timestamp.strftime('%H:%M:%S')}] "
                                  f"Orders: {order_count} | Success: {successful_orders} | "
                                  f"Failed: {failed_orders} | Stock: {available_stock} | "
                                  f"RPS: {rps:.1f} | Concurrent: {concurrent_requests}", 
                                  end='', flush=True)
                    else:
                        failed_orders += 1
                        response = {
                            "error": "INSUFFICIENT_STOCK",
                            "message": "Insufficient stock for the requested items",
                            "orderId": order_id
                        }
                        status_code = 409
                
                response_time = (time.time() - request_start) * 1000
                response_times.append(response_time)
                
                self.send_response(status_code)
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
            else:
                self.send_response(404)
                self.end_headers()
        finally:
            with stock_lock:
                concurrent_requests -= 1
    
    def do_GET(self):
        if self.path == '/metrics':
            # Calculate metrics
            total_time = time.time() - start_time
            avg_response_time = sum(response_times) / len(response_times) if response_times else 0
            
            metrics = {
                "summary": {
                    "initialStock": INITIAL_STOCK,
                    "availableStock": available_stock,
                    "totalOrders": order_count,
                    "successfulOrders": successful_orders,
                    "failedOrders": failed_orders,
                    "successRate": (successful_orders / order_count * 100) if order_count > 0 else 0,
                    "totalTime": total_time,
                    "avgTPS": order_count / total_time if total_time > 0 else 0,
                    "maxConcurrentRequests": max_concurrent
                },
                "responseTime": {
                    "avg": avg_response_time,
                    "min": min(response_times) if response_times else 0,
                    "max": max(response_times) if response_times else 0,
                    "count": len(response_times)
                }
            }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(metrics, indent=2).encode())
            
        elif self.path.startswith('/api/v1/inventory/products/'):
            response = {
                "productId": "550e8400-e29b-41d4-a716-446655440001",
                "initialStock": INITIAL_STOCK,
                "availableStock": available_stock,
                "reservedStock": INITIAL_STOCK - available_stock,
                "totalOrders": order_count,
                "successfulOrders": successful_orders,
                "failedOrders": failed_orders
            }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response, indent=2).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        # Suppress request logging
        pass

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', PORT), MockHandler)
    print(f"🚀 Enhanced Mock Server Started on port {PORT}")
    print(f"📦 Initial stock: {INITIAL_STOCK}")
    print(f"📊 Metrics: http://localhost:{PORT}/metrics")
    print(f"📈 Stock API: http://localhost:{PORT}/api/v1/inventory/products/test/stock")
    print("-" * 80)
    print("Real-time monitoring:")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\n\n📊 Final Results:")
        print(f"Total time: {time.time() - start_time:.2f} seconds")
        print(f"Total orders: {order_count}")
        print(f"Successful: {successful_orders}")
        print(f"Failed: {failed_orders}")
        print(f"Final stock: {available_stock}")
        print(f"Max concurrent requests: {max_concurrent}")
        if response_times:
            print(f"Average response time: {sum(response_times) / len(response_times):.2f}ms")
        server.shutdown()