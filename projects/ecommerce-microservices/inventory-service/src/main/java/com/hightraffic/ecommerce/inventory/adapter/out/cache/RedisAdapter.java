package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.CachePort;
import com.hightraffic.ecommerce.inventory.config.RedisConfiguration;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class RedisAdapter implements CachePort {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisAdapter.class);
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfiguration.CacheProperties cacheProperties;
    private final MeterRegistry meterRegistry;
    private final ExecutorService cacheWarmingExecutor;
    
    // Cache statistics
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);
    
    // Cache key prefixes
    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String STOCK_QUANTITY_KEY_PREFIX = "stock:";
    private static final String HOT_ITEMS_KEY = "hot_items";
    private static final String CACHE_VERSION_PREFIX = "version:";
    private static final String CACHE_LOCK_PREFIX = "lock:";
    
    // Cache access patterns tracking
    private final Map<String, AccessPattern> accessPatterns = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheEvictionCounter;
    private final Timer cacheOperationTimer;
    
    // Lua scripts for atomic operations
    private final DefaultRedisScript<Boolean> conditionalSetScript;
    private final DefaultRedisScript<Long> bulkEvictScript;
    
    public RedisAdapter(RedisTemplate<String, Object> redisTemplate,
                       RedisConfiguration.CacheProperties cacheProperties,
                       MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.cacheProperties = cacheProperties;
        this.meterRegistry = meterRegistry;
        this.cacheWarmingExecutor = Executors.newFixedThreadPool(
            cacheProperties.getWarmingThreads() != null ? cacheProperties.getWarmingThreads() : 4
        );
        
        // Initialize metrics
        this.cacheHitCounter = Counter.builder("cache.hits")
            .description("Number of cache hits")
            .tag("cache", "inventory")
            .register(meterRegistry);
            
        this.cacheMissCounter = Counter.builder("cache.misses")
            .description("Number of cache misses")
            .tag("cache", "inventory")
            .register(meterRegistry);
            
        this.cacheEvictionCounter = Counter.builder("cache.evictions")
            .description("Number of cache evictions")
            .tag("cache", "inventory")
            .register(meterRegistry);
            
        this.cacheOperationTimer = Timer.builder("cache.operation.duration")
            .description("Cache operation duration")
            .tag("cache", "inventory")
            .register(meterRegistry);
            
        // Initialize Lua scripts
        this.conditionalSetScript = new DefaultRedisScript<>();
        conditionalSetScript.setScriptText(
            "local key = KEYS[1]\n" +
            "local value = ARGV[1]\n" +
            "local ttl = ARGV[2]\n" +
            "local version = ARGV[3]\n" +
            "local currentVersion = redis.call('GET', key .. ':version')\n" +
            "if currentVersion == false or currentVersion < version then\n" +
            "  redis.call('SET', key, value, 'EX', ttl)\n" +
            "  redis.call('SET', key .. ':version', version, 'EX', ttl)\n" +
            "  return true\n" +
            "else\n" +
            "  return false\n" +
            "end"
        );
        conditionalSetScript.setResultType(Boolean.class);
        
        this.bulkEvictScript = new DefaultRedisScript<>();
        bulkEvictScript.setScriptText(
            "local count = 0\n" +
            "for i, key in ipairs(KEYS) do\n" +
            "  if redis.call('DEL', key) == 1 then\n" +
            "    count = count + 1\n" +
            "  end\n" +
            "  redis.call('DEL', key .. ':version')\n" +
            "end\n" +
            "return count"
        );
        bulkEvictScript.setResultType(Long.class);
    }
    
    @Override
    public void cacheProduct(Product product, Duration ttl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String key = buildProductKey(product.getProductId());
            
            // Use conditional set with version control
            long version = System.currentTimeMillis();
            Boolean success = redisTemplate.execute(conditionalSetScript,
                Collections.singletonList(key),
                product,
                ttl.getSeconds(),
                version
            );
            
            if (Boolean.TRUE.equals(success)) {
                // Track access pattern
                trackAccess(key);
                logger.debug("Cached product: {} with version: {}", product.getProductId(), version);
            } else {
                logger.debug("Skipped caching product: {} (older version)", product.getProductId());
            }
        } catch (Exception e) {
            logger.error("Failed to cache product: {}", product.getProductId(), e);
        } finally {
            sample.stop(cacheOperationTimer);
        }
    }
    
    @Override
    public void cacheProducts(List<Product> products, Duration ttl) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Use pipeline for better performance
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                long version = System.currentTimeMillis();
                
                for (Product product : products) {
                    String key = buildProductKey(product.getProductId());
                    byte[] keyBytes = key.getBytes();
                    byte[] valueBytes = serialize(product);
                    
                    connection.setEx(keyBytes, ttl.getSeconds(), valueBytes);
                    connection.setEx((key + ":version").getBytes(), 
                        ttl.getSeconds(), 
                        String.valueOf(version).getBytes());
                    
                    // Track access pattern
                    trackAccess(key);
                }
                return null;
            });
            
            logger.debug("Cached {} products in batch", products.size());
        } catch (Exception e) {
            logger.error("Failed to cache products", e);
        } finally {
            sample.stop(cacheOperationTimer);
        }
    }
    
    @Override
    public Optional<Product> getCachedProduct(ProductId productId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String key = buildProductKey(productId);
            Product product = (Product) redisTemplate.opsForValue().get(key);
            
            if (product != null) {
                hitCount.incrementAndGet();
                cacheHitCounter.increment();
                trackAccess(key);
                
                // Async refresh if near expiration
                asyncRefreshIfNeeded(key, productId);
                
                logger.debug("Cache hit for product: {}", productId);
                return Optional.of(product);
            } else {
                missCount.incrementAndGet();
                cacheMissCounter.increment();
                logger.debug("Cache miss for product: {}", productId);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.error("Failed to get cached product: {}", productId, e);
            missCount.incrementAndGet();
            cacheMissCounter.increment();
            return Optional.empty();
        } finally {
            sample.stop(cacheOperationTimer);
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
                // Batch process for efficient warming
                int batchSize = cacheProperties.getWarmingBatchSize() != null ? 
                    cacheProperties.getWarmingBatchSize() : 100;
                
                List<List<Product>> batches = partition(products, batchSize);
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                for (List<Product> batch : batches) {
                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        cacheProducts(batch, cacheProperties.getDefaultTtl());
                        logger.debug("Warmed cache batch of {} products", batch.size());
                    }, cacheWarmingExecutor);
                    futures.add(future);
                }
                
                // Wait for all batches to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                
                logger.info("Cache warming completed for {} products in {} batches", 
                    products.size(), batches.size());
                    
                // Mark hot items based on warming
                Set<ProductId> hotItemIds = products.stream()
                    .map(Product::getProductId)
                    .collect(Collectors.toSet());
                cacheHotItems(hotItemIds, cacheProperties.getHotItemsTtl());
                
            } catch (Exception e) {
                logger.error("Failed to warm cache", e);
                throw new RuntimeException("Cache warming failed", e);
            }
        }, cacheWarmingExecutor);
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
    
    // Helper methods for advanced features
    
    private void trackAccess(String key) {
        accessPatterns.compute(key, (k, pattern) -> {
            if (pattern == null) {
                return new AccessPattern(1, LocalDateTime.now());
            }
            pattern.increment();
            return pattern;
        });
    }
    
    private void asyncRefreshIfNeeded(String key, ProductId productId) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0 && ttl < cacheProperties.getRefreshThreshold()) {
            CompletableFuture.runAsync(() -> {
                logger.debug("Async refresh triggered for product: {} (TTL: {}s)", productId, ttl);
                // Note: Actual refresh logic should be implemented by the service layer
            }, cacheWarmingExecutor);
        }
    }
    
    private byte[] serialize(Object object) {
        try {
            @SuppressWarnings("unchecked")
            RedisSerializer<Object> serializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
            return serializer.serialize(object);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
    
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }
    
    // Scheduled tasks for cache maintenance
    
    @Scheduled(fixedDelayString = "${cache.maintenance.interval:300000}") // 5 minutes
    public void performCacheMaintenance() {
        try {
            // Clean up old access patterns
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            accessPatterns.entrySet().removeIf(entry -> 
                entry.getValue().getLastAccessed().isBefore(cutoff));
            
            // Log cache statistics
            CacheStats stats = getStats();
            logger.info("Cache stats - Hits: {}, Misses: {}, Hit Rate: {:.2f}%, Size: {}", 
                stats.hitCount(), stats.missCount(), stats.hitRate() * 100, stats.size());
                
            // Identify and update hot items
            updateHotItems();
            
        } catch (Exception e) {
            logger.error("Cache maintenance failed", e);
        }
    }
    
    private void updateHotItems() {
        // Find top accessed items
        int hotItemThreshold = cacheProperties.getHotItemThreshold() != null ? 
            cacheProperties.getHotItemThreshold() : 10;
            
        Set<ProductId> hotItems = accessPatterns.entrySet().stream()
            .filter(entry -> entry.getValue().getAccessCount() > hotItemThreshold)
            .map(entry -> {
                String key = entry.getKey();
                if (key.startsWith(cacheProperties.getKeyPrefix() + PRODUCT_KEY_PREFIX)) {
                    String productId = key.substring(
                        (cacheProperties.getKeyPrefix() + PRODUCT_KEY_PREFIX).length()
                    );
                    return ProductId.of(productId);
                }
                return null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
            
        if (!hotItems.isEmpty()) {
            cacheHotItems(hotItems, cacheProperties.getHotItemsTtl());
            logger.info("Updated {} hot items", hotItems.size());
        }
    }
    
    // Shutdown hook
    public void shutdown() {
        try {
            cacheWarmingExecutor.shutdown();
            if (!cacheWarmingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cacheWarmingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cacheWarmingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Inner classes
    
    private static class AccessPattern {
        private long accessCount;
        private LocalDateTime lastAccessed;
        
        public AccessPattern(long accessCount, LocalDateTime lastAccessed) {
            this.accessCount = accessCount;
            this.lastAccessed = lastAccessed;
        }
        
        public void increment() {
            this.accessCount++;
            this.lastAccessed = LocalDateTime.now();
        }
        
        public long getAccessCount() {
            return accessCount;
        }
        
        public LocalDateTime getLastAccessed() {
            return lastAccessed;
        }
    }
}