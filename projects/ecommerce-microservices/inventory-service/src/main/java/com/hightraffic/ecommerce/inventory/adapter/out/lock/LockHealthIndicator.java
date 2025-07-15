package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import org.redisson.api.RedissonClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LockHealthIndicator implements HealthIndicator {
    
    private final RedissonClient redissonClient;
    private final RedisDistributedLockAdapter lockAdapter;
    
    public LockHealthIndicator(RedissonClient redissonClient, RedisDistributedLockAdapter lockAdapter) {
        this.redissonClient = redissonClient;
        this.lockAdapter = lockAdapter;
    }
    
    @Override
    public Health health() {
        try {
            // Test basic connectivity
            if (!redissonClient.getNodesGroup().pingAll()) {
                return Health.down()
                    .withDetail("error", "Redis nodes ping failed")
                    .withDetail("redisson", "DOWN")
                    .build();
            }
            
            // Test lock functionality
            String testLockKey = "health-check-lock";
            boolean lockTestPassed = testLockFunctionality(testLockKey);
            
            if (!lockTestPassed) {
                return Health.down()
                    .withDetail("error", "Lock functionality test failed")
                    .withDetail("redisson", "UP")
                    .withDetail("lock", "DOWN")
                    .build();
            }
            
            // Get lock statistics
            RedisDistributedLockAdapter.LockStats stats = lockAdapter.getStats();
            
            Map<String, Object> details = new HashMap<>();
            details.put("redisson", "UP");
            details.put("lock", "UP");
            details.put("lockSuccessCount", stats.getSuccessCount());
            details.put("lockFailureCount", stats.getFailureCount());
            details.put("activeLockCount", stats.getActiveLockCount());
            details.put("lockSuccessRate", String.format("%.2f%%", stats.getSuccessRate() * 100));
            details.put("averageExecutionTime", String.format("%.2f ms", stats.getAverageExecutionTime()));
            
            return Health.up()
                .withDetails(details)
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .withDetail("redisson", "DOWN")
                .withDetail("lock", "DOWN")
                .build();
        }
    }
    
    private boolean testLockFunctionality(String testLockKey) {
        try {
            // Test lock acquisition and release
            boolean acquired = lockAdapter.tryLock(testLockKey, 1, 5, TimeUnit.SECONDS);
            
            if (!acquired) {
                return false;
            }
            
            // Test lock info
            RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(testLockKey);
            if (!lockInfo.isLocked() || !lockInfo.isHeldByCurrentThread()) {
                lockAdapter.unlock(testLockKey);
                return false;
            }
            
            // Release lock
            lockAdapter.unlock(testLockKey);
            
            // Verify lock is released
            lockInfo = lockAdapter.getLockInfo(testLockKey);
            return !lockInfo.isLocked();
            
        } catch (Exception e) {
            return false;
        }
    }
}