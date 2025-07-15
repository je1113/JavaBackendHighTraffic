package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.config.RedissonConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Component
public class RedisDistributedLockAdapter implements DistributedLockPort {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisDistributedLockAdapter.class);
    
    private final RedissonClient redissonClient;
    private final RedissonConfiguration.DistributedLockProperties lockProperties;
    private final MeterRegistry meterRegistry;
    
    // Metrics
    private final Counter lockAcquisitionSuccessCounter;
    private final Counter lockAcquisitionFailureCounter;
    private final Timer lockExecutionTimer;
    private final AtomicLong activeLockCount = new AtomicLong(0);
    
    // Thread-local storage for lock tracking
    private final ThreadLocal<ConcurrentMap<String, RLock>> threadLocalLocks = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    public RedisDistributedLockAdapter(RedissonClient redissonClient,
                                     RedissonConfiguration.DistributedLockProperties lockProperties,
                                     MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.lockProperties = lockProperties;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.lockAcquisitionSuccessCounter = Counter.builder("lock.acquisition.success")
            .description("Number of successful lock acquisitions")
            .register(meterRegistry);
        
        this.lockAcquisitionFailureCounter = Counter.builder("lock.acquisition.failure")
            .description("Number of failed lock acquisitions")
            .register(meterRegistry);
        
        this.lockExecutionTimer = Timer.builder("lock.execution.time")
            .description("Time spent executing with lock")
            .register(meterRegistry);
        
        // Register active lock count gauge
        meterRegistry.gauge("lock.active.count", activeLockCount);
    }
    
    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, 
                                TimeUnit unit, Supplier<T> task) {
        String fullLockKey = buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                lockAcquisitionFailureCounter.increment();
                throw new LockAcquisitionException(lockKey, 
                    String.format("Failed to acquire lock for key: %s within %d %s", 
                        lockKey, waitTime, unit.name()));
            }
            
            lockAcquisitionSuccessCounter.increment();
            activeLockCount.incrementAndGet();
            
            // Store lock in thread-local storage for potential manual unlock
            threadLocalLocks.get().put(lockKey, lock);
            
            logger.debug("Lock acquired for key: {} with lease time: {} {}", 
                lockKey, leaseTime, unit.name());
            
            try {
                return task.get();
            } finally {
                // Always remove from thread-local storage after execution
                threadLocalLocks.get().remove(lockKey);
                
                // Unlock if still held by current thread
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    activeLockCount.decrementAndGet();
                    logger.debug("Lock released for key: {}", lockKey);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockAcquisitionFailureCounter.increment();
            throw new LockAcquisitionException(lockKey, 
                "Lock acquisition interrupted for key: " + lockKey);
        } catch (Exception e) {
            lockAcquisitionFailureCounter.increment();
            
            if (e instanceof LockAcquisitionException) {
                throw e;
            }
            
            throw new LockAcquisitionException(lockKey, 
                "Unexpected error during lock acquisition for key: " + lockKey + ", error: " + e.getMessage());
        } finally {
            sample.stop(lockExecutionTimer);
        }
    }
    
    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        String fullLockKey = buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            
            if (acquired) {
                lockAcquisitionSuccessCounter.increment();
                activeLockCount.incrementAndGet();
                
                // Store lock in thread-local storage
                threadLocalLocks.get().put(lockKey, lock);
                
                logger.debug("Lock acquired for key: {} with lease time: {} {}", 
                    lockKey, leaseTime, unit.name());
            } else {
                lockAcquisitionFailureCounter.increment();
                logger.debug("Failed to acquire lock for key: {} within {} {}", 
                    lockKey, waitTime, unit.name());
            }
            
            return acquired;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockAcquisitionFailureCounter.increment();
            logger.error("Lock acquisition interrupted for key: {}", lockKey, e);
            return false;
        } catch (Exception e) {
            lockAcquisitionFailureCounter.increment();
            logger.error("Unexpected error during lock acquisition for key: {}", lockKey, e);
            return false;
        }
    }
    
    @Override
    public void unlock(String lockKey) {
        ConcurrentMap<String, RLock> lockMap = threadLocalLocks.get();
        RLock lock = lockMap.remove(lockKey);
        
        if (lock == null) {
            logger.warn("Attempted to unlock key: {} but no lock found in thread-local storage", lockKey);
            return;
        }
        
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                activeLockCount.decrementAndGet();
                logger.debug("Lock released for key: {}", lockKey);
            } else {
                logger.warn("Attempted to unlock key: {} but lock is not held by current thread", lockKey);
            }
        } catch (Exception e) {
            logger.error("Error releasing lock for key: {}", lockKey, e);
        }
    }
    
    /**
     * Get lock information for monitoring purposes
     */
    public LockInfo getLockInfo(String lockKey) {
        String fullLockKey = buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);
        
        try {
            return new LockInfo(
                lockKey,
                lock.isLocked(),
                lock.isHeldByCurrentThread(),
                lock.getHoldCount(),
                lock.remainTimeToLive()
            );
        } catch (Exception e) {
            logger.error("Error getting lock info for key: {}", lockKey, e);
            return new LockInfo(lockKey, false, false, 0, -1);
        }
    }
    
    /**
     * Force unlock a lock (use with caution)
     */
    public boolean forceUnlock(String lockKey) {
        String fullLockKey = buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);
        
        try {
            boolean unlocked = lock.forceUnlock();
            if (unlocked) {
                activeLockCount.decrementAndGet();
                threadLocalLocks.get().remove(lockKey);
                logger.warn("Force unlocked key: {}", lockKey);
            }
            return unlocked;
        } catch (Exception e) {
            logger.error("Error force unlocking key: {}", lockKey, e);
            return false;
        }
    }
    
    /**
     * Get current lock statistics
     */
    public LockStats getStats() {
        return new LockStats(
            lockAcquisitionSuccessCounter.count(),
            lockAcquisitionFailureCounter.count(),
            activeLockCount.get(),
            lockExecutionTimer.count(),
            lockExecutionTimer.totalTime(TimeUnit.MILLISECONDS)
        );
    }
    
    /**
     * Clean up thread-local locks (call this when thread is done)
     */
    public void cleanupThreadLocalLocks() {
        ConcurrentMap<String, RLock> lockMap = threadLocalLocks.get();
        
        for (String lockKey : lockMap.keySet()) {
            try {
                unlock(lockKey);
            } catch (Exception e) {
                logger.error("Error cleaning up lock for key: {}", lockKey, e);
            }
        }
        
        threadLocalLocks.remove();
    }
    
    private String buildLockKey(String lockKey) {
        return lockProperties.getKeyPrefix() + lockKey;
    }
    
    /**
     * Lock information for monitoring
     */
    public static class LockInfo {
        private final String lockKey;
        private final boolean locked;
        private final boolean heldByCurrentThread;
        private final long holdCount;
        private final long remainTimeToLive;
        
        public LockInfo(String lockKey, boolean locked, boolean heldByCurrentThread, 
                       long holdCount, long remainTimeToLive) {
            this.lockKey = lockKey;
            this.locked = locked;
            this.heldByCurrentThread = heldByCurrentThread;
            this.holdCount = holdCount;
            this.remainTimeToLive = remainTimeToLive;
        }
        
        public String getLockKey() {
            return lockKey;
        }
        
        public boolean isLocked() {
            return locked;
        }
        
        public boolean isHeldByCurrentThread() {
            return heldByCurrentThread;
        }
        
        public long getHoldCount() {
            return holdCount;
        }
        
        public long getRemainTimeToLive() {
            return remainTimeToLive;
        }
    }
    
    /**
     * Lock statistics for monitoring
     */
    public static class LockStats {
        private final double successCount;
        private final double failureCount;
        private final long activeLockCount;
        private final double executionCount;
        private final double totalExecutionTime;
        
        public LockStats(double successCount, double failureCount, long activeLockCount, 
                        double executionCount, double totalExecutionTime) {
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.activeLockCount = activeLockCount;
            this.executionCount = executionCount;
            this.totalExecutionTime = totalExecutionTime;
        }
        
        public double getSuccessCount() {
            return successCount;
        }
        
        public double getFailureCount() {
            return failureCount;
        }
        
        public long getActiveLockCount() {
            return activeLockCount;
        }
        
        public double getExecutionCount() {
            return executionCount;
        }
        
        public double getTotalExecutionTime() {
            return totalExecutionTime;
        }
        
        public double getSuccessRate() {
            double total = successCount + failureCount;
            return total > 0 ? successCount / total : 0.0;
        }
        
        public double getAverageExecutionTime() {
            return executionCount > 0 ? totalExecutionTime / executionCount : 0.0;
        }
    }
}