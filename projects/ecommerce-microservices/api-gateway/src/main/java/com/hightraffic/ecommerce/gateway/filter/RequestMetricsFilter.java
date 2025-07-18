package com.hightraffic.ecommerce.gateway.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RequestMetricsFilter implements GlobalFilter, Ordered {
    
    private static final String GATEWAY_REQUEST_TIME = "gateway.request.time";
    private final MeterRegistry meterRegistry;
    
    public RequestMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getAttributes().put(GATEWAY_REQUEST_TIME, System.currentTimeMillis());
        
        return chain.filter(exchange).doFinally(signalType -> {
            Long startTime = exchange.getAttribute(GATEWAY_REQUEST_TIME);
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                recordMetrics(exchange, duration);
            }
        });
    }
    
    private void recordMetrics(ServerWebExchange exchange, long duration) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().toString();
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        String statusCode = status != null ? String.valueOf(status.value()) : "unknown";
        
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "unknown";
        
        // Record request count
        Counter.builder("gateway.requests.total")
                .description("Total number of gateway requests")
                .tag("method", method)
                .tag("status", statusCode)
                .tag("route", routeId)
                .tag("path", path)
                .register(meterRegistry)
                .increment();
        
        // Record request duration
        Timer.builder("gateway.request.duration")
                .description("Gateway request duration")
                .tag("method", method)
                .tag("status", statusCode)
                .tag("route", routeId)
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);
        
        // Record error count for non-2xx responses
        if (status != null && !status.is2xxSuccessful()) {
            Counter.builder("gateway.errors.total")
                    .description("Total number of gateway errors")
                    .tag("method", method)
                    .tag("status", statusCode)
                    .tag("route", routeId)
                    .register(meterRegistry)
                    .increment();
        }
        
        // Record slow requests (> 1 second)
        if (duration > 1000) {
            Counter.builder("gateway.slow.requests")
                    .description("Number of slow requests (>1s)")
                    .tag("method", method)
                    .tag("route", routeId)
                    .register(meterRegistry)
                    .increment();
        }
    }
    
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}