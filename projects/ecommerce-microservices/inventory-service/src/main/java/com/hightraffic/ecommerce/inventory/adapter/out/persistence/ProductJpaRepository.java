package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

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
 * 상품 JPA 리포지토리
 * 
 * 재고 관리를 위한 데이터 접근 계층입니다.
 * 동시성 제어와 성능 최적화를 위한 다양한 쿼리 메서드를 제공합니다.
 */
@Repository
public interface ProductJpaRepository extends JpaRepository<ProductJpaEntity, String> {
    
    /**
     * SKU로 상품 조회
     */
    Optional<ProductJpaEntity> findBySku(String sku);
    
    /**
     * ID로 상품 조회 (비관적 쓰기 잠금)
     * 재고 업데이트 시 동시성 제어를 위해 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id = :id")
    Optional<ProductJpaEntity> findByIdWithLock(@Param("id") String id);
    
    /**
     * SKU로 상품 조회 (비관적 쓰기 잠금)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.sku = :sku")
    Optional<ProductJpaEntity> findBySkuWithLock(@Param("sku") String sku);
    
    /**
     * 여러 상품 ID로 조회 (비관적 쓰기 잠금)
     * 주문 처리 시 여러 상품의 재고를 동시에 처리할 때 사용
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.id IN :ids ORDER BY p.id")
    List<ProductJpaEntity> findByIdsWithLock(@Param("ids") List<String> ids);
    
    /**
     * 카테고리별 활성 상품 조회
     */
    @Query("SELECT p FROM ProductJpaEntity p WHERE p.category = :category " +
           "AND p.status = 'ACTIVE' ORDER BY p.name")
    Page<ProductJpaEntity> findActiveByCategory(
        @Param("category") String category, 
        Pageable pageable
    );
    
    /**
     * 낮은 재고 상품 조회
     * 재고 보충이 필요한 상품을 식별
     */
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.availableQuantity <= p.minimumStockLevel " +
           "AND p.status = 'ACTIVE' " +
           "ORDER BY p.availableQuantity ASC")
    List<ProductJpaEntity> findLowStockProducts(Pageable pageable);
    
    /**
     * 품절 상품 조회
     */
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.availableQuantity = 0 " +
           "AND p.status IN ('ACTIVE', 'OUT_OF_STOCK') " +
           "ORDER BY p.updatedAt DESC")
    List<ProductJpaEntity> findOutOfStockProducts(Pageable pageable);
    
    /**
     * 과재고 상품 조회
     */
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.maximumStockLevel IS NOT NULL " +
           "AND p.totalQuantity > p.maximumStockLevel " +
           "AND p.status = 'ACTIVE' " +
           "ORDER BY (p.totalQuantity - p.maximumStockLevel) DESC")
    List<ProductJpaEntity> findOverStockProducts(Pageable pageable);
    
    /**
     * 재고 업데이트 (낙관적 잠금 사용)
     * 동시성이 낮은 경우 성능 최적화
     */
    @Modifying
    @Query("UPDATE ProductJpaEntity p " +
           "SET p.availableQuantity = :availableQuantity, " +
           "p.reservedQuantity = :reservedQuantity, " +
           "p.version = p.version + 1 " +
           "WHERE p.id = :productId AND p.version = :currentVersion")
    int updateStockOptimistic(
        @Param("productId") String productId,
        @Param("availableQuantity") Integer availableQuantity,
        @Param("reservedQuantity") Integer reservedQuantity,
        @Param("currentVersion") Long currentVersion
    );
    
    /**
     * 재고 일괄 업데이트
     */
    @Modifying
    @Query("UPDATE ProductJpaEntity p " +
           "SET p.totalQuantity = p.totalQuantity + :quantity, " +
           "p.availableQuantity = p.availableQuantity + :quantity, " +
           "p.lastRestockAt = :restockTime " +
           "WHERE p.id IN :productIds")
    int bulkUpdateStock(
        @Param("productIds") List<String> productIds,
        @Param("quantity") Integer quantity,
        @Param("restockTime") Instant restockTime
    );
    
    /**
     * 상품 상태 일괄 변경
     */
    @Modifying
    @Query("UPDATE ProductJpaEntity p " +
           "SET p.status = :newStatus, p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id IN :productIds")
    int updateProductStatusBatch(
        @Param("productIds") List<String> productIds,
        @Param("newStatus") ProductJpaEntity.ProductStatus newStatus
    );
    
    /**
     * 카테고리별 재고 통계
     */
    @Query("SELECT new map(" +
           "p.category as category, " +
           "COUNT(p) as productCount, " +
           "SUM(p.totalQuantity) as totalStock, " +
           "SUM(p.availableQuantity) as availableStock, " +
           "SUM(p.reservedQuantity) as reservedStock, " +
           "SUM(p.price * p.totalQuantity) as totalValue) " +
           "FROM ProductJpaEntity p " +
           "WHERE p.status = 'ACTIVE' " +
           "GROUP BY p.category")
    List<Object[]> getCategoryStockStatistics();
    
    /**
     * 재고 회전율 계산을 위한 데이터
     */
    @Query(value = "SELECT p.id, p.sku, p.name, p.total_quantity, " +
                   "COALESCE(SUM(sm.quantity), 0) as total_sold " +
                   "FROM products p " +
                   "LEFT JOIN stock_movements sm ON p.id = sm.product_id " +
                   "AND sm.movement_type = 'STOCK_OUT' " +
                   "AND sm.created_at >= :startDate " +
                   "WHERE p.status = 'ACTIVE' " +
                   "GROUP BY p.id, p.sku, p.name, p.total_quantity " +
                   "ORDER BY total_sold DESC",
           nativeQuery = true)
    List<Object[]> getInventoryTurnoverData(@Param("startDate") Instant startDate);
    
    /**
     * 예약된 재고가 있는 상품 조회
     */
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.reservedQuantity > 0 " +
           "ORDER BY p.reservedQuantity DESC")
    List<ProductJpaEntity> findProductsWithReservations();
    
    /**
     * 특정 기간 동안 업데이트되지 않은 상품 조회 (데드 스톡)
     */
    @Query("SELECT p FROM ProductJpaEntity p " +
           "WHERE p.updatedAt < :cutoffDate " +
           "AND p.status = 'ACTIVE' " +
           "AND p.totalQuantity > 0 " +
           "ORDER BY p.updatedAt ASC")
    List<ProductJpaEntity> findDeadStock(
        @Param("cutoffDate") Instant cutoffDate,
        Pageable pageable
    );
    
    /**
     * 상품 존재 여부 확인 (성능 최적화)
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END " +
           "FROM ProductJpaEntity p WHERE p.sku = :sku")
    boolean existsBySku(@Param("sku") String sku);
    
    /**
     * 활성 상품 수 조회
     */
    @Query("SELECT COUNT(p) FROM ProductJpaEntity p WHERE p.status = 'ACTIVE'")
    long countActiveProducts();
    
    /**
     * 전체 재고 가치 계산
     */
    @Query("SELECT SUM(p.totalQuantity * p.price) FROM ProductJpaEntity p " +
           "WHERE p.status = 'ACTIVE'")
    Double calculateTotalInventoryValue();
}