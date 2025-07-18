package com.hightraffic.ecommerce.discovery.health;

import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class EurekaHealthIndicator implements HealthIndicator {

    private final EurekaServerContext eurekaServerContext;

    public EurekaHealthIndicator(EurekaServerContext eurekaServerContext) {
        this.eurekaServerContext = eurekaServerContext;
    }

    @Override
    public Health health() {
        try {
            PeerAwareInstanceRegistry registry = eurekaServerContext.getRegistry();
            int registeredApps = registry.getApplications().size();
            int totalInstances = registry.getApplications().getRegisteredApplications().stream()
                .mapToInt(app -> app.getInstances().size())
                .sum();

            return Health.up()
                .withDetail("registeredApplications", registeredApps)
                .withDetail("totalInstances", totalInstances)
                .withDetail("status", "Eureka Server is running")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .withDetail("status", "Eureka Server is not available")
                .build();
        }
    }
}