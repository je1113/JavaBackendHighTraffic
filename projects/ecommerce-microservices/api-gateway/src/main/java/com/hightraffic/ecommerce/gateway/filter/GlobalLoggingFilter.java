package com.hightraffic.ecommerce.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
public class GlobalLoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalLoggingFilter.class);
    private static final String REQUEST_TIME_ATTR = "requestTime";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = getOrCreateCorrelationId(exchange);
        
        // Add correlation ID to request headers
        ServerHttpRequest modifiedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();
        
        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(modifiedRequest)
                .build();
        
        // Store request time
        modifiedExchange.getAttributes().put(REQUEST_TIME_ATTR, Instant.now());
        
        // Log request details
        logRequest(modifiedExchange, correlationId);
        
        return chain.filter(modifiedExchange)
                .then(Mono.fromRunnable(() -> logResponse(modifiedExchange, correlationId)));
    }
    
    private String getOrCreateCorrelationId(ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }
    
    private void logRequest(ServerWebExchange exchange, String correlationId) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().toString();
        String queryParams = request.getQueryParams().toString();
        URI routeUri = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        
        logger.info("Incoming request - CorrelationId: {}, Method: {}, Path: {}, QueryParams: {}, RouteUri: {}",
                correlationId, method, path, queryParams, routeUri);
    }
    
    private void logResponse(ServerWebExchange exchange, String correlationId) {
        ServerHttpResponse response = exchange.getResponse();
        HttpStatusCode statusCode = response.getStatusCode();
        Instant requestTime = exchange.getAttribute(REQUEST_TIME_ATTR);
        
        if (requestTime != null) {
            long duration = Duration.between(requestTime, Instant.now()).toMillis();
            
            if (statusCode != null && statusCode.is2xxSuccessful()) {
                logger.info("Outgoing response - CorrelationId: {}, Status: {}, Duration: {}ms",
                        correlationId, statusCode.value(), duration);
            } else {
                logger.warn("Outgoing response - CorrelationId: {}, Status: {}, Duration: {}ms",
                        correlationId, statusCode != null ? statusCode.value() : "unknown", duration);
            }
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}