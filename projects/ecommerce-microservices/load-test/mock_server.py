#!/usr/bin/env python3
import json
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

# Global state
available_stock = 100
stock_lock = threading.Lock()
order_count = 0
successful_orders = 0

class MockHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        global available_stock, order_count, successful_orders
        
        if self.path == '/api/v1/orders':
            with stock_lock:
                order_count += 1
                order_id = f"ORDER-{order_count:05d}"
                
                if available_stock > 0:
                    available_stock -= 1
                    successful_orders += 1
                    
                    response = {
                        "orderId": order_id,
                        "status": "CONFIRMED",
                        "message": "Order created successfully"
                    }
                    self.send_response(201)
                else:
                    response = {
                        "error": "INSUFFICIENT_STOCK",
                        "message": "Insufficient stock for the requested items"
                    }
                    self.send_response(409)
                
                self.send_header('Content-Type', 'application/json')
                self.end_headers()
                self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_GET(self):
        if self.path.startswith('/api/v1/inventory/products/'):
            with stock_lock:
                response = {
                    "productId": "550e8400-e29b-41d4-a716-446655440001",
                    "availableStock": available_stock,
                    "reservedStock": 100 - available_stock,
                    "totalOrders": order_count,
                    "successfulOrders": successful_orders
                }
            
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps(response).encode())
        else:
            self.send_response(404)
            self.end_headers()
    
    def log_message(self, format, *args):
        # Suppress request logging for cleaner output
        pass

if __name__ == '__main__':
    server = HTTPServer(('localhost', 8081), MockHandler)
    print(f"Mock server started on port 8081")
    print(f"Initial stock: {available_stock}")
    print("Ready for load testing...")
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print(f"\nShutting down...")
        print(f"Final results: Total orders: {order_count}, Successful: {successful_orders}, Failed: {order_count - successful_orders}")
        server.shutdown()