package com.hightraffic.ecommerce.inventory.adapter.out.lock;

import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.config.RedissonConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockAdapterUnitTest {
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private RLock lock;
    
    private RedisDistributedLockAdapter lockAdapter;
    private SimpleMeterRegistry meterRegistry;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        RedissonConfiguration.DistributedLockProperties lockProperties = 
            new RedissonConfiguration.DistributedLockProperties();
        lockProperties.setKeyPrefix("test:lock:");
        lockProperties.setDefaultWaitTime(Duration.ofSeconds(3));
        lockProperties.setDefaultLeaseTime(Duration.ofSeconds(10));
        
        lockAdapter = new RedisDistributedLockAdapter(redissonClient, lockProperties, meterRegistry);
    }
    
    @Test
    void shouldExecuteWithLockSuccessfully() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        String expectedResult = "task-completed";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
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
        verify(lock).tryLock(3L, 10L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }
    
    @Test
    void shouldThrowExceptionWhenLockAcquisitionFails() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> 
            lockAdapter.executeWithLock(
                lockKey,
                3L,
                10L,
                TimeUnit.SECONDS,
                () -> "should-not-execute"
            )
        ).isInstanceOf(DistributedLockPort.LockAcquisitionException.class)
         .hasMessageContaining("Failed to acquire lock for key: test-lock");
        
        verify(lock).tryLock(3L, 10L, TimeUnit.SECONDS);
        verify(lock, never()).unlock();
    }
    
    @Test
    void shouldHandleInterruptedException() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Test interrupt"));
        
        // When & Then
        assertThatThrownBy(() -> 
            lockAdapter.executeWithLock(
                lockKey,
                3L,
                10L,
                TimeUnit.SECONDS,
                () -> "should-not-execute"
            )
        ).isInstanceOf(DistributedLockPort.LockAcquisitionException.class)
         .hasMessageContaining("Lock acquisition interrupted");
        
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // Clear interrupt flag
    }
    
    @Test
    void shouldUnlockEvenWhenTaskThrowsException() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        RuntimeException taskException = new RuntimeException("Task failed");
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // When & Then
        assertThatThrownBy(() -> 
            lockAdapter.executeWithLock(
                lockKey,
                3L,
                10L,
                TimeUnit.SECONDS,
                () -> {
                    throw taskException;
                }
            )
        ).isSameAs(taskException);
        
        verify(lock).tryLock(3L, 10L, TimeUnit.SECONDS);
        verify(lock).unlock();
    }
    
    @Test
    void shouldTryLockSuccessfully() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        
        // When
        boolean result = lockAdapter.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS);
        
        // Then
        assertThat(result).isTrue();
        verify(lock).tryLock(3L, 10L, TimeUnit.SECONDS);
    }
    
    @Test
    void shouldReturnFalseWhenTryLockFails() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(false);
        
        // When
        boolean result = lockAdapter.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS);
        
        // Then
        assertThat(result).isFalse();
        verify(lock).tryLock(3L, 10L, TimeUnit.SECONDS);
    }
    
    @Test
    void shouldHandleInterruptedExceptionInTryLock() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenThrow(new InterruptedException("Test interrupt"));
        
        // When
        boolean result = lockAdapter.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS);
        
        // Then
        assertThat(result).isFalse();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted(); // Clear interrupt flag
    }
    
    @Test
    void shouldUnlockSuccessfully() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // First acquire the lock
        lockAdapter.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS);
        
        // When
        lockAdapter.unlock(lockKey);
        
        // Then
        verify(lock).unlock();
    }
    
    @Test
    void shouldHandleUnlockWhenLockNotHeldByCurrentThread() throws InterruptedException {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.tryLock(3L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);
        
        // First acquire the lock
        lockAdapter.tryLock(lockKey, 3L, 10L, TimeUnit.SECONDS);
        
        // When
        lockAdapter.unlock(lockKey);
        
        // Then
        verify(lock, never()).unlock();
    }
    
    @Test
    void shouldGetLockInfo() {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.isLocked()).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(lock.getHoldCount()).thenReturn(1L);
        when(lock.remainTimeToLive()).thenReturn(5000L);
        
        // When
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        
        // Then
        assertThat(lockInfo.getLockKey()).isEqualTo(lockKey);
        assertThat(lockInfo.isLocked()).isTrue();
        assertThat(lockInfo.isHeldByCurrentThread()).isTrue();
        assertThat(lockInfo.getHoldCount()).isEqualTo(1L);
        assertThat(lockInfo.getRemainTimeToLive()).isEqualTo(5000L);
    }
    
    @Test
    void shouldForceUnlock() {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.forceUnlock()).thenReturn(true);
        
        // When
        boolean result = lockAdapter.forceUnlock(lockKey);
        
        // Then
        assertThat(result).isTrue();
        verify(lock).forceUnlock();
    }
    
    @Test
    void shouldProvideStats() {
        // Given & When
        RedisDistributedLockAdapter.LockStats stats = lockAdapter.getStats();
        
        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getSuccessCount()).isEqualTo(0);
        assertThat(stats.getFailureCount()).isEqualTo(0);
        assertThat(stats.getActiveLockCount()).isEqualTo(0);
        assertThat(stats.getExecutionCount()).isEqualTo(0);
        assertThat(stats.getTotalExecutionTime()).isEqualTo(0);
    }
    
    @Test
    void shouldHandleExceptionInGetLockInfo() {
        // Given
        String lockKey = "test-lock";
        
        when(redissonClient.getLock("test:lock:test-lock")).thenReturn(lock);
        when(lock.isLocked()).thenThrow(new RuntimeException("Redis error"));
        
        // When
        RedisDistributedLockAdapter.LockInfo lockInfo = lockAdapter.getLockInfo(lockKey);
        
        // Then
        assertThat(lockInfo.getLockKey()).isEqualTo(lockKey);
        assertThat(lockInfo.isLocked()).isFalse();
        assertThat(lockInfo.isHeldByCurrentThread()).isFalse();
        assertThat(lockInfo.getHoldCount()).isEqualTo(0);
        assertThat(lockInfo.getRemainTimeToLive()).isEqualTo(-1);
    }
}