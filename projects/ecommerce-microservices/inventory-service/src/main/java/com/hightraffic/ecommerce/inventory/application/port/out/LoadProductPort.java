package com.hightraffic.ecommerce.inventory.application.port.out;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;

import java.util.Optional;

/**
 * 상품 조회 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 상품 조회 인터페이스
 */
public interface LoadProductPort {
    
    /**
     * 상품 ID로 상품 조회
     * 
     * @param productId 상품 ID
     * @return 상품 정보 (없으면 Optional.empty())
     */
    Optional<Product> loadProduct(ProductId productId);
    
    /**
     * 상품 존재 여부 확인
     * 
     * @param productId 상품 ID
     * @return 존재하면 true
     */
    boolean existsProduct(ProductId productId);
}