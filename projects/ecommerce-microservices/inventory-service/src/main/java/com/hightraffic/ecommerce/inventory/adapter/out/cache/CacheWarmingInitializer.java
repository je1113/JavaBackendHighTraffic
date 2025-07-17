package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductsByConditionPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Initializes cache warming on application startup
 * Preloads frequently accessed data to improve initial performance
 */
@Component
public class CacheWarmingInitializer {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheWarmingInitializer.class);
    
    private final RedisAdapter redisAdapter;
    private final LoadProductsByConditionPort loadProductsByConditionPort;
    private final Environment environment;
    
    public CacheWarmingInitializer(RedisAdapter redisAdapter,
                                  LoadProductsByConditionPort loadProductsByConditionPort,
                                  Environment environment) {
        this.redisAdapter = redisAdapter;
        this.loadProductsByConditionPort = loadProductsByConditionPort;
        this.environment = environment;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!isWarmingEnabled()) {
            logger.info("Cache warming is disabled");
            return;
        }
        
        logger.info("Starting cache warming process...");
        
        CompletableFuture<Void> warmingFuture = CompletableFuture.runAsync(() -> {
            try {
                // Load products with low stock for warming
                // Using StockQuantity.of(50) as threshold, including inactive products, limit 100
                List<Product> lowStockProducts = loadProductsByConditionPort.loadLowStockProducts(
                    StockQuantity.of(50), 
                    true,  // include inactive products for comprehensive warming
                    100    // limit to 100 products
                );
                
                if (!lowStockProducts.isEmpty()) {
                    redisAdapter.warmCache(lowStockProducts)
                        .thenRun(() -> logger.info("Warmed cache with {} low stock products", 
                            lowStockProducts.size()))
                        .exceptionally(throwable -> {
                            logger.error("Failed to warm cache for low stock products", throwable);
                            return null;
                        });
                }
                
                // Note: Hot products functionality would need to be implemented separately
                // as the LoadProductsByConditionPort doesn't have a method for finding hot products
                // This could be based on order history or access patterns
                logger.info("Hot products cache warming not implemented - requires order history analysis");
                
            } catch (Exception e) {
                logger.error("Cache warming failed", e);
            }
        });
        
        // Log completion asynchronously
        warmingFuture.thenRun(() -> logger.info("Cache warming process completed"));
    }
    
    private boolean isWarmingEnabled() {
        return environment.getProperty("cache.warming.enabled", Boolean.class, true);
    }
}