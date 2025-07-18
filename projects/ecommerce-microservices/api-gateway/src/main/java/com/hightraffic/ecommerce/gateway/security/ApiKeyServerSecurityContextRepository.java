package com.hightraffic.ecommerce.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ApiKeyServerSecurityContextRepository implements ServerSecurityContextRepository {
    
    @Value("${api-keys.header-name:X-API-Key}")
    private String apiKeyHeaderName;
    
    private final ApiKeyAuthenticationManager authenticationManager;
    
    public ApiKeyServerSecurityContextRepository(ApiKeyAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }
    
    @Override
    public Mono<Void> save(ServerWebExchange exchange, SecurityContext context) {
        return Mono.empty();
    }
    
    @Override
    public Mono<SecurityContext> load(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(apiKeyHeaderName);
        
        if (apiKey != null && !apiKey.isEmpty()) {
            Authentication auth = new UsernamePasswordAuthenticationToken(apiKey, apiKey);
            
            return authenticationManager.authenticate(auth)
                    .map(SecurityContextImpl::new)
                    .cast(SecurityContext.class);
        }
        
        return Mono.empty();
    }
}