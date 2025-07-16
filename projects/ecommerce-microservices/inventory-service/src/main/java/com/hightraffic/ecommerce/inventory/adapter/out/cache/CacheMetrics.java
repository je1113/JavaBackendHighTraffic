package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.CachePort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CacheMetrics implements InfoContributor {
    
    private final CachePort cachePort;
    private final MeterRegistry meterRegistry;
    
    public CacheMetrics(CachePort cachePort, MeterRegistry meterRegistry) {
        this.cachePort = cachePort;
        this.meterRegistry = meterRegistry;
        
        registerMetrics();
    }
    
    private void registerMetrics() {
        Gauge.builder("cache.hit.count", this, metrics -> metrics.cachePort.getStats().hitCount())
            .description("Number of cache hits")
            .register(meterRegistry);
        
        Gauge.builder("cache.miss.count", this, metrics -> metrics.cachePort.getStats().missCount())
            .description("Number of cache misses")
            .register(meterRegistry);
        
        Gauge.builder("cache.eviction.count", this, metrics -> metrics.cachePort.getStats().evictionCount())
            .description("Number of cache evictions")
            .register(meterRegistry);
        
        Gauge.builder("cache.hit.rate", this, metrics -> metrics.cachePort.getStats().hitRate())
            .description("Cache hit rate")
            .register(meterRegistry);
        
        Gauge.builder("cache.size", this, metrics -> metrics.cachePort.getStats().size())
            .description("Current cache size")
            .register(meterRegistry);
    }
    
    @Override
    public void contribute(Info.Builder builder) {
        CachePort.CacheStats stats = cachePort.getStats();
        
        Map<String, Object> cacheInfo = new HashMap<>();
        cacheInfo.put("hitCount", stats.hitCount());
        cacheInfo.put("missCount", stats.missCount());
        cacheInfo.put("evictionCount", stats.evictionCount());
        cacheInfo.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
        cacheInfo.put("size", stats.size());
        
        builder.withDetail("cache", cacheInfo);
    }
}