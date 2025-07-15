package com.hightraffic.ecommerce.order.adapter.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 주문 JPA 리포지토리
 * 
 * Spring Data JPA를 사용하여 주문 영속성을 관리합니다.
 * 복잡한 쿼리와 성능 최적화를 위한 커스텀 메서드를 포함합니다.
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, String> {
    
    /**
     * ID로 주문 조회 (비관적 잠금)
     * 동시성 제어를 위해 비관적 쓰기 잠금을 사용합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderJpaEntity o WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithLock(@Param("id") String id);
    
    /**
     * ID로 주문과 아이템 함께 조회 (Fetch Join)
     * N+1 문제를 방지하기 위해 Fetch Join을 사용합니다.
     */
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.id = :id")
    Optional<OrderJpaEntity> findByIdWithItems(@Param("id") String id);
    
    /**
     * 고객 ID로 주문 목록 조회 (페이징)
     */
    Page<OrderJpaEntity> findByCustomerId(String customerId, Pageable pageable);
    
    /**
     * 고객 ID로 주문과 아이템 함께 조회 (Fetch Join + 페이징)
     * 주의: Fetch Join과 페이징을 함께 사용할 때는 메모리에서 페이징이 수행됩니다.
     */
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o " +
           "LEFT JOIN FETCH o.items " +
           "WHERE o.customerId = :customerId")
    List<OrderJpaEntity> findByCustomerIdWithItems(@Param("customerId") String customerId);
    
    /**
     * 고객 ID와 상태로 주문 조회
     */
    List<OrderJpaEntity> findByCustomerIdAndStatus(
        String customerId, 
        OrderJpaEntity.OrderStatusEntity status
    );
    
    /**
     * 특정 기간 내 고객의 주문 수 조회
     */
    @Query("SELECT COUNT(o) FROM OrderJpaEntity o " +
           "WHERE o.customerId = :customerId " +
           "AND o.createdAt >= :startDate " +
           "AND o.createdAt < :endDate")
    long countByCustomerIdAndCreatedAtBetween(
        @Param("customerId") String customerId,
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * 특정 상태의 주문 목록 조회 (생성일 기준 정렬)
     */
    List<OrderJpaEntity> findByStatusOrderByCreatedAtDesc(
        OrderJpaEntity.OrderStatusEntity status
    );
    
    /**
     * 특정 기간 동안 생성된 주문 목록 조회
     */
    @Query("SELECT o FROM OrderJpaEntity o " +
           "WHERE o.createdAt >= :startDate " +
           "AND o.createdAt < :endDate " +
           "ORDER BY o.createdAt DESC")
    List<OrderJpaEntity> findOrdersCreatedBetween(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * 결제 ID로 주문 조회
     */
    Optional<OrderJpaEntity> findByPaymentId(String paymentId);
    
    /**
     * 특정 상품을 포함한 주문 목록 조회
     */
    @Query("SELECT DISTINCT o FROM OrderJpaEntity o " +
           "JOIN o.items i " +
           "WHERE i.productId = :productId " +
           "AND o.status NOT IN ('CANCELLED')")
    List<OrderJpaEntity> findActiveOrdersContainingProduct(@Param("productId") String productId);
    
    /**
     * 고객의 최근 주문 조회
     */
    @Query("SELECT o FROM OrderJpaEntity o " +
           "WHERE o.customerId = :customerId " +
           "ORDER BY o.createdAt DESC")
    List<OrderJpaEntity> findRecentOrdersByCustomerId(
        @Param("customerId") String customerId,
        Pageable pageable
    );
    
    /**
     * 주문 상태 일괄 업데이트
     */
    @Modifying
    @Query("UPDATE OrderJpaEntity o " +
           "SET o.status = :newStatus, o.updatedAt = :updatedAt " +
           "WHERE o.id IN :orderIds")
    int updateOrderStatusBatch(
        @Param("orderIds") List<String> orderIds,
        @Param("newStatus") OrderJpaEntity.OrderStatusEntity newStatus,
        @Param("updatedAt") Instant updatedAt
    );
    
    /**
     * 만료된 대기 상태 주문 조회
     */
    @Query("SELECT o FROM OrderJpaEntity o " +
           "WHERE o.status = 'PENDING' " +
           "AND o.createdAt < :expirationTime")
    List<OrderJpaEntity> findExpiredPendingOrders(@Param("expirationTime") Instant expirationTime);
    
    /**
     * 고객별 주문 통계 조회
     */
    @Query("SELECT new map(" +
           "o.customerId as customerId, " +
           "COUNT(o) as orderCount, " +
           "SUM(o.totalAmount) as totalAmount) " +
           "FROM OrderJpaEntity o " +
           "WHERE o.status = 'COMPLETED' " +
           "AND o.createdAt >= :startDate " +
           "GROUP BY o.customerId")
    List<Object[]> getCustomerOrderStatistics(@Param("startDate") Instant startDate);
    
    /**
     * 특정 기간의 일별 주문 통계
     */
    @Query(value = "SELECT DATE(created_at) as orderDate, " +
                   "COUNT(*) as orderCount, " +
                   "SUM(total_amount) as totalRevenue " +
                   "FROM orders " +
                   "WHERE created_at >= :startDate " +
                   "AND created_at < :endDate " +
                   "AND status = 'COMPLETED' " +
                   "GROUP BY DATE(created_at) " +
                   "ORDER BY orderDate", 
           nativeQuery = true)
    List<Object[]> getDailyOrderStatistics(
        @Param("startDate") Instant startDate,
        @Param("endDate") Instant endDate
    );
    
    /**
     * 주문 존재 여부 확인 (성능 최적화)
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END " +
           "FROM OrderJpaEntity o WHERE o.id = :id")
    boolean existsByIdOptimized(@Param("id") String id);
}