package com.hightraffic.ecommerce.loadtest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mock server to simulate inventory service behavior for load testing
 */
@Slf4j
public class MockInventoryServer {
    
    private static final int PORT = 8081;
    private static final int INITIAL_STOCK = 100;
    private static final AtomicInteger availableStock = new AtomicInteger(INITIAL_STOCK);
    private static final AtomicInteger orderCount = new AtomicInteger(0);
    
    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Configure endpoints
        server.createContext("/api/v1/orders", new OrderHandler());
        server.createContext("/api/v1/inventory/products/", new StockHandler());
        
        // Use thread pool
        server.setExecutor(Executors.newFixedThreadPool(200));
        
        server.start();
        log.info("Mock server started on port {}", PORT);
        log.info("Initial stock: {}", INITIAL_STOCK);
    }
    
    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                int orderNumber = orderCount.incrementAndGet();
                
                // Simulate stock check and reservation
                boolean stockAvailable = availableStock.get() > 0;
                int responseCode;
                String response;
                
                if (stockAvailable && availableStock.decrementAndGet() >= 0) {
                    responseCode = 201;
                    response = String.format("""
                        {
                            "orderId": "ORDER-%05d",
                            "status": "CONFIRMED",
                            "message": "Order created successfully"
                        }
                        """, orderNumber);
                    log.debug("Order {} created. Remaining stock: {}", orderNumber, availableStock.get());
                } else {
                    // Restore stock if we went negative
                    availableStock.incrementAndGet();
                    responseCode = 409;
                    response = """
                        {
                            "error": "INSUFFICIENT_STOCK",
                            "message": "Insufficient stock for the requested items"
                        }
                        """;
                    log.debug("Order {} rejected - insufficient stock", orderNumber);
                }
                
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseCode, response.length());
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
    
    static class StockHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = String.format("""
                    {
                        "productId": "550e8400-e29b-41d4-a716-446655440001",
                        "availableStock": %d,
                        "reservedStock": %d,
                        "totalOrders": %d
                    }
                    """, 
                    availableStock.get(),
                    INITIAL_STOCK - availableStock.get(),
                    orderCount.get()
                );
                
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}