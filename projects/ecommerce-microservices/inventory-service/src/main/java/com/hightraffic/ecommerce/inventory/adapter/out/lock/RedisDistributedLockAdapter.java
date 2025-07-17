package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.config.RedissonConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
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
    private final Counter lockTimeoutCounter;
    private final Counter deadlockDetectedCounter;
    private final Timer lockExecutionTimer;
    private final Timer lockWaitTimer;
    private final AtomicLong activeLockCount = new AtomicLong(0);
    private final AtomicLong fairLockQueueSize = new AtomicLong(0);
    
    // Thread-local storage for lock tracking
    private final ThreadLocal<ConcurrentMap<String, LockContext>> threadLocalLocks = 
        ThreadLocal.withInitial(ConcurrentHashMap::new);
        
    // Lock monitoring
    private final ConcurrentMap<String, LockMetadata> lockRegistry = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitoringExecutor = Executors.newScheduledThreadPool(2);
    
    // Deadlock detection
    private final Map<String, Set<String>> lockDependencyGraph = new ConcurrentHashMap<>();
    private final Set<String> potentialDeadlocks = ConcurrentHashMap.newKeySet();
    
    public RedisDistributedLockAdapter(RedissonClient redissonClient,
                                     RedissonConfiguration.DistributedLockProperties lockProperties,
                                     MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.lockProperties = lockProperties;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.lockAcquisitionSuccessCounter = Counter.builder("lock.acquisition.success")
            .description("Number of successful lock acquisitions")
            .tag("type", "distributed")
            .register(meterRegistry);
        
        this.lockAcquisitionFailureCounter = Counter.builder("lock.acquisition.failure")
            .description("Number of failed lock acquisitions")
            .tag("type", "distributed")
            .register(meterRegistry);
            
        this.lockTimeoutCounter = Counter.builder("lock.timeout")
            .description("Number of lock timeouts")
            .tag("type", "distributed")
            .register(meterRegistry);
            
        this.deadlockDetectedCounter = Counter.builder("lock.deadlock.detected")
            .description("Number of deadlocks detected")
            .tag("type", "distributed")
            .register(meterRegistry);
        
        this.lockExecutionTimer = Timer.builder("lock.execution.time")
            .description("Time spent executing with lock")
            .tag("type", "distributed")
            .register(meterRegistry);
            
        this.lockWaitTimer = Timer.builder("lock.wait.time")
            .description("Time spent waiting for lock")
            .tag("type", "distributed")
            .register(meterRegistry);
        
        // Register gauges
        meterRegistry.gauge("lock.active.count", activeLockCount);
        meterRegistry.gauge("lock.fair.queue.size", fairLockQueueSize);
        meterRegistry.gauge("lock.registry.size", lockRegistry, Map::size);
        
        // Start monitoring tasks
        startMonitoringTasks();
    }
    
    @Override
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, 
                                TimeUnit unit, Supplier<T> task) {
        String fullLockKey = buildLockKey(lockKey);
        RLock lock = createLock(lockKey, fullLockKey);
        
        Timer.Sample executionSample = Timer.start(meterRegistry);
        Timer.Sample waitSample = Timer.start(meterRegistry);
        
        // Register lock attempt for deadlock detection
        String threadId = Thread.currentThread().getName();
        registerLockAttempt(lockKey, threadId);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            waitSample.stop(lockWaitTimer);
            
            if (!acquired) {
                lockAcquisitionFailureCounter.increment();
                unregisterLockAttempt(lockKey, threadId);
                
                // Check for potential deadlock
                if (isDeadlockSuspected(lockKey, threadId)) {
                    deadlockDetectedCounter.increment();
                    logger.warn("Potential deadlock detected for lock: {} and thread: {}", lockKey, threadId);
                }
                
                throw new LockAcquisitionException(lockKey, 
                    String.format("Failed to acquire lock for key: %s within %d %s", 
                        lockKey, waitTime, unit.name()));
            }
            
            lockAcquisitionSuccessCounter.increment();
            activeLockCount.incrementAndGet();
            
            // Create lock context with metadata
            LockContext context = new LockContext(lock, lockKey, leaseTime, unit, LocalDateTime.now());
            threadLocalLocks.get().put(lockKey, context);
            
            // Register lock acquisition
            registerLockAcquisition(lockKey, threadId, context);
            
            logger.debug("Lock acquired for key: {} with lease time: {} {} by thread: {}", 
                lockKey, leaseTime, unit.name(), threadId);
            
            try {
                // Enable watchdog for automatic lease renewal if configured
                if (lockProperties.isEnableWatchdog()) {
                    startWatchdog(lockKey, lock, leaseTime, unit);
                }
                
                return task.get();
                
            } finally {
                // Clean up
                stopWatchdog(lockKey);
                unregisterLockAcquisition(lockKey, threadId);
                threadLocalLocks.get().remove(lockKey);
                
                // Unlock if still held by current thread
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                    activeLockCount.decrementAndGet();
                    logger.debug("Lock released for key: {} by thread: {}", lockKey, threadId);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockAcquisitionFailureCounter.increment();
            unregisterLockAttempt(lockKey, threadId);
            throw new LockAcquisitionException(lockKey, 
                "Lock acquisition interrupted for key: " + lockKey);
        } catch (Exception e) {
            if (!(e instanceof LockAcquisitionException)) {
                lockAcquisitionFailureCounter.increment();
            }
            
            if (e instanceof LockAcquisitionException) {
                throw e;
            }
            
            throw new LockAcquisitionException(lockKey, 
                "Unexpected error during lock acquisition for key: " + lockKey + ", error: " + e.getMessage());
        } finally {
            executionSample.stop(lockExecutionTimer);
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
                LockContext context = new LockContext(lock, lockKey, leaseTime, unit, LocalDateTime.now());
                threadLocalLocks.get().put(lockKey, context);
                
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
        ConcurrentMap<String, LockContext> lockMap = threadLocalLocks.get();
        LockContext context = lockMap.remove(lockKey);
        
        if (context == null) {
            logger.warn("Attempted to unlock key: {} but no lock found in thread-local storage", lockKey);
            return;
        }
        
        RLock lock = context.getLock();
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
        ConcurrentMap<String, LockContext> lockMap = threadLocalLocks.get();
        
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
    
    // New methods for advanced features
    
    /**
     * Create appropriate lock type based on configuration
     */
    private RLock createLock(String lockKey, String fullLockKey) {
        if (lockProperties.isUseFairLock()) {
            RLock fairLock = redissonClient.getFairLock(fullLockKey);
            // Track fair lock queue size
            try {
                // Fair lock queue size tracking can be done through metrics
                logger.debug("Using fair lock for key: {}", fullLockKey);
            } catch (Exception e) {
                logger.debug("Failed to get fair lock info", e);
            }
            return fairLock;
        }
        return redissonClient.getLock(fullLockKey);
    }
    
    /**
     * Execute with read lock (for shared access)
     */
    public <T> T executeWithReadLock(String lockKey, long waitTime, long leaseTime, 
                                    TimeUnit unit, Supplier<T> task) {
        String fullLockKey = buildLockKey(lockKey);
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(fullLockKey);
        RLock readLock = rwLock.readLock();
        
        return executeLockOperation(lockKey, readLock, waitTime, leaseTime, unit, task, "READ");
    }
    
    /**
     * Execute with write lock (for exclusive access)
     */
    public <T> T executeWithWriteLock(String lockKey, long waitTime, long leaseTime, 
                                     TimeUnit unit, Supplier<T> task) {
        String fullLockKey = buildLockKey(lockKey);
        RReadWriteLock rwLock = redissonClient.getReadWriteLock(fullLockKey);
        RLock writeLock = rwLock.writeLock();
        
        return executeLockOperation(lockKey, writeLock, waitTime, leaseTime, unit, task, "WRITE");
    }
    
    /**
     * Common lock execution logic
     */
    private <T> T executeLockOperation(String lockKey, RLock lock, long waitTime, 
                                      long leaseTime, TimeUnit unit, Supplier<T> task, String lockType) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String threadId = Thread.currentThread().getName();
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, unit);
            
            if (!acquired) {
                lockAcquisitionFailureCounter.increment();
                throw new LockAcquisitionException(lockKey, 
                    String.format("Failed to acquire %s lock for key: %s", lockType, lockKey));
            }
            
            lockAcquisitionSuccessCounter.increment();
            logger.debug("{} lock acquired for key: {} by thread: {}", lockType, lockKey, threadId);
            
            return task.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException(lockKey, "Lock acquisition interrupted");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            sample.stop(lockExecutionTimer);
        }
    }
    
    // Watchdog implementation for automatic lease renewal
    
    private final Map<String, ScheduledFuture<?>> watchdogTasks = new ConcurrentHashMap<>();
    
    private void startWatchdog(String lockKey, RLock lock, long leaseTime, TimeUnit unit) {
        long renewalInterval = unit.toMillis(leaseTime) / 3; // Renew at 1/3 of lease time
        
        ScheduledFuture<?> watchdogTask = monitoringExecutor.scheduleAtFixedRate(() -> {
            try {
                if (lock.isHeldByCurrentThread()) {
                    // Redisson's RLock doesn't have expire method, use expireAsync instead
                    lock.lock(leaseTime, unit);
                    logger.debug("Renewed lease for lock: {}", lockKey);
                } else {
                    stopWatchdog(lockKey);
                }
            } catch (Exception e) {
                logger.error("Error renewing lock lease for key: {}", lockKey, e);
                stopWatchdog(lockKey);
            }
        }, renewalInterval, renewalInterval, TimeUnit.MILLISECONDS);
        
        watchdogTasks.put(lockKey, watchdogTask);
    }
    
    private void stopWatchdog(String lockKey) {
        ScheduledFuture<?> task = watchdogTasks.remove(lockKey);
        if (task != null) {
            task.cancel(false);
        }
    }
    
    // Deadlock detection
    
    private void registerLockAttempt(String lockKey, String threadId) {
        lockDependencyGraph.computeIfAbsent(threadId, k -> ConcurrentHashMap.newKeySet()).add(lockKey);
    }
    
    private void unregisterLockAttempt(String lockKey, String threadId) {
        Set<String> dependencies = lockDependencyGraph.get(threadId);
        if (dependencies != null) {
            dependencies.remove(lockKey);
            if (dependencies.isEmpty()) {
                lockDependencyGraph.remove(threadId);
            }
        }
    }
    
    private void registerLockAcquisition(String lockKey, String threadId, LockContext context) {
        LockMetadata metadata = new LockMetadata(lockKey, threadId, context.getAcquiredAt(), 
            context.getLeaseTime(), context.getTimeUnit());
        lockRegistry.put(lockKey, metadata);
    }
    
    private void unregisterLockAcquisition(String lockKey, String threadId) {
        lockRegistry.remove(lockKey);
        unregisterLockAttempt(lockKey, threadId);
    }
    
    private boolean isDeadlockSuspected(String lockKey, String threadId) {
        // Simple cycle detection in dependency graph
        Set<String> visited = new HashSet<>();
        return hasCycle(threadId, visited, new HashSet<>());
    }
    
    private boolean hasCycle(String node, Set<String> visited, Set<String> recursionStack) {
        visited.add(node);
        recursionStack.add(node);
        
        Set<String> dependencies = lockDependencyGraph.get(node);
        if (dependencies != null) {
            for (String dep : dependencies) {
                if (!visited.contains(dep)) {
                    if (hasCycle(dep, visited, recursionStack)) {
                        return true;
                    }
                } else if (recursionStack.contains(dep)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    // Monitoring tasks
    
    private void startMonitoringTasks() {
        // Lock timeout monitoring
        monitoringExecutor.scheduleAtFixedRate(this::checkLockTimeouts, 
            30, 30, TimeUnit.SECONDS);
            
        // Deadlock detection
        monitoringExecutor.scheduleAtFixedRate(this::detectDeadlocks, 
            60, 60, TimeUnit.SECONDS);
            
        // Metrics reporting
        monitoringExecutor.scheduleAtFixedRate(this::reportMetrics, 
            300, 300, TimeUnit.SECONDS);
    }
    
    private void checkLockTimeouts() {
        try {
            LocalDateTime now = LocalDateTime.now();
            lockRegistry.forEach((key, metadata) -> {
                long holdTime = java.time.Duration.between(metadata.getAcquiredAt(), now).toMillis();
                long maxHoldTime = metadata.getTimeUnit().toMillis(metadata.getLeaseTime());
                
                if (holdTime > maxHoldTime * 1.5) {
                    logger.warn("Lock {} held by thread {} for {}ms (expected max: {}ms)", 
                        key, metadata.getThreadId(), holdTime, maxHoldTime);
                    lockTimeoutCounter.increment();
                }
            });
        } catch (Exception e) {
            logger.error("Error checking lock timeouts", e);
        }
    }
    
    private void detectDeadlocks() {
        try {
            Set<String> threads = new HashSet<>(lockDependencyGraph.keySet());
            for (String thread : threads) {
                if (isDeadlockSuspected("", thread)) {
                    if (potentialDeadlocks.add(thread)) {
                        deadlockDetectedCounter.increment();
                        logger.error("Deadlock detected involving thread: {}", thread);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error detecting deadlocks", e);
        }
    }
    
    private void reportMetrics() {
        try {
            LockStats stats = getStats();
            logger.info("Lock statistics - Success: {}, Failure: {}, Success Rate: {:.2f}%, " +
                       "Active: {}, Avg Execution Time: {:.2f}ms", 
                stats.getSuccessCount(), stats.getFailureCount(), 
                stats.getSuccessRate() * 100, stats.getActiveLockCount(),
                stats.getAverageExecutionTime());
        } catch (Exception e) {
            logger.error("Error reporting metrics", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            // Cancel all watchdog tasks
            watchdogTasks.values().forEach(task -> task.cancel(false));
            watchdogTasks.clear();
            
            // Shutdown monitoring executor
            monitoringExecutor.shutdown();
            if (!monitoringExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitoringExecutor.shutdownNow();
            }
            
            // Clean up any remaining locks
            cleanupThreadLocalLocks();
            
            logger.info("RedisDistributedLockAdapter shutdown completed");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    // Inner classes
    
    private static class LockContext {
        private final RLock lock;
        private final String lockKey;
        private final long leaseTime;
        private final TimeUnit timeUnit;
        private final LocalDateTime acquiredAt;
        
        public LockContext(RLock lock, String lockKey, long leaseTime, 
                          TimeUnit timeUnit, LocalDateTime acquiredAt) {
            this.lock = lock;
            this.lockKey = lockKey;
            this.leaseTime = leaseTime;
            this.timeUnit = timeUnit;
            this.acquiredAt = acquiredAt;
        }
        
        public RLock getLock() {
            return lock;
        }
        
        public String getLockKey() {
            return lockKey;
        }
        
        public long getLeaseTime() {
            return leaseTime;
        }
        
        public TimeUnit getTimeUnit() {
            return timeUnit;
        }
        
        public LocalDateTime getAcquiredAt() {
            return acquiredAt;
        }
    }
    
    private static class LockMetadata {
        private final String lockKey;
        private final String threadId;
        private final LocalDateTime acquiredAt;
        private final long leaseTime;
        private final TimeUnit timeUnit;
        
        public LockMetadata(String lockKey, String threadId, LocalDateTime acquiredAt,
                           long leaseTime, TimeUnit timeUnit) {
            this.lockKey = lockKey;
            this.threadId = threadId;
            this.acquiredAt = acquiredAt;
            this.leaseTime = leaseTime;
            this.timeUnit = timeUnit;
        }
        
        public String getLockKey() {
            return lockKey;
        }
        
        public String getThreadId() {
            return threadId;
        }
        
        public LocalDateTime getAcquiredAt() {
            return acquiredAt;
        }
        
        public long getLeaseTime() {
            return leaseTime;
        }
        
        public TimeUnit getTimeUnit() {
            return timeUnit;
        }
    }
}