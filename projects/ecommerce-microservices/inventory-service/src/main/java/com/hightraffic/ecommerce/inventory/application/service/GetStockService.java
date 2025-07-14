package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.inventory.application.port.in.GetStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductsByConditionPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.service.StockDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 재고 조회 Use Case 구현체
 * 
 * 책임:
 * - 단일/다수 상품 재고 조회
 * - 예약 정보 조회
 * - 재고 부족 상품 분석
 * 
 * 읽기 전용 트랜잭션으로 성능 최적화
 */
@Service
@Transactional(readOnly = true)
public class GetStockService implements GetStockUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(GetStockService.class);
    
    private final LoadProductPort loadProductPort;
    private final LoadProductsByConditionPort loadProductsByConditionPort;
    private final StockDomainService stockDomainService;
    
    public GetStockService(LoadProductPort loadProductPort,
                         LoadProductsByConditionPort loadProductsByConditionPort,
                         StockDomainService stockDomainService) {
        this.loadProductPort = loadProductPort;
        this.loadProductsByConditionPort = loadProductsByConditionPort;
        this.stockDomainService = stockDomainService;
    }
    
    @Override
    public StockResponse getStock(GetStockQuery query) {
        log.debug("Getting stock for product: {}", query.getProductId());
        
        Product product = loadProductPort.loadProduct(query.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(query.getProductId()));
        
        return new StockResponse(product);
    }
    
    @Override
    public List<StockResponse> getBatchStock(GetBatchStockQuery query) {
        log.debug("Getting batch stock for {} products", query.getProductIds().size());
        
        List<Product> products = loadProductsByConditionPort.loadProductsByIds(query.getProductIds());
        
        return products.stream()
            .filter(product -> query.isIncludeInactive() || product.isActive())
            .map(StockResponse::new)
            .collect(Collectors.toList());
    }
    
    @Override
    public ReservationResponse getReservation(GetReservationQuery query) {
        log.debug("Getting reservation: {}", query.getReservationId());
        
        // 예약 ID로 상품 찾기
        Product product = loadProductsByConditionPort.loadProductByReservationId(
            query.getReservationId().getValue().toString()
        );
        
        if (product == null) {
            throw new ReservationNotFoundException(query.getReservationId());
        }
        
        StockReservation reservation = product.getReservation(query.getReservationId());
        if (reservation == null) {
            throw new ReservationNotFoundException(query.getReservationId());
        }
        
        return new ReservationResponse(reservation, product.getProductId());
    }
    
    @Override
    public List<LowStockResponse> getLowStockProducts(GetLowStockQuery query) {
        log.debug("Getting low stock products with limit: {}", query.getLimit());
        
        // 임계값 결정 (오버라이드가 있으면 사용, 없으면 기본값)
        StockQuantity threshold = query.getThresholdOverride() != null 
            ? query.getThresholdOverride() 
            : StockQuantity.of(10); // 기본 임계값
        
        List<Product> lowStockProducts = loadProductsByConditionPort.loadLowStockProducts(
            threshold,
            query.isIncludeInactive(),
            query.getLimit()
        );
        
        List<LowStockResponse> responses = new ArrayList<>();
        
        for (Product product : lowStockProducts) {
            int severity = stockDomainService.evaluateStockSeverity(product);
            String recommendedAction = getRecommendedAction(severity, product);
            
            responses.add(new LowStockResponse(product, severity, recommendedAction));
        }
        
        return responses;
    }
    
    /**
     * 재고 심각도에 따른 권장 조치 생성
     */
    private String getRecommendedAction(int severity, Product product) {
        switch (severity) {
            case 3: // 품절
                return String.format("URGENT: Product %s is out of stock. Immediate restocking required.", 
                    product.getProductName());
            case 2: // 매우 부족
                return String.format("HIGH PRIORITY: Product %s stock is critically low (%d units). Order immediately.", 
                    product.getProductName(), product.getAvailableQuantity().getValue());
            case 1: // 부족
                return String.format("ATTENTION: Product %s stock is below threshold (%d units). Plan restocking.", 
                    product.getProductName(), product.getAvailableQuantity().getValue());
            default: // 충분
                return "Stock level is adequate.";
        }
    }
}