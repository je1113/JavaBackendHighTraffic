package com.hightraffic.ecommerce.inventory.application.port.out;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 캐시 관리 Outbound Port
 * 
 * 재고 정보의 캐싱을 담당하는 인터페이스
 * Redis 등의 캐시 어댑터에서 구현됩니다.
 */
public interface CachePort {
    
    /**
     * 상품 정보 캐싱
     * 
     * @param product 캐싱할 상품
     * @param ttl 캐시 유효 시간
     */
    void cacheProduct(Product product, Duration ttl);
    
    /**
     * 여러 상품 정보 일괄 캐싱
     * 
     * @param products 캐싱할 상품 목록
     * @param ttl 캐시 유효 시간
     */
    void cacheProducts(List<Product> products, Duration ttl);
    
    /**
     * 캐시에서 상품 정보 조회
     * 
     * @param productId 상품 ID
     * @return 캐시된 상품 정보 (없으면 Optional.empty())
     */
    Optional<Product> getCachedProduct(ProductId productId);
    
    /**
     * 캐시에서 여러 상품 정보 조회
     * 
     * @param productIds 상품 ID 목록
     * @return 캐시된 상품 목록 (캐시에 없는 상품은 제외)
     */
    List<Product> getCachedProducts(Set<ProductId> productIds);
    
    /**
     * 캐시에서 상품 정보 삭제
     * 
     * @param productId 삭제할 상품 ID
     */
    void evictProduct(ProductId productId);
    
    /**
     * 캐시에서 여러 상품 정보 삭제
     * 
     * @param productIds 삭제할 상품 ID 목록
     */
    void evictProducts(Set<ProductId> productIds);
    
    /**
     * 재고 수량 캐싱 (빠른 조회용)
     * 
     * @param productId 상품 ID
     * @param availableQuantity 가용 재고
     * @param reservedQuantity 예약 재고
     * @param ttl 캐시 유효 시간
     */
    void cacheStockQuantity(ProductId productId, StockQuantityCache stockQuantity, Duration ttl);
    
    /**
     * 캐시된 재고 수량 조회
     * 
     * @param productId 상품 ID
     * @return 캐시된 재고 수량 정보
     */
    Optional<StockQuantityCache> getCachedStockQuantity(ProductId productId);
    
    /**
     * 재고 수량 캐시 삭제
     * 
     * @param productId 상품 ID
     */
    void evictStockQuantity(ProductId productId);
    
    /**
     * 핫 아이템 목록 캐싱 (자주 조회되는 상품)
     * 
     * @param productIds 핫 아이템 ID 목록
     * @param ttl 캐시 유효 시간
     */
    void cacheHotItems(Set<ProductId> productIds, Duration ttl);
    
    /**
     * 핫 아이템 목록 조회
     * 
     * @return 핫 아이템 ID 목록
     */
    Set<ProductId> getHotItems();
    
    /**
     * 캐시 워밍 (미리 캐시에 로드)
     * 
     * @param products 워밍할 상품 목록
     * @return 워밍 완료 Future
     */
    CompletableFuture<Void> warmCache(List<Product> products);
    
    /**
     * 전체 캐시 초기화
     */
    void clearAll();
    
    /**
     * 캐시 상태 확인
     * 
     * @return 캐시 상태 정보
     */
    CacheStats getStats();
    
    /**
     * 재고 수량 캐시 정보
     */
    record StockQuantityCache(
        java.math.BigDecimal availableQuantity,
        java.math.BigDecimal reservedQuantity,
        java.time.LocalDateTime lastUpdated
    ) {}
    
    /**
     * 캐시 통계 정보
     */
    record CacheStats(
        long hitCount,
        long missCount,
        long evictionCount,
        double hitRate,
        long size
    ) {}
}