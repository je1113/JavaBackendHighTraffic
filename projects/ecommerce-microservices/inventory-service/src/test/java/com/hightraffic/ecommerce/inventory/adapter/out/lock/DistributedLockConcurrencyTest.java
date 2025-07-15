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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DistributedLockConcurrencyTest {
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("redisson.address", () -> "redis://" + redis.getHost() + ":" + redis.getFirstMappedPort());
    }
    
    private RedisDistributedLockAdapter lockAdapter;
    private RedissonClient redissonClient;
    
    @BeforeEach
    void setUp() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        // Configure Redisson
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + redis.getHost() + ":" + redis.getFirstMappedPort())
            .setConnectionPoolSize(50)
            .setConnectionMinimumIdleSize(10)
            .setTimeout(3000);
        
        redissonClient = Redisson.create(config);
        
        // Configure lock properties
        RedissonConfiguration.DistributedLockProperties lockProperties = 
            new RedissonConfiguration.DistributedLockProperties();
        lockProperties.setKeyPrefix("concurrency:lock:");
        lockProperties.setDefaultWaitTime(Duration.ofSeconds(5));
        lockProperties.setDefaultLeaseTime(Duration.ofSeconds(10));
        
        lockAdapter = new RedisDistributedLockAdapter(redissonClient, lockProperties, meterRegistry);
    }
    
    @Test
    void shouldEnsureMutualExclusionUnderHighConcurrency() throws InterruptedException {
        // Given
        String lockKey = "high-concurrency-test";
        int threadCount = 50;
        int operationsPerThread = 10;
        AtomicLong counter = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount * operationsPerThread);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try {
                        lockAdapter.executeWithLock(
                            lockKey,
                            2L,
                            1L,
                            TimeUnit.SECONDS,
                            () -> {
                                // Critical section - increment counter
                                long currentValue = counter.get();
                                // Simulate some processing time
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                counter.set(currentValue + 1);
                                successCount.incrementAndGet();
                                return currentValue + 1;
                            }
                        );
                    } catch (DistributedLockPort.LockAcquisitionException e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
        
        // Then
        latch.await(60, TimeUnit.SECONDS);
        
        // All operations should either succeed or fail, but the counter should be consistent
        int totalOperations = threadCount * operationsPerThread;
        assertThat(successCount.get() + failureCount.get()).isEqualTo(totalOperations);
        assertThat(counter.get()).isEqualTo(successCount.get());
        
        executor.shutdown();
    }
    
    @Test
    void shouldHandleDeadlockPrevention() throws InterruptedException {
        // Given
        String lockKey1 = "deadlock-test-1";
        String lockKey2 = "deadlock-test-2";
        int threadCount = 20;
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When - Create potential deadlock scenario
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    if (threadId % 2 == 0) {
                        // Even threads acquire lock1 first, then lock2
                        lockAdapter.executeWithLock(
                            lockKey1,
                            2L,
                            3L,
                            TimeUnit.SECONDS,
                            () -> {
                                return lockAdapter.executeWithLock(
                                    lockKey2,
                                    2L,
                                    3L,
                                    TimeUnit.SECONDS,
                                    () -> {
                                        completedTasks.incrementAndGet();
                                        return "completed";
                                    }
                                );
                            }
                        );
                    } else {
                        // Odd threads acquire lock2 first, then lock1
                        lockAdapter.executeWithLock(
                            lockKey2,
                            2L,
                            3L,
                            TimeUnit.SECONDS,
                            () -> {
                                return lockAdapter.executeWithLock(
                                    lockKey1,
                                    2L,
                                    3L,
                                    TimeUnit.SECONDS,
                                    () -> {
                                        completedTasks.incrementAndGet();
                                        return "completed";
                                    }
                                );
                            }
                        );
                    }
                } catch (DistributedLockPort.LockAcquisitionException e) {
                    // Some tasks may fail due to timeout, which is expected
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        
        // Some tasks should complete successfully without deadlock
        assertThat(completedTasks.get()).isGreaterThan(0);
        
        executor.shutdown();
    }
    
    @Test
    void shouldHandleLockTimeout() throws InterruptedException {
        // Given
        String lockKey = "timeout-test";
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        
        // When - One thread holds lock for long time, others should timeout
        executor.submit(() -> {
            try {
                lockAdapter.executeWithLock(
                    lockKey,
                    1L,
                    30L,  // Long lease time
                    TimeUnit.SECONDS,
                    () -> {
                        try {
                            Thread.sleep(5000); // Hold lock for 5 seconds
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        successCount.incrementAndGet();
                        return "long-running-task";
                    }
                );
            } catch (DistributedLockPort.LockAcquisitionException e) {
                timeoutCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });
        
        // Start other threads that should timeout
        for (int i = 0; i < 9; i++) {
            executor.submit(() -> {
                try {
                    lockAdapter.executeWithLock(
                        lockKey,
                        1L,  // Short wait time
                        5L,
                        TimeUnit.SECONDS,
                        () -> {
                            successCount.incrementAndGet();
                            return "quick-task";
                        }
                    );
                } catch (DistributedLockPort.LockAcquisitionException e) {
                    timeoutCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        
        assertThat(successCount.get()).isEqualTo(1); // Only the long-running task should succeed
        assertThat(timeoutCount.get()).isEqualTo(9); // All other tasks should timeout
        
        executor.shutdown();
    }
    
    @Test
    void shouldProvideConsistentLockStats() throws InterruptedException {
        // Given
        String lockKey = "stats-test";
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    lockAdapter.executeWithLock(
                        lockKey,
                        3L,
                        1L,
                        TimeUnit.SECONDS,
                        () -> {
                            // Simulate work
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return "work-done";
                        }
                    );
                } catch (DistributedLockPort.LockAcquisitionException e) {
                    // Expected for some threads
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        
        RedisDistributedLockAdapter.LockStats stats = lockAdapter.getStats();
        assertThat(stats.getSuccessCount()).isGreaterThan(0);
        assertThat(stats.getExecutionCount()).isGreaterThan(0);
        assertThat(stats.getTotalExecutionTime()).isGreaterThan(0);
        assertThat(stats.getSuccessRate()).isGreaterThan(0);
        
        executor.shutdown();
    }
    
    @Test
    void shouldHandleMultipleLockKeysSimultaneously() throws InterruptedException {
        // Given
        int lockKeyCount = 5;
        int threadsPerLock = 10;
        AtomicInteger totalSuccessCount = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(lockKeyCount * threadsPerLock);
        CountDownLatch latch = new CountDownLatch(lockKeyCount * threadsPerLock);
        
        // When
        for (int lockIndex = 0; lockIndex < lockKeyCount; lockIndex++) {
            final String lockKey = "multi-lock-" + lockIndex;
            
            for (int threadIndex = 0; threadIndex < threadsPerLock; threadIndex++) {
                executor.submit(() -> {
                    try {
                        lockAdapter.executeWithLock(
                            lockKey,
                            2L,
                            1L,
                            TimeUnit.SECONDS,
                            () -> {
                                totalSuccessCount.incrementAndGet();
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return "done";
                            }
                        );
                    } catch (DistributedLockPort.LockAcquisitionException e) {
                        // Expected for some threads
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        // Then
        latch.await(30, TimeUnit.SECONDS);
        
        // Each lock should allow at least one thread to succeed
        assertThat(totalSuccessCount.get()).isGreaterThanOrEqualTo(lockKeyCount);
        
        executor.shutdown();
    }
}