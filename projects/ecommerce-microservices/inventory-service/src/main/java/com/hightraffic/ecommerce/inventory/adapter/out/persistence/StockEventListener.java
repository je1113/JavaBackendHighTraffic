package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA 엔티티 리스너 - 재고 관련 이벤트 처리
 * 
 * 재고 변경 시 자동으로 감사 로그를 생성하고 
 * 비즈니스 규칙을 적용합니다.
 */
public class StockEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(StockEventListener.class);
    
    /**
     * 엔티티 생성 후 처리
     */
    @PostPersist
    public void onPostPersist(ProductJpaEntity product) {
        log.info("새 상품 생성됨: productId={}, sku={}, initialStock={}", 
            product.getId(), product.getSku(), product.getTotalQuantity());
    }
    
    /**
     * 엔티티 업데이트 전 처리
     */
    @PreUpdate
    public void onPreUpdate(ProductJpaEntity product) {
        // 재고 상태 자동 업데이트
        if (product.getAvailableQuantity() <= 0 && 
            product.getStatus() == ProductJpaEntity.ProductStatus.ACTIVE) {
            product.setStatus(ProductJpaEntity.ProductStatus.OUT_OF_STOCK);
            log.warn("상품 품절 처리: productId={}, sku={}", 
                product.getId(), product.getSku());
        }
        
        // 낮은 재고 경고
        if (product.isLowStock()) {
            log.warn("낮은 재고 경고: productId={}, sku={}, available={}, minimum={}", 
                product.getId(), product.getSku(), 
                product.getAvailableQuantity(), product.getMinimumStockLevel());
        }
    }
    
    /**
     * 엔티티 업데이트 후 처리
     */
    @PostUpdate
    public void onPostUpdate(ProductJpaEntity product) {
        log.debug("상품 정보 업데이트됨: productId={}, version={}", 
            product.getId(), product.getVersion());
    }
}