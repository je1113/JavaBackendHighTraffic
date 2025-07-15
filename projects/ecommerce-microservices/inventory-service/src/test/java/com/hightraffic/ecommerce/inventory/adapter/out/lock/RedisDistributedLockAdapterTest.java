package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.config.RedissonConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class RedisDistributedLockAdapterTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("redisson.address", () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    }
    
    private RedisDistributedLockAdapter lockAdapter;
    private RedissonClient redissonClient;
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        // Configure Redisson
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort())
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(5)
            .setTimeout(3000);
        
        redissonClient = Redisson.create(config);
        
        // Configure lock properties
        RedissonConfiguration.DistributedLockProperties lockProperties = 
            new RedissonConfiguration.DistributedLockProperties();
        lockProperties.setKeyPrefix("test:lock:");
        lockProperties.setDefaultWaitTime(Duration.ofSeconds(3));
        lockProperties.setDefaultLeaseTime(Duration.ofSeconds(10));
        
        lockAdapter = new RedisDistributedLockAdapter(redissonClient, lockProperties, meterRegistry);
    }
    
    @Test
    void shouldExecuteWithLockSuccessfully() {
        // Given
        String lockKey = "test-lock-1";
        String expectedResult = "task-completed";
        
        // When
        String result = lockAdapter.executeWithLock(
            lockKey,
            3L,
            10L,
            TimeUnit.SECONDS,
            () -> expectedResult
        );
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        
        // Verify lock is released
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
    }
    
    @Test
    void shouldThrowExceptionWhenLockAcquisitionFails() {
        // Given
        String lockKey = "test-lock-2";
        
        // First acquire the lock
        boolean firstLockAcquired = lockAdapter.tryLock(lockKey, 1, 10, TimeUnit.SECONDS);
        assertThat(firstLockAcquired).isTrue();
        
        try {
            // When - try to acquire the same lock with short wait time
            assertThatThrownBy(() -> 
                lockAdapter.executeWithLock(
                    lockKey,
                    1L,  // Short wait time
                    5L,
                    TimeUnit.SECONDS,
                    () -> "should-not-execute"
                )
            ).isInstanceOf(DistributedLockPort.LockAcquisitionException.class)
             .hasMessageContaining("Failed to acquire lock for key: " + lockKey);
        } finally {
            // Clean up
            lockAdapter.unlock(lockKey);
        }
    }
    
    @Test
    void shouldHandleConcurrentLockRequests() throws InterruptedException {
        // Given
        String lockKey = "concurrent-test";
        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    lockAdapter.executeWithLock(
                        lockKey,
                        2L,
                        1L,
                        TimeUnit.SECONDS,
                        () -> {
                            // Simulate some work
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            successCount.incrementAndGet();
                            return "completed-" + threadId;
                        }
                    );
                } catch (DistributedLockPort.LockAcquisitionException e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        
        // Only one thread should succeed at a time
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failureCount.get()).isEqualTo(0);
        
        executor.shutdown();
    }
    
    @Test
    void shouldTryLockAndUnlockManually() {
        // Given
        String lockKey = "manual-lock-test";
        
        // When
        boolean acquired = lockAdapter.tryLock(lockKey, 3, 10, TimeUnit.SECONDS);
        
        // Then
        assertThat(acquired).isTrue();
        
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isTrue();
        assertThat(lockInfo.isHeldByCurrentThread()).isTrue();
        
        // Unlock
        lockAdapter.unlock(lockKey);
        
        lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
    }
    
    @Test
    void shouldReturnFalseWhenTryLockFails() {
        // Given
        String lockKey = "try-lock-fail-test";
        
        // First acquire the lock
        boolean firstLockAcquired = lockAdapter.tryLock(lockKey, 1, 10, TimeUnit.SECONDS);
        assertThat(firstLockAcquired).isTrue();
        
        try {
            // When - try to acquire the same lock
            boolean secondLockAcquired = lockAdapter.tryLock(lockKey, 1, 5, TimeUnit.SECONDS);
            
            // Then
            assertThat(secondLockAcquired).isFalse();
        } finally {
            // Clean up
            lockAdapter.unlock(lockKey);
        }
    }
    
    @Test
    void shouldProvideAccurateLockStats() {
        // Given
        String lockKey = "stats-test";
        
        // When
        lockAdapter.executeWithLock(lockKey, 3, 10, TimeUnit.SECONDS, () -> "test");
        
        // Try to acquire lock that will fail
        try {
            lockAdapter.executeWithLock(lockKey, 0, 1, TimeUnit.MILLISECONDS, () -> "should-fail");
        } catch (DistributedLockPort.LockAcquisitionException e) {
            // Expected
        }
        
        // Then
        RedisDistributedLockAdapter.LockStats stats = lockAdapter.getStats();
        assertThat(stats.getSuccessCount()).isGreaterThan(0);
        assertThat(stats.getFailureCount()).isGreaterThan(0);
        assertThat(stats.getExecutionCount()).isGreaterThan(0);
        assertThat(stats.getSuccessRate()).isGreaterThan(0);
    }
    
    @Test
    void shouldHandleExceptionInTask() {
        // Given
        String lockKey = "exception-test";
        RuntimeException expectedException = new RuntimeException("Task failed");
        
        // When & Then
        assertThatThrownBy(() -> 
            lockAdapter.executeWithLock(
                lockKey,
                3L,
                10L,
                TimeUnit.SECONDS,
                () -> {
                    throw expectedException;
                }
            )
        ).isSameAs(expectedException);
        
        // Verify lock is released even when task throws exception
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
    }
    
    @Test
    void shouldHandleLockTimeout() {
        // Given
        String lockKey = "timeout-test";
        
        // Acquire lock with short lease time
        boolean acquired = lockAdapter.tryLock(lockKey, 1, 1, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();
        
        try {
            // Wait for lease to expire
            Thread.sleep(2000);
            
            // Lock should have expired
            RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
            assertThat(lockInfo.isLocked()).isFalse();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Test
    void shouldForceUnlockWhenNecessary() {
        // Given
        String lockKey = "force-unlock-test";
        
        boolean acquired = lockAdapter.tryLock(lockKey, 1, 30, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();
        
        // When
        boolean forceUnlocked = lockAdapter.forceUnlock(lockKey);
        
        // Then
        assertThat(forceUnlocked).isTrue();
        
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
    }
    
    @Test
    void shouldHandleInterruptedThread() throws InterruptedException {
        // Given
        String lockKey = "interrupt-test";
        AtomicReference<Exception> caughtException = new AtomicReference<>();
        
        Thread lockThread = new Thread(() -> {
            try {
                lockAdapter.executeWithLock(
                    lockKey,
                    30L,  // Long wait time
                    10L,
                    TimeUnit.SECONDS,
                    () -> "should-not-complete"
                );
            } catch (Exception e) {
                caughtException.set(e);
            }
        });
        
        // When
        lockThread.start();
        Thread.sleep(100); // Let thread start
        lockThread.interrupt();
        lockThread.join();
        
        // Then
        assertThat(caughtException.get()).isInstanceOf(DistributedLockPort.LockAcquisitionException.class);
        assertThat(caughtException.get().getMessage()).contains("interrupted");
    }
    
    @Test
    void shouldCleanupThreadLocalLocks() {
        // Given
        String lockKey = "cleanup-test";
        
        boolean acquired = lockAdapter.tryLock(lockKey, 1, 10, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();
        
        // When
        lockAdapter.cleanupThreadLocalLocks();
        
        // Then
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
    }
}