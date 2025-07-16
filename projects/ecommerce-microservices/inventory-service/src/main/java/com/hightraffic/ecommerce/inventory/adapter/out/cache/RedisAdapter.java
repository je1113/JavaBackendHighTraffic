package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.CachePort;
import com.hightraffic.ecommerce.inventory.config.RedisConfiguration;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class RedisAdapter implements CachePort {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisAdapter.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfiguration.CacheProperties cacheProperties;
    
    // Cache statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    // Cache key prefixes
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String STOCK_QUANTITY_KEY_PREFIX = "stock:";
    private static final String HOT_ITEMS_KEY = "hot_items";
    
    public RedisAdapter(RedisTemplate<String, Object> redisTemplate,
                       RedisConfiguration.CacheProperties cacheProperties) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
    }
    
    @Override
    public void cacheProduct(Product product, Duration ttl) {
        try {
            String key = buildProductKey(product.getProductId());
            redisTemplate.opsForValue().set(key, product, ttl);
            logger.debug("Cached product: {}", product.getProductId());
        } catch (Exception e) {
            logger.error("Failed to cache product: {}", product.getProductId(), e);
        }
    }
    
    @Override
    public void cacheProducts(List<Product> products, Duration ttl) {
        try {
            Map<String, Object> productMap = products.stream()
                .collect(Collectors.toMap(
                    product -> buildProductKey(product.getProductId()),
                    product -> product
                ));
            
            redisTemplate.opsForValue().multiSet(productMap);
            
            // Set TTL for each key
            productMap.keySet().forEach(key -> redisTemplate.expire(key, ttl));
            
            logger.debug("Cached {} products", products.size());
        } catch (Exception e) {
            logger.error("Failed to cache products", e);
        }
    }
    
    @Override
    public Optional<Product> getCachedProduct(ProductId productId) {
        try {
            String key = buildProductKey(productId);
            Product product = (Product) redisTemplate.opsForValue().get(key);
            
            if (product != null) {
                hitCount.incrementAndGet();
                logger.debug("Cache hit for product: {}", productId);
                return Optional.of(product);
            } else {
                missCount.incrementAndGet();
                logger.debug("Cache miss for product: {}", productId);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Failed to get cached product: {}", productId, e);
            missCount.incrementAndGet();
            return Optional.empty();
        }
    }
    
    @Override
    public List<Product> getCachedProducts(Set<ProductId> productIds) {
        try {
            List<String> keys = productIds.stream()
                .map(this::buildProductKey)
                .collect(Collectors.toList());
            
            List<Object> values = redisTemplate.opsForValue().multiGet(keys);
            
            return values.stream()
                .filter(Objects::nonNull)
                .map(Product.class::cast)
                .peek(p -> hitCount.incrementAndGet())
                .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Failed to get cached products", e);
            return Collections.emptyList();
        }
    }
    
    @Override
    public void evictProduct(ProductId productId) {
        try {
            String key = buildProductKey(productId);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                evictionCount.incrementAndGet();
                logger.debug("Evicted product: {}", productId);
            }
        } catch (Exception e) {
            logger.error("Failed to evict product: {}", productId, e);
        }
    }
    
    @Override
    public void evictProducts(Set<ProductId> productIds) {
        try {
            List<String> keys = productIds.stream()
                .map(this::buildProductKey)
                .collect(Collectors.toList());
            
            Long deletedCount = redisTemplate.delete(keys);
            if (deletedCount != null && deletedCount > 0) {
                evictionCount.addAndGet(deletedCount);
                logger.debug("Evicted {} products", deletedCount);
            }
        } catch (Exception e) {
            logger.error("Failed to evict products", e);
        }
    }
    
    @Override
    public void cacheStockQuantity(ProductId productId, StockQuantityCache stockQuantity, Duration ttl) {
        try {
            String key = buildStockQuantityKey(productId);
            redisTemplate.opsForValue().set(key, stockQuantity, ttl);
            logger.debug("Cached stock quantity for product: {}", productId);
        } catch (Exception e) {
            logger.error("Failed to cache stock quantity for product: {}", productId, e);
        }
    }
    
    @Override
    public Optional<StockQuantityCache> getCachedStockQuantity(ProductId productId) {
        try {
            String key = buildStockQuantityKey(productId);
            StockQuantityCache stockQuantity = (StockQuantityCache) redisTemplate.opsForValue().get(key);
            
            if (stockQuantity != null) {
                hitCount.incrementAndGet();
                logger.debug("Cache hit for stock quantity: {}", productId);
                return Optional.of(stockQuantity);
            } else {
                missCount.incrementAndGet();
                logger.debug("Cache miss for stock quantity: {}", productId);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Failed to get cached stock quantity: {}", productId, e);
            missCount.incrementAndGet();
            return Optional.empty();
        }
    }
    
    @Override
    public void evictStockQuantity(ProductId productId) {
        try {
            String key = buildStockQuantityKey(productId);
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                evictionCount.incrementAndGet();
                logger.debug("Evicted stock quantity: {}", productId);
            }
        } catch (Exception e) {
            logger.error("Failed to evict stock quantity: {}", productId, e);
        }
    }
    
    @Override
    public void cacheHotItems(Set<ProductId> productIds, Duration ttl) {
        try {
            String key = buildHotItemsKey();
            redisTemplate.opsForSet().getOperations().delete(key);
            
            if (!productIds.isEmpty()) {
                Object[] productIdArray = productIds.stream()
                    .map(ProductId::getValue)
                    .toArray();
                redisTemplate.opsForSet().add(key, productIdArray);
                redisTemplate.expire(key, ttl);
            }
            
            logger.debug("Cached {} hot items", productIds.size());
        } catch (Exception e) {
            logger.error("Failed to cache hot items", e);
        }
    }
    
    @Override
    public Set<ProductId> getHotItems() {
        try {
            String key = buildHotItemsKey();
            Set<Object> hotItemValues = redisTemplate.opsForSet().members(key);
            
            if (hotItemValues != null) {
                return hotItemValues.stream()
                    .map(Object::toString)
                    .map(ProductId::of)
                    .collect(Collectors.toSet());
            }
            
            return Collections.emptySet();
        } catch (Exception e) {
            logger.error("Failed to get hot items", e);
            return Collections.emptySet();
        }
    }
    
    @Override
    public CompletableFuture<Void> warmCache(List<Product> products) {
        return CompletableFuture.runAsync(() -> {
            try {
                cacheProducts(products, cacheProperties.getDefaultTtl());
                logger.info("Cache warming completed for {} products", products.size());
            } catch (Exception e) {
                logger.error("Failed to warm cache", e);
                throw new RuntimeException("Cache warming failed", e);
            }
        });
    }
    
    @Override
    public void clearAll() {
        try {
            Set<String> keys = redisTemplate.keys(cacheProperties.getKeyPrefix() + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                logger.info("Cleared {} cache entries", keys.size());
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache", e);
        }
    }
    
    @Override
    public CacheStats getStats() {
        try {
            long size = 0;
            Set<String> keys = redisTemplate.keys(cacheProperties.getKeyPrefix() + "*");
            if (keys != null) {
                size = keys.size();
            }
            
            long totalRequests = hitCount.get() + missCount.get();
            double hitRate = totalRequests > 0 ? (double) hitCount.get() / totalRequests : 0.0;
            
            return new CacheStats(
                hitCount.get(),
                missCount.get(),
                evictionCount.get(),
                hitRate,
                size
            );
        } catch (Exception e) {
            logger.error("Failed to get cache stats", e);
            return new CacheStats(0, 0, 0, 0.0, 0);
        }
    }
    
    private String buildProductKey(ProductId productId) {
        return cacheProperties.getKeyPrefix() + PRODUCT_KEY_PREFIX + productId.getValue();
    }
    
    private String buildStockQuantityKey(ProductId productId) {
        return cacheProperties.getKeyPrefix() + STOCK_QUANTITY_KEY_PREFIX + productId.getValue();
    }
    
    private String buildHotItemsKey() {
        return cacheProperties.getKeyPrefix() + HOT_ITEMS_KEY;
    }
}