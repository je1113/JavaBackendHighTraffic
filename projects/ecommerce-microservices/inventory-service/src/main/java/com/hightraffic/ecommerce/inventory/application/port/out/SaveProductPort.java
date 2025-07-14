package com.hightraffic.ecommerce.inventory.application.port.out;

import com.hightraffic.ecommerce.inventory.domain.model.Product;

/**
 * 상품 저장 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 상품 저장 인터페이스
 */
public interface SaveProductPort {
    
    /**
     * 상품 저장 또는 수정
     * 
     * @param product 저장할 상품
     * @return 저장된 상품
     */
    Product saveProduct(Product product);
}