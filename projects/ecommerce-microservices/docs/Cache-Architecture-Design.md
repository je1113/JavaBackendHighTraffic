# Cache Architecture Design

## Overview

The inventory service implements a sophisticated caching layer using Redis to handle high-traffic scenarios efficiently. The cache adapter provides advanced features including distributed cache invalidation, hot item tracking, cache warming, and comprehensive monitoring.

## Architecture Components

### 1. RedisAdapter
The core caching implementation that extends the basic CachePort interface with advanced features:

- **Version-based Conditional Caching**: Prevents stale data overwrites using timestamp-based versioning
- **Pipeline Operations**: Batch operations for improved performance
- **Access Pattern Tracking**: Monitors cache access patterns to identify hot items
- **Automatic Refresh**: Proactive cache refresh for items nearing expiration
- **Metrics Integration**: Comprehensive monitoring with Micrometer

### 2. Cache Invalidation System

#### Distributed Cache Invalidation
```java
// CacheInvalidationListener handles cross-instance cache synchronization
- INVALIDATE_PRODUCT: Single product invalidation
- INVALIDATE_PRODUCTS: Batch product invalidation  
- INVALIDATE_STOCK: Stock-specific invalidation
- INVALIDATE_ALL: Full cache clear
```

The system uses Redis Pub/Sub to propagate cache invalidation events across all service instances, ensuring cache consistency in a distributed deployment.

### 3. Cache Warming Strategy

#### CacheWarmingInitializer
Automatically preloads frequently accessed data on application startup:

1. **Low Stock Products**: Products with stock below threshold
2. **Hot Products**: Frequently accessed items based on historical data
3. **Batch Processing**: Efficient parallel loading with configurable batch sizes
4. **Asynchronous Execution**: Non-blocking startup process

## Key Features

### 1. Multi-Level TTL Management
```yaml
cache:
  redis:
    default-ttl: PT30M      # Default 30 minutes
    product-ttl: PT10M      # Product data: 10 minutes
    stock-ttl: PT5M         # Stock data: 5 minutes (more volatile)
    hot-items-ttl: PT1H     # Hot items list: 1 hour
```

### 2. Access Pattern Tracking
The system tracks access patterns to identify hot items:

```java
private void trackAccess(String key) {
    accessPatterns.compute(key, (k, pattern) -> {
        if (pattern == null) {
            return new AccessPattern(1, LocalDateTime.now());
        }
        pattern.increment();
        return pattern;
    });
}
```

### 3. Automatic Hot Item Detection
A scheduled task analyzes access patterns and updates the hot items list:

```java
@Scheduled(fixedDelayString = "${cache.maintenance.interval:300000}")
public void performCacheMaintenance() {
    // Clean old patterns
    // Update hot items
    // Log statistics
}
```

### 4. Near-Expiry Refresh
Items close to expiration are automatically refreshed to prevent cache misses:

```java
private void asyncRefreshIfNeeded(String key, ProductId productId) {
    Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
    if (ttl != null && ttl > 0 && ttl < cacheProperties.getRefreshThreshold()) {
        // Trigger async refresh
    }
}
```

## Performance Optimizations

### 1. Pipeline Operations
Batch operations use Redis pipelines for improved throughput:

```java
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    // Batch operations
});
```

### 2. Lua Scripts
Atomic operations using Lua scripts ensure consistency:

- **Conditional Set**: Version-based updates
- **Bulk Eviction**: Efficient batch deletions

### 3. Connection Pooling
Optimized Lettuce connection pool configuration:

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

## Monitoring and Metrics

### 1. Cache Metrics
- **Hit Rate**: Percentage of successful cache hits
- **Miss Count**: Number of cache misses
- **Eviction Count**: Number of items evicted
- **Operation Duration**: Time taken for cache operations

### 2. Health Indicators
The CacheHealthIndicator monitors:
- Redis connectivity
- Response time
- Memory usage
- Key space statistics

### 3. Business Metrics
- Hot items identification
- Cache size monitoring
- Access pattern analysis

## Configuration

### Required Properties
```yaml
cache:
  redis:
    key-prefix: "inventory:"         # Namespace prefix
    enable-statistics: true          # Enable metrics collection
    warming-threads: 4               # Thread pool size for warming
    warming-batch-size: 100         # Batch size for warming
    hot-item-threshold: 10          # Access count threshold
    refresh-threshold: 60           # Seconds before expiry to refresh
```

### Optional Properties
```yaml
cache:
  warming:
    enabled: true                   # Enable cache warming on startup
  maintenance:
    interval: 300000               # Maintenance task interval (ms)
```

## Usage Patterns

### 1. Basic Caching
```java
// Cache a product
cachePort.cacheProduct(product, Duration.ofMinutes(10));

// Retrieve from cache
Optional<Product> cached = cachePort.getCachedProduct(productId);
```

### 2. Batch Operations
```java
// Cache multiple products
cachePort.cacheProducts(products, Duration.ofMinutes(10));

// Retrieve multiple
List<Product> cached = cachePort.getCachedProducts(productIds);
```

### 3. Stock Quantity Caching
```java
// Cache lightweight stock data
StockQuantityCache stockCache = new StockQuantityCache(
    availableQuantity, reservedQuantity, LocalDateTime.now()
);
cachePort.cacheStockQuantity(productId, stockCache, Duration.ofMinutes(5));
```

### 4. Cache Warming
```java
// Warm cache with products
CompletableFuture<Void> future = cachePort.warmCache(products);
```

## Best Practices

### 1. TTL Strategy
- Use shorter TTLs for volatile data (stock quantities)
- Longer TTLs for stable data (product information)
- Consider business requirements for data freshness

### 2. Cache Key Design
- Use consistent prefixes for namespacing
- Include version information where applicable
- Keep keys short but descriptive

### 3. Error Handling
- Cache operations should never fail the main business logic
- Log errors but continue with database queries
- Monitor cache health and performance

### 4. Warming Strategy
- Warm cache during off-peak hours
- Focus on frequently accessed data
- Use batch operations for efficiency

## Troubleshooting

### Common Issues

1. **High Miss Rate**
   - Check if TTLs are too short
   - Verify warming is working correctly
   - Analyze access patterns

2. **Memory Issues**
   - Monitor Redis memory usage
   - Adjust eviction policies
   - Review key expiration settings

3. **Inconsistent Data**
   - Ensure distributed invalidation is working
   - Check network connectivity between instances
   - Verify Pub/Sub configuration

### Debug Commands
```bash
# Monitor cache operations
redis-cli MONITOR

# Check memory usage
redis-cli INFO memory

# View all keys
redis-cli --scan --pattern "inventory:*"

# Check TTL
redis-cli TTL "inventory:product:123"
```

## Future Enhancements

1. **Intelligent Prefetching**: Predict and preload data based on access patterns
2. **Multi-Region Support**: Cache replication across regions
3. **Advanced Eviction**: LFU/LRU with custom weights
4. **Cache Aside Pattern**: Automatic cache-aside implementation
5. **Circuit Breaker**: Fallback mechanisms for cache failures