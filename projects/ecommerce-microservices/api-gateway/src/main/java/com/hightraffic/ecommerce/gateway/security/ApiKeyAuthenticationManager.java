package com.hightraffic.ecommerce.gateway.security;

import com.hightraffic.ecommerce.gateway.config.ApiKeyConfiguration;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Component
public class ApiKeyAuthenticationManager implements ReactiveAuthenticationManager {
    
    private final ApiKeyConfiguration apiKeyConfiguration;
    
    public ApiKeyAuthenticationManager(ApiKeyConfiguration apiKeyConfiguration) {
        this.apiKeyConfiguration = apiKeyConfiguration;
    }
    
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String apiKey = authentication.getName();
        
        return apiKeyConfiguration.findByKey(apiKey)
                .map(apiKeyInfo -> {
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_API_CLIENT"));
                    
                    return (Authentication) new UsernamePasswordAuthenticationToken(
                            apiKeyInfo.getName(),
                            apiKey,
                            authorities
                    );
                })
                .cast(Authentication.class);
    }
}