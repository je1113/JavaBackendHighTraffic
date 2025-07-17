package com.hightraffic.ecommerce.inventory.config;

import com.hightraffic.ecommerce.inventory.adapter.out.cache.CacheInvalidationListener;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

@Configuration
@EnableScheduling
public class RedisConfiguration {
    
    @Bean
    @ConfigurationProperties(prefix = "cache.redis")
    public CacheProperties cacheProperties() {
        return new CacheProperties();
    }
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serialization for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serialization for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationListener cacheInvalidationListener) {
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Subscribe to cache invalidation channel
        container.addMessageListener(cacheInvalidationListener, 
            new ChannelTopic("cache:invalidation"));
        
        return container;
    }
    
    @Bean
    public ChannelTopic cacheInvalidationTopic() {
        return new ChannelTopic("cache:invalidation");
    }
    
    public static class CacheProperties {
        private Duration defaultTtl = Duration.ofMinutes(30);
        private Duration productTtl = Duration.ofMinutes(10);
        private Duration stockTtl = Duration.ofMinutes(5);
        private Duration hotItemsTtl = Duration.ofHours(1);
        private String keyPrefix = "inventory:";
        private boolean enableStatistics = true;
        private Integer warmingThreads = 4;
        private Integer warmingBatchSize = 100;
        private Integer hotItemThreshold = 10;
        private Long refreshThreshold = 60L; // seconds before expiry to trigger refresh
        
        // Getters and setters
        public Duration getDefaultTtl() {
            return defaultTtl;
        }
        
        public void setDefaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
        }
        
        public Duration getProductTtl() {
            return productTtl;
        }
        
        public void setProductTtl(Duration productTtl) {
            this.productTtl = productTtl;
        }
        
        public Duration getStockTtl() {
            return stockTtl;
        }
        
        public void setStockTtl(Duration stockTtl) {
            this.stockTtl = stockTtl;
        }
        
        public Duration getHotItemsTtl() {
            return hotItemsTtl;
        }
        
        public void setHotItemsTtl(Duration hotItemsTtl) {
            this.hotItemsTtl = hotItemsTtl;
        }
        
        public String getKeyPrefix() {
            return keyPrefix;
        }
        
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
        
        public boolean isEnableStatistics() {
            return enableStatistics;
        }
        
        public void setEnableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
        }
        
        public Integer getWarmingThreads() {
            return warmingThreads;
        }
        
        public void setWarmingThreads(Integer warmingThreads) {
            this.warmingThreads = warmingThreads;
        }
        
        public Integer getWarmingBatchSize() {
            return warmingBatchSize;
        }
        
        public void setWarmingBatchSize(Integer warmingBatchSize) {
            this.warmingBatchSize = warmingBatchSize;
        }
        
        public Integer getHotItemThreshold() {
            return hotItemThreshold;
        }
        
        public void setHotItemThreshold(Integer hotItemThreshold) {
            this.hotItemThreshold = hotItemThreshold;
        }
        
        public Long getRefreshThreshold() {
            return refreshThreshold;
        }
        
        public void setRefreshThreshold(Long refreshThreshold) {
            this.refreshThreshold = refreshThreshold;
        }
    }
}