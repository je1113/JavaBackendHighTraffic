package com.hightraffic.ecommerce.inventory.application.port.out;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 재고 관리 통합 영속성 Outbound Port
 * 
 * 재고 관리와 관련된 모든 영속성 작업을 담당하는 통합 인터페이스
 * 이 인터페이스는 LoadProductPort, SaveProductPort, LoadProductsByConditionPort의
 * 기능을 통합하여 제공합니다.
 */
public interface InventoryPersistencePort extends LoadProductPort, SaveProductPort, LoadProductsByConditionPort {
    
    /**
     * 트랜잭션 내에서 상품 조회 (비관적 락)
     * 
     * @param productId 상품 ID
     * @return 락이 걸린 상품 정보
     */
    Optional<Product> loadProductForUpdate(ProductId productId);
    
    /**
     * 여러 상품을 트랜잭션 내에서 조회 (비관적 락)
     * 
     * @param productIds 상품 ID 목록
     * @return 락이 걸린 상품 목록
     */
    List<Product> loadProductsForUpdate(Set<ProductId> productIds);
    
    /**
     * 특정 시간 이전에 생성된 예약 조회
     * 
     * @param before 기준 시간
     * @return 만료된 예약을 가진 상품 목록
     */
    List<Product> loadProductsWithExpiredReservations(LocalDateTime before);
    
    /**
     * 재고 수량 범위로 상품 조회
     * 
     * @param minQuantity 최소 재고 수량
     * @param maxQuantity 최대 재고 수량
     * @return 조건에 맞는 상품 목록
     */
    List<Product> loadProductsByStockRange(BigDecimal minQuantity, BigDecimal maxQuantity);
    
    /**
     * 활성 예약이 있는 상품 조회
     * 
     * @return 활성 예약이 있는 상품 목록
     */
    List<Product> loadProductsWithActiveReservations();
    
    /**
     * 배치 상품 저장
     * 
     * @param products 저장할 상품 목록
     * @return 저장된 상품 목록
     */
    List<Product> saveProducts(List<Product> products);
    
    /**
     * 상품 삭제
     * 
     * @param productId 삭제할 상품 ID
     */
    void deleteProduct(ProductId productId);
    
    /**
     * 모든 상품의 재고 현황 조회
     * 
     * @return 재고 현황 (상품 ID, 가용 재고, 예약 재고)
     */
    List<StockSummary> loadStockSummary();
    
    /**
     * 재고 현황 요약 정보
     */
    record StockSummary(
        ProductId productId,
        BigDecimal availableQuantity,
        BigDecimal reservedQuantity,
        int activeReservationCount
    ) {}
}