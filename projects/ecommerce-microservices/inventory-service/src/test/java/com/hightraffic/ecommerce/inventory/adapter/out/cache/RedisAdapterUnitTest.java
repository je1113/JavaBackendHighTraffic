package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.hightraffic.ecommerce.inventory.application.port.out.CachePort;
import com.hightraffic.ecommerce.inventory.config.RedisConfiguration;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.Stock;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisAdapterUnitTest {
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @Mock
    private SetOperations<String, Object> setOperations;
    
    private RedisAdapter redisAdapter;
    private RedisConfiguration.CacheProperties cacheProperties;
    
    private Product testProduct;
    private ProductId testProductId;
    
    @BeforeEach
    void setUp() {
        cacheProperties = new RedisConfiguration.CacheProperties();
        cacheProperties.setDefaultTtl(Duration.ofMinutes(30));
        cacheProperties.setKeyPrefix("inventory:");
        cacheProperties.setEnableStatistics(true);
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.getOperations()).thenReturn(redisTemplate);
        
        redisAdapter = new RedisAdapter(redisTemplate, cacheProperties);
        
        // Create test product
        testProductId = new ProductId("TEST-001");
        Stock stock = new Stock(
            new StockQuantity(BigDecimal.valueOf(100)),
            new StockQuantity(BigDecimal.valueOf(10))
        );
        testProduct = new Product(testProductId, "Test Product", stock);
    }
    
    @Test
    void shouldCacheProduct() {
        // Given
        Duration ttl = Duration.ofMinutes(5);
        
        // When
        redisAdapter.cacheProduct(testProduct, ttl);
        
        // Then
        verify(valueOperations).set(eq("inventory:product:TEST-001"), eq(testProduct), eq(ttl));
    }
    
    @Test
    void shouldGetCachedProduct() {
        // Given
        when(valueOperations.get("inventory:product:TEST-001")).thenReturn(testProduct);
        
        // When
        Optional<Product> result = redisAdapter.getCachedProduct(testProductId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(testProductId);
        assertThat(result.get().getName()).isEqualTo("Test Product");
    }
    
    @Test
    void shouldReturnEmptyWhenProductNotCached() {
        // Given
        when(valueOperations.get("inventory:product:TEST-001")).thenReturn(null);
        
        // When
        Optional<Product> result = redisAdapter.getCachedProduct(testProductId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldEvictProduct() {
        // Given
        when(redisTemplate.delete("inventory:product:TEST-001")).thenReturn(true);
        
        // When
        redisAdapter.evictProduct(testProductId);
        
        // Then
        verify(redisTemplate).delete("inventory:product:TEST-001");
    }
    
    @Test
    void shouldCacheMultipleProducts() {
        // Given
        Product product2 = new Product(new ProductId("TEST-002"), "Test Product 2", 
            new Stock(new StockQuantity(BigDecimal.valueOf(50)), new StockQuantity(BigDecimal.valueOf(5))));
        List<Product> products = Arrays.asList(testProduct, product2);
        Duration ttl = Duration.ofMinutes(10);
        
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("inventory:product:TEST-001", testProduct);
        expectedMap.put("inventory:product:TEST-002", product2);
        
        // When
        redisAdapter.cacheProducts(products, ttl);
        
        // Then
        verify(valueOperations).multiSet(expectedMap);
        verify(redisTemplate, times(2)).expire(anyString(), eq(ttl));
    }
    
    @Test
    void shouldCacheStockQuantity() {
        // Given
        CachePort.StockQuantityCache stockQuantity = new CachePort.StockQuantityCache(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            LocalDateTime.now()
        );
        Duration ttl = Duration.ofMinutes(5);
        
        // When
        redisAdapter.cacheStockQuantity(testProductId, stockQuantity, ttl);
        
        // Then
        verify(valueOperations).set(eq("inventory:stock:TEST-001"), eq(stockQuantity), eq(ttl));
    }
    
    @Test
    void shouldGetCachedStockQuantity() {
        // Given
        CachePort.StockQuantityCache stockQuantity = new CachePort.StockQuantityCache(
            BigDecimal.valueOf(100),
            BigDecimal.valueOf(10),
            LocalDateTime.now()
        );
        when(valueOperations.get("inventory:stock:TEST-001")).thenReturn(stockQuantity);
        
        // When
        Optional<CachePort.StockQuantityCache> result = redisAdapter.getCachedStockQuantity(testProductId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().availableQuantity()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(result.get().reservedQuantity()).isEqualTo(BigDecimal.valueOf(10));
    }
    
    @Test
    void shouldCacheHotItems() {
        // Given
        Set<ProductId> hotItems = Set.of(
            new ProductId("HOT-001"),
            new ProductId("HOT-002")
        );
        Duration ttl = Duration.ofMinutes(15);
        
        // When
        redisAdapter.cacheHotItems(hotItems, ttl);
        
        // Then
        verify(redisTemplate).delete("inventory:hot_items");
        verify(setOperations).add(eq("inventory:hot_items"), eq("HOT-001"), eq("HOT-002"));
        verify(redisTemplate).expire("inventory:hot_items", ttl);
    }
    
    @Test
    void shouldGetHotItems() {
        // Given
        Set<Object> hotItemValues = Set.of("HOT-001", "HOT-002");
        when(setOperations.members("inventory:hot_items")).thenReturn(hotItemValues);
        
        // When
        Set<ProductId> result = redisAdapter.getHotItems();
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result).contains(
            new ProductId("HOT-001"),
            new ProductId("HOT-002")
        );
    }
    
    @Test
    void shouldClearAllCache() {
        // Given
        Set<String> keys = Set.of("inventory:product:TEST-001", "inventory:stock:TEST-001");
        when(redisTemplate.keys("inventory:*")).thenReturn(keys);
        
        // When
        redisAdapter.clearAll();
        
        // Then
        verify(redisTemplate).delete(keys);
    }
    
    @Test
    void shouldProvideAccurateStats() {
        // Given
        Set<String> keys = Set.of("inventory:product:TEST-001", "inventory:stock:TEST-001");
        when(redisTemplate.keys("inventory:*")).thenReturn(keys);
        
        // When
        CachePort.CacheStats stats = redisAdapter.getStats();
        
        // Then
        assertThat(stats.size()).isEqualTo(2);
        assertThat(stats.hitCount()).isEqualTo(0);
        assertThat(stats.missCount()).isEqualTo(0);
        assertThat(stats.evictionCount()).isEqualTo(0);
        assertThat(stats.hitRate()).isEqualTo(0.0);
    }
    
    @Test
    void shouldHandleExceptionsGracefully() {
        // Given
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));
        
        // When
        Optional<Product> result = redisAdapter.getCachedProduct(testProductId);
        
        // Then
        assertThat(result).isEmpty();
    }
}