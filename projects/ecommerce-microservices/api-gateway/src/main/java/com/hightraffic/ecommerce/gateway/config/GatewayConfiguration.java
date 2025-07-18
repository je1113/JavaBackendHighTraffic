package com.hightraffic.ecommerce.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class GatewayConfiguration {
    
    /**
     * Additional custom routes can be defined here programmatically
     * The main routes are defined in application.yml
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // API Documentation route
                .route("api-docs", r -> r
                        .path("/v3/api-docs/**")
                        .filters(f -> f
                                .rewritePath("/v3/api-docs/(?<service>.*)", "/$\\{service}/v3/api-docs")
                                .circuitBreaker(config -> config
                                        .setName("api-docs")
                                        .setFallbackUri("forward:/fallback/api-docs")))
                        .uri("lb://"))
                
                // Swagger UI route
                .route("swagger-ui", r -> r
                        .path("/swagger-ui/**")
                        .filters(f -> f
                                .rewritePath("/swagger-ui/(?<service>.*)", "/$\\{service}/swagger-ui"))
                        .uri("lb://"))
                
                .build();
    }
    
    /**
     * Fallback endpoints for circuit breaker
     */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoutes() {
        return RouterFunctions
                .route(RequestPredicates.path("/fallback/inventory"),
                        request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(createFallbackResponse("Inventory Service"))))
                
                .andRoute(RequestPredicates.path("/fallback/order"),
                        request -> ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(createFallbackResponse("Order Service"))))
                
                .andRoute(RequestPredicates.path("/fallback/api-docs"),
                        request -> ServerResponse.ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(BodyInserters.fromValue(createApiDocsFallback())));
    }
    
    private Map<String, Object> createFallbackResponse(String serviceName) {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", serviceName + " is temporarily unavailable. Please try again later.");
        response.put("path", null);
        return response;
    }
    
    private Map<String, Object> createApiDocsFallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("openapi", "3.0.1");
        response.put("info", Map.of(
                "title", "API Documentation Unavailable",
                "version", "1.0.0",
                "description", "API documentation is temporarily unavailable"
        ));
        response.put("paths", new HashMap<>());
        return response;
    }
}