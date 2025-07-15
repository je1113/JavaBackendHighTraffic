package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 재고 예약 JPA 리포지토리
 * 
 * 재고 예약 관리를 위한 데이터 접근 계층입니다.
 */
@Repository
public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, String> {
    
    /**
     * 주문 ID로 예약 목록 조회
     */
    List<StockReservationJpaEntity> findByOrderId(String orderId);
    
    /**
     * 주문 ID와 상품 ID로 예약 조회
     */
    @Query("SELECT r FROM StockReservationJpaEntity r " +
           "WHERE r.orderId = :orderId AND r.product.id = :productId")
    Optional<StockReservationJpaEntity> findByOrderIdAndProductId(
        @Param("orderId") String orderId,
        @Param("productId") String productId
    );
    
    /**
     * 활성 예약 조회
     */
    @Query("SELECT r FROM StockReservationJpaEntity r " +
           "WHERE r.orderId = :orderId AND r.status = 'ACTIVE'")
    List<StockReservationJpaEntity> findActiveByOrderId(@Param("orderId") String orderId);
    
    /**
     * 상품별 활성 예약 수량 합계
     */
    @Query("SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservationJpaEntity r " +
           "WHERE r.product.id = :productId AND r.status = 'ACTIVE'")
    Integer sumActiveReservationsByProductId(@Param("productId") String productId);
    
    /**
     * 만료된 예약 조회
     */
    @Query("SELECT r FROM StockReservationJpaEntity r " +
           "WHERE r.status = 'ACTIVE' AND r.expiresAt < :currentTime")
    List<StockReservationJpaEntity> findExpiredReservations(@Param("currentTime") Instant currentTime);
    
    /**
     * 만료된 예약 일괄 취소
     */
    @Modifying
    @Query("UPDATE StockReservationJpaEntity r " +
           "SET r.status = 'EXPIRED', r.cancelledAt = :cancelTime, " +
           "r.cancellationReason = 'Reservation expired' " +
           "WHERE r.status = 'ACTIVE' AND r.expiresAt < :currentTime")
    int expireReservations(
        @Param("currentTime") Instant currentTime,
        @Param("cancelTime") Instant cancelTime
    );
    
    /**
     * 주문별 예약 일괄 확정
     */
    @Modifying
    @Query("UPDATE StockReservationJpaEntity r " +
           "SET r.status = 'CONFIRMED', r.confirmedAt = :confirmTime " +
           "WHERE r.orderId = :orderId AND r.status = 'ACTIVE'")
    int confirmOrderReservations(
        @Param("orderId") String orderId,
        @Param("confirmTime") Instant confirmTime
    );
    
    /**
     * 주문별 예약 일괄 취소
     */
    @Modifying
    @Query("UPDATE StockReservationJpaEntity r " +
           "SET r.status = 'CANCELLED', r.cancelledAt = :cancelTime, " +
           "r.cancellationReason = :reason " +
           "WHERE r.orderId = :orderId AND r.status = 'ACTIVE'")
    int cancelOrderReservations(
        @Param("orderId") String orderId,
        @Param("cancelTime") Instant cancelTime,
        @Param("reason") String reason
    );
    
    /**
     * 특정 기간의 예약 통계
     */
    @Query("SELECT new map(" +
           "DATE(r.createdAt) as date, " +
           "COUNT(r) as totalReservations, " +
           "SUM(CASE WHEN r.status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmedCount, " +
           "SUM(CASE WHEN r.status = 'CANCELLED' THEN 1 ELSE 0 END) as cancelledCount, " +
           "SUM(CASE WHEN r.status = 'EXPIRED' THEN 1 ELSE 0 END) as expiredCount) " +
           "FROM StockReservationJpaEntity r " +
           "WHERE r.createdAt >= :startDate AND r.createdAt < :endDate " +
           "GROUP BY DATE(r.createdAt)")
    List<Object[]> getReservationStatistics(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * 예약 완료율 계산
     */
    @Query("SELECT " +
           "COUNT(CASE WHEN r.status = 'CONFIRMED' THEN 1 END) * 100.0 / COUNT(*) " +
           "FROM StockReservationJpaEntity r " +
           "WHERE r.createdAt >= :startDate")
    Double calculateReservationCompletionRate(@Param("startDate") Instant startDate);
}