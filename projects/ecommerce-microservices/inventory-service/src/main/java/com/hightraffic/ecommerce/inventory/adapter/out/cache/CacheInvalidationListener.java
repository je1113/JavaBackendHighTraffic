package com.hightraffic.ecommerce.inventory.adapter.out.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Redis message listener for cache invalidation events
 * 
 * Handles distributed cache invalidation across multiple instances
 * by listening to Redis pub/sub messages
 */
@Component
public class CacheInvalidationListener implements MessageListener {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationListener.class);
    
    private final RedisAdapter redisAdapter;
    private final ObjectMapper objectMapper;
    
    public CacheInvalidationListener(RedisAdapter redisAdapter, ObjectMapper objectMapper) {
        this.redisAdapter = redisAdapter;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String messageBody = new String(message.getBody());
            logger.debug("Received cache invalidation message: {}", messageBody);
            
            CacheInvalidationMessage invalidationMessage = 
                objectMapper.readValue(messageBody, CacheInvalidationMessage.class);
            
            switch (invalidationMessage.getType()) {
                case SINGLE_PRODUCT:
                    invalidateSingleProduct(invalidationMessage);
                    break;
                case MULTIPLE_PRODUCTS:
                    invalidateMultipleProducts(invalidationMessage);
                    break;
                case ALL_PRODUCTS:
                    invalidateAllProducts();
                    break;
                default:
                    logger.warn("Unknown invalidation type: {}", invalidationMessage.getType());
            }
            
        } catch (Exception e) {
            logger.error("Failed to process cache invalidation message", e);
        }
    }
    
    private void invalidateSingleProduct(CacheInvalidationMessage message) {
        if (message.getProductId() != null) {
            ProductId productId = ProductId.of(message.getProductId());
            redisAdapter.evictProduct(productId);
            redisAdapter.evictStockQuantity(productId);
            logger.info("Invalidated cache for product: {}", productId);
        }
    }
    
    private void invalidateMultipleProducts(CacheInvalidationMessage message) {
        if (message.getProductIds() != null && !message.getProductIds().isEmpty()) {
            Set<ProductId> productIds = new HashSet<>();
            for (String id : message.getProductIds()) {
                productIds.add(ProductId.of(id));
            }
            redisAdapter.evictProducts(productIds);
            
            // Also evict stock quantities
            for (ProductId productId : productIds) {
                redisAdapter.evictStockQuantity(productId);
            }
            
            logger.info("Invalidated cache for {} products", productIds.size());
        }
    }
    
    private void invalidateAllProducts() {
        redisAdapter.clearAll();
        logger.info("Invalidated all cache entries");
    }
    
    /**
     * Cache invalidation message structure
     */
    public static class CacheInvalidationMessage {
        private InvalidationType type;
        private String productId;
        private Set<String> productIds;
        private String source;
        private Long timestamp;
        
        public InvalidationType getType() {
            return type;
        }
        
        public void setType(InvalidationType type) {
            this.type = type;
        }
        
        public String getProductId() {
            return productId;
        }
        
        public void setProductId(String productId) {
            this.productId = productId;
        }
        
        public Set<String> getProductIds() {
            return productIds;
        }
        
        public void setProductIds(Set<String> productIds) {
            this.productIds = productIds;
        }
        
        public String getSource() {
            return source;
        }
        
        public void setSource(String source) {
            this.source = source;
        }
        
        public Long getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(Long timestamp) {
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Types of cache invalidation
     */
    public enum InvalidationType {
        SINGLE_PRODUCT,
        MULTIPLE_PRODUCTS,
        ALL_PRODUCTS
    }
}