package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LockMetrics implements InfoContributor {
    
    private final RedisDistributedLockAdapter lockAdapter;
    
    public LockMetrics(RedisDistributedLockAdapter lockAdapter) {
        this.lockAdapter = lockAdapter;
    }
    
    @Override
    public void contribute(Info.Builder builder) {
        RedisDistributedLockAdapter.LockStats stats = lockAdapter.getStats();
        
        Map<String, Object> lockInfo = new HashMap<>();
        lockInfo.put("successCount", stats.getSuccessCount());
        lockInfo.put("failureCount", stats.getFailureCount());
        lockInfo.put("activeLockCount", stats.getActiveLockCount());
        lockInfo.put("executionCount", stats.getExecutionCount());
        lockInfo.put("totalExecutionTime", String.format("%.2f ms", stats.getTotalExecutionTime()));
        lockInfo.put("successRate", String.format("%.2f%%", stats.getSuccessRate() * 100));
        lockInfo.put("averageExecutionTime", String.format("%.2f ms", stats.getAverageExecutionTime()));
        
        builder.withDetail("distributedLock", lockInfo);
    }
}