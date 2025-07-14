package com.hightraffic.ecommerce.inventory.application.port.out;

import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

import java.util.List;

/**
 * 조건별 상품 조회 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 상품 일괄 조회 인터페이스
 */
public interface LoadProductsByConditionPort {
    
    /**
     * 여러 상품 ID로 상품 목록 조회
     * 
     * @param productIds 상품 ID 목록
     * @return 상품 목록
     */
    List<Product> loadProductsByIds(List<ProductId> productIds);
    
    /**
     * 재고 부족 상품 조회
     * 
     * @param threshold 임계값
     * @param includeInactive 비활성 상품 포함 여부
     * @param limit 조회 개수 제한
     * @return 재고 부족 상품 목록
     */
    List<Product> loadLowStockProducts(StockQuantity threshold, boolean includeInactive, int limit);
    
    /**
     * 예약 ID로 상품 조회
     * 
     * @param reservationId 예약 ID
     * @return 해당 예약을 가진 상품 (없으면 null)
     */
    Product loadProductByReservationId(String reservationId);
}