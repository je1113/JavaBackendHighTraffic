package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.CachePort;
import com.hightraffic.ecommerce.inventory.config.RedisConfiguration;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.Stock;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RedisAdapterTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", redis::getFirstMappedPort);
    }
    
    @Autowired
    private RedisAdapter redisAdapter;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    private Product testProduct;
    private ProductId testProductId;
    
    @BeforeEach
    void setUp() {
        // Clear Redis before each test
        redisTemplate.getConnectionFactory().getConnection().flushAll();
        
        // Create test product
        testProductId = new ProductId("TEST-001");
        Stock stock = new Stock(
            new StockQuantity(BigDecimal.valueOf(100)),
            new StockQuantity(BigDecimal.valueOf(10))
        );
        testProduct = new Product(testProductId, "Test Product", stock);
    }
    
    @Test
    void shouldCacheAndRetrieveProduct() {
        // Given
        Duration ttl = Duration.ofMinutes(5);
        
        // When
        redisAdapter.cacheProduct(testProduct, ttl);
        Optional<Product> cachedProduct = redisAdapter.getCachedProduct(testProductId);
        
        // Then
        assertThat(cachedProduct).isPresent();
        assertThat(cachedProduct.get().getId()).isEqualTo(testProductId);
        assertThat(cachedProduct.get().getName()).isEqualTo("Test Product");
    }
    
    @Test
    void shouldReturnEmptyWhenProductNotInCache() {
        // Given
        ProductId nonExistentId = new ProductId("NON-EXISTENT");
        
        // When
        Optional<Product> cachedProduct = redisAdapter.getCachedProduct(nonExistentId);
        
        // Then
        assertThat(cachedProduct).isEmpty();
    }
    
    @Test
    void shouldCacheAndRetrieveMultipleProducts() {
        // Given
        Product product2 = new Product(new ProductId("TEST-002"), "Test Product 2", 
            new Stock(new StockQuantity(BigDecimal.valueOf(50)), new StockQuantity(BigDecimal.valueOf(5))));
        Product product3 = new Product(new ProductId("TEST-003"), "Test Product 3", 
            new Stock(new StockQuantity(BigDecimal.valueOf(75)), new StockQuantity(BigDecimal.valueOf(7))));
        
        List<Product> products = Arrays.asList(testProduct, product2, product3);
        Duration ttl = Duration.ofMinutes(10);
        
        // When
        redisAdapter.cacheProducts(products, ttl);
        
        Set<ProductId> productIds = Set.of(testProductId, product2.getId(), product3.getId());
        List<Product> cachedProducts = redisAdapter.getCachedProducts(productIds);
        
        // Then
        assertThat(cachedProducts).hasSize(3);
        assertThat(cachedProducts).extracting(Product::getId)
            .containsExactlyInAnyOrder(testProductId, product2.getId(), product3.getId());
    }
    
    @Test
    void shouldEvictProduct() {
        // Given
        redisAdapter.cacheProduct(testProduct, Duration.ofMinutes(5));
        
        // When
        redisAdapter.evictProduct(testProductId);
        Optional<Product> cachedProduct = redisAdapter.getCachedProduct(testProductId);
        
        // Then
        assertThat(cachedProduct).isEmpty();
    }
    
    @Test
    void shouldEvictMultipleProducts() {
        // Given
        Product product2 = new Product(new ProductId("TEST-002"), "Test Product 2", 
            new Stock(new StockQuantity(BigDecimal.valueOf(50)), new StockQuantity(BigDecimal.valueOf(5))));
        
        redisAdapter.cacheProduct(testProduct, Duration.ofMinutes(5));
        redisAdapter.cacheProduct(product2, Duration.ofMinutes(5));
        
        // When
        Set<ProductId> productIds = Set.of(testProductId, product2.getId());
        redisAdapter.evictProducts(productIds);
        
        // Then
        assertThat(redisAdapter.getCachedProduct(testProductId)).isEmpty();
        assertThat(redisAdapter.getCachedProduct(product2.getId())).isEmpty();
    }
    
    @Test
    void shouldCacheAndRetrieveStockQuantity() {
        // Given
        CachePort.StockQuantityCache stockQuantity = new CachePort.StockQuantityCache(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            LocalDateTime.now()
        );
        Duration ttl = Duration.ofMinutes(5);
        
        // When
        redisAdapter.cacheStockQuantity(testProductId, stockQuantity, ttl);
        Optional<CachePort.StockQuantityCache> cachedStock = redisAdapter.getCachedStockQuantity(testProductId);
        
        // Then
        assertThat(cachedStock).isPresent();
        assertThat(cachedStock.get().availableQuantity()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(cachedStock.get().reservedQuantity()).isEqualTo(BigDecimal.valueOf(10));
    }
    
    @Test
    void shouldEvictStockQuantity() {
        // Given
        CachePort.StockQuantityCache stockQuantity = new CachePort.StockQuantityCache(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            LocalDateTime.now()
        );
        redisAdapter.cacheStockQuantity(testProductId, stockQuantity, Duration.ofMinutes(5));
        
        // When
        redisAdapter.evictStockQuantity(testProductId);
        Optional<CachePort.StockQuantityCache> cachedStock = redisAdapter.getCachedStockQuantity(testProductId);
        
        // Then
        assertThat(cachedStock).isEmpty();
    }
    
    @Test
    void shouldCacheAndRetrieveHotItems() {
        // Given
        Set<ProductId> hotItems = Set.of(
            new ProductId("HOT-001"),
            new ProductId("HOT-002"),
            new ProductId("HOT-003")
        );
        Duration ttl = Duration.ofMinutes(15);
        
        // When
        redisAdapter.cacheHotItems(hotItems, ttl);
        Set<ProductId> cachedHotItems = redisAdapter.getHotItems();
        
        // Then
        assertThat(cachedHotItems).hasSize(3);
        assertThat(cachedHotItems).containsAll(hotItems);
    }
    
    @Test
    void shouldWarmCache() throws ExecutionException, InterruptedException {
        // Given
        Product product2 = new Product(new ProductId("TEST-002"), "Test Product 2", 
            new Stock(new StockQuantity(BigDecimal.valueOf(50)), new StockQuantity(BigDecimal.valueOf(5))));
        List<Product> products = Arrays.asList(testProduct, product2);
        
        // When
        CompletableFuture<Void> future = redisAdapter.warmCache(products);
        future.get(); // Wait for completion
        
        // Then
        assertThat(redisAdapter.getCachedProduct(testProductId)).isPresent();
        assertThat(redisAdapter.getCachedProduct(product2.getId())).isPresent();
    }
    
    @Test
    void shouldClearAllCache() {
        // Given
        redisAdapter.cacheProduct(testProduct, Duration.ofMinutes(5));
        redisAdapter.cacheHotItems(Set.of(testProductId), Duration.ofMinutes(5));
        
        // When
        redisAdapter.clearAll();
        
        // Then
        assertThat(redisAdapter.getCachedProduct(testProductId)).isEmpty();
        assertThat(redisAdapter.getHotItems()).isEmpty();
    }
    
    @Test
    void shouldProvideAccurateCacheStats() {
        // Given
        redisAdapter.cacheProduct(testProduct, Duration.ofMinutes(5));
        
        // When - Generate some hits and misses
        redisAdapter.getCachedProduct(testProductId); // Hit
        redisAdapter.getCachedProduct(testProductId); // Hit
        redisAdapter.getCachedProduct(new ProductId("NON-EXISTENT")); // Miss
        
        CachePort.CacheStats stats = redisAdapter.getStats();
        
        // Then
        assertThat(stats.hitCount()).isEqualTo(2);
        assertThat(stats.missCount()).isEqualTo(1);
        assertThat(stats.hitRate()).isEqualTo(2.0 / 3.0);
        assertThat(stats.size()).isGreaterThan(0);
    }
    
    @Test
    void shouldUpdateStatsOnEviction() {
        // Given
        redisAdapter.cacheProduct(testProduct, Duration.ofMinutes(5));
        
        // When
        redisAdapter.evictProduct(testProductId);
        CachePort.CacheStats stats = redisAdapter.getStats();
        
        // Then
        assertThat(stats.evictionCount()).isEqualTo(1);
    }
}