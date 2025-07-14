package com.hightraffic.ecommerce.inventory.application.port.out;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 분산 락 Outbound Port
 * 
 * Redis 등의 분산 락 구현체에서 제공해야 하는 인터페이스
 * 재고 동시성 제어를 위해 사용
 */
public interface DistributedLockPort {
    
    /**
     * 락을 획득하고 작업 수행
     * 
     * @param lockKey 락 키
     * @param waitTime 락 대기 시간
     * @param leaseTime 락 유지 시간
     * @param unit 시간 단위
     * @param task 수행할 작업
     * @return 작업 결과
     * @throws LockAcquisitionException 락 획득 실패
     */
    <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, 
                         TimeUnit unit, Supplier<T> task);
    
    /**
     * 락 획득 시도
     * 
     * @param lockKey 락 키
     * @param waitTime 락 대기 시간
     * @param leaseTime 락 유지 시간
     * @param unit 시간 단위
     * @return 락 획득 성공 여부
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit);
    
    /**
     * 락 해제
     * 
     * @param lockKey 락 키
     */
    void unlock(String lockKey);
    
    /**
     * 락 획득 실패 예외
     */
    class LockAcquisitionException extends RuntimeException {
        private final String lockKey;
        
        public LockAcquisitionException(String lockKey, String message) {
            super(message);
            this.lockKey = lockKey;
        }
        
        public String getLockKey() {
            return lockKey;
        }
    }
}