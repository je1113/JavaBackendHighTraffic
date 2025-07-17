package com.hightraffic.ecommerce.inventory.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedissonConfiguration {
    
    @Bean
    @ConfigurationProperties(prefix = "redisson")
    public RedissonProperties redissonProperties() {
        return new RedissonProperties();
    }
    
    @Bean
    public RedissonClient redissonClient(RedissonProperties redissonProperties) {
        Config config = new Config();
        
        // Single server configuration
        config.useSingleServer()
            .setAddress(redissonProperties.getAddress())
            .setPassword(redissonProperties.getPassword())
            .setConnectionPoolSize(redissonProperties.getConnectionPoolSize())
            .setConnectionMinimumIdleSize(redissonProperties.getConnectionMinimumIdleSize())
            .setTimeout(redissonProperties.getTimeout())
            .setRetryAttempts(redissonProperties.getRetryAttempts())
            .setRetryInterval(redissonProperties.getRetryInterval())
            .setDatabase(redissonProperties.getDatabase())
            .setKeepAlive(true)
            .setTcpNoDelay(true);
        
        // Codec configuration for serialization
        config.setCodec(new org.redisson.codec.JsonJacksonCodec());
        
        // Threads configuration
        config.setThreads(redissonProperties.getThreads());
        config.setNettyThreads(redissonProperties.getNettyThreads());
        
        // Lock watchdog timeout (default lease time for locks without explicit lease time)
        config.setLockWatchdogTimeout(redissonProperties.getLockWatchdogTimeout().toMillis());
        
        return Redisson.create(config);
    }
    
    @Bean
    @ConfigurationProperties(prefix = "distributed.lock")
    public DistributedLockProperties distributedLockProperties() {
        return new DistributedLockProperties();
    }
    
    public static class RedissonProperties {
        private String address = "redis://localhost:6379";
        private String password;
        private int connectionPoolSize = 10;
        private int connectionMinimumIdleSize = 5;
        private int timeout = 3000;
        private int retryAttempts = 3;
        private int retryInterval = 1500;
        private int database = 0;
        private int threads = 16;
        private int nettyThreads = 32;
        private Duration lockWatchdogTimeout = Duration.ofSeconds(30);
        
        // Getters and setters
        public String getAddress() {
            return address;
        }
        
        public void setAddress(String address) {
            this.address = address;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public int getConnectionPoolSize() {
            return connectionPoolSize;
        }
        
        public void setConnectionPoolSize(int connectionPoolSize) {
            this.connectionPoolSize = connectionPoolSize;
        }
        
        public int getConnectionMinimumIdleSize() {
            return connectionMinimumIdleSize;
        }
        
        public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
            this.connectionMinimumIdleSize = connectionMinimumIdleSize;
        }
        
        public int getTimeout() {
            return timeout;
        }
        
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        
        public int getRetryAttempts() {
            return retryAttempts;
        }
        
        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }
        
        public int getRetryInterval() {
            return retryInterval;
        }
        
        public void setRetryInterval(int retryInterval) {
            this.retryInterval = retryInterval;
        }
        
        public int getDatabase() {
            return database;
        }
        
        public void setDatabase(int database) {
            this.database = database;
        }
        
        public int getThreads() {
            return threads;
        }
        
        public void setThreads(int threads) {
            this.threads = threads;
        }
        
        public int getNettyThreads() {
            return nettyThreads;
        }
        
        public void setNettyThreads(int nettyThreads) {
            this.nettyThreads = nettyThreads;
        }
        
        public Duration getLockWatchdogTimeout() {
            return lockWatchdogTimeout;
        }
        
        public void setLockWatchdogTimeout(Duration lockWatchdogTimeout) {
            this.lockWatchdogTimeout = lockWatchdogTimeout;
        }
    }
    
    public static class DistributedLockProperties {
        private String keyPrefix = "lock:inventory:";
        private Duration defaultWaitTime = Duration.ofSeconds(3);
        private Duration defaultLeaseTime = Duration.ofSeconds(10);
        private boolean enableMetrics = true;
        private Duration metricsReportInterval = Duration.ofMinutes(1);
        private boolean useFairLock = false;
        private boolean enableWatchdog = true;
        private boolean enableDeadlockDetection = true;
        private Duration deadlockDetectionInterval = Duration.ofMinutes(1);
        private Duration lockTimeoutCheckInterval = Duration.ofSeconds(30);
        
        public String getKeyPrefix() {
            return keyPrefix;
        }
        
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
        
        public Duration getDefaultWaitTime() {
            return defaultWaitTime;
        }
        
        public void setDefaultWaitTime(Duration defaultWaitTime) {
            this.defaultWaitTime = defaultWaitTime;
        }
        
        public Duration getDefaultLeaseTime() {
            return defaultLeaseTime;
        }
        
        public void setDefaultLeaseTime(Duration defaultLeaseTime) {
            this.defaultLeaseTime = defaultLeaseTime;
        }
        
        public boolean isEnableMetrics() {
            return enableMetrics;
        }
        
        public void setEnableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
        }
        
        public Duration getMetricsReportInterval() {
            return metricsReportInterval;
        }
        
        public void setMetricsReportInterval(Duration metricsReportInterval) {
            this.metricsReportInterval = metricsReportInterval;
        }
        
        public boolean isUseFairLock() {
            return useFairLock;
        }
        
        public void setUseFairLock(boolean useFairLock) {
            this.useFairLock = useFairLock;
        }
        
        public boolean isEnableWatchdog() {
            return enableWatchdog;
        }
        
        public void setEnableWatchdog(boolean enableWatchdog) {
            this.enableWatchdog = enableWatchdog;
        }
        
        public boolean isEnableDeadlockDetection() {
            return enableDeadlockDetection;
        }
        
        public void setEnableDeadlockDetection(boolean enableDeadlockDetection) {
            this.enableDeadlockDetection = enableDeadlockDetection;
        }
        
        public Duration getDeadlockDetectionInterval() {
            return deadlockDetectionInterval;
        }
        
        public void setDeadlockDetectionInterval(Duration deadlockDetectionInterval) {
            this.deadlockDetectionInterval = deadlockDetectionInterval;
        }
        
        public Duration getLockTimeoutCheckInterval() {
            return lockTimeoutCheckInterval;
        }
        
        public void setLockTimeoutCheckInterval(Duration lockTimeoutCheckInterval) {
            this.lockTimeoutCheckInterval = lockTimeoutCheckInterval;
        }
    }
}