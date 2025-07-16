package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import com.hightraffic.ecommerce.inventory.application.port.out.*;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.Stock;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재고 영속성 어댑터
 * 
 * 도메인 모델과 JPA 엔티티 간의 변환을 담당하며,
 * 영속성 관련 포트 인터페이스를 구현합니다.
 * 높은 동시성 환경에서의 재고 관리를 위해 최적화되었습니다.
 */
@Component
@Transactional(readOnly = true)
public class InventoryPersistenceAdapter implements 
        LoadProductPort, 
        SaveProductPort, 
        LoadProductsByConditionPort,
        InventoryPersistencePort {
    
    private static final Logger log = LoggerFactory.getLogger(InventoryPersistenceAdapter.class);
    
    private final ProductJpaRepository productRepository;
    private final StockReservationJpaRepository reservationRepository;
    private final ProductMapper mapper;
    
    public InventoryPersistenceAdapter(
            ProductJpaRepository productRepository,
            StockReservationJpaRepository reservationRepository) {
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.mapper = new ProductMapper();
    }
    
    @Override
    @Transactional
    public Product saveProduct(Product product) {
        log.debug("상품 저장 시작: productId={}", product.getProductId().getValue());
        
        ProductJpaEntity entity = mapper.toJpaEntity(product);
        ProductJpaEntity savedEntity = productRepository.save(entity);
        
        log.info("상품 저장 완료: productId={}, version={}", 
            savedEntity.getId(), savedEntity.getVersion());
        
        // 저장된 엔티티를 다시 도메인 모델로 변환하여 반환
        return mapper.toDomainModel(savedEntity);
    }
    
    @Override
    public Optional<Product> loadProduct(ProductId productId) {
        log.debug("상품 조회 시작: productId={}", productId.getValue());
        
        return productRepository.findById(productId.getValue().toString())
            .map(mapper::toDomainModel);
    }
    
    @Override
    public boolean existsProduct(ProductId productId) {
        log.debug("상품 존재 여부 확인: productId={}", productId.getValue());
        
        return productRepository.existsById(productId.getValue().toString());
    }
    
    @Override
    public List<Product> loadProductsByIds(List<ProductId> productIds) {
        log.debug("상품 ID 목록으로 조회: size={}", productIds.size());
        
        List<String> idStrings = productIds.stream()
            .map(id -> id.getValue().toString())
            .collect(Collectors.toList());
            
        return productRepository.findAllById(idStrings).stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Product> loadLowStockProducts(StockQuantity threshold, boolean includeInactive, int limit) {
        log.debug("재고 부족 상품 조회: threshold={}, includeInactive={}, limit={}", 
            threshold.getValue(), includeInactive, limit);
            
        return productRepository.findLowStockProducts(PageRequest.of(0, limit)).stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public Product loadProductByReservationId(String reservationId) {
        log.debug("예약 ID로 상품 조회: reservationId={}", reservationId);
        
        // This would require a more complex query to find product by reservation
        // For now, throwing unsupported operation
        throw new UnsupportedOperationException("Finding product by reservation ID needs proper implementation");
    }
    
    @Transactional
    public Optional<Product> loadProductWithLock(ProductId productId) {
        log.debug("상품 조회 시작 (잠금): productId={}", productId.getValue());
        
        return productRepository.findByIdWithLock(productId.getValue().toString())
            .map(mapper::toDomainModel);
    }
    
    public List<Product> loadProductsByCondition(String condition, Object... params) {
        log.debug("조건별 상품 조회: condition={}", condition);
        
        return switch (condition) {
            case "LOW_STOCK" -> productRepository.findLowStockProducts(PageRequest.of(0, 100))
                .stream()
                .map(mapper::toDomainModel)
                .collect(Collectors.toList());
                
            case "CATEGORY" -> {
                String category = (String) params[0];
                yield productRepository.findActiveByCategory(category, PageRequest.of(0, 100))
                    .stream()
                    .map(mapper::toDomainModel)
                    .collect(Collectors.toList());
            }
            
            case "OUT_OF_STOCK" -> productRepository.findOutOfStockProducts(PageRequest.of(0, 50))
                .stream()
                .map(mapper::toDomainModel)
                .collect(Collectors.toList());
                
            default -> List.of();
        };
    }
    
    @Transactional
    public void save(Product product) {
        log.debug("상품 저장 시작: productId={}", product.getProductId().getValue());
        
        ProductJpaEntity entity = mapper.toJpaEntity(product);
        
        // 재고 변동 기록
        if (entity.getId() != null) {
            productRepository.findById(entity.getId()).ifPresent(existing -> {
                recordStockMovement(existing, entity);
            });
        }
        
        productRepository.save(entity);
        
        // 예약 정보도 함께 저장
        saveReservations(product, entity);
        
        log.info("상품 저장 완료: productId={}, totalStock={}, availableStock={}", 
            product.getProductId().getValue(), 
            product.getStock().getTotalQuantity().getValue(),
            product.getStock().getAvailableQuantity().getValue());
    }
    
    @Transactional
    public boolean reserveStock(ProductId productId, Integer quantity, String orderId) {
        log.debug("재고 예약 시작: productId={}, quantity={}, orderId={}", 
            productId.getValue(), quantity, orderId);
        
        Optional<ProductJpaEntity> productOpt = productRepository.findByIdWithLock(productId.getValue().toString());
        if (productOpt.isEmpty()) {
            throw new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId.getValue().toString());
        }
        
        ProductJpaEntity product = productOpt.get();
        
        // 재고 예약 가능 여부 확인
        if (!product.reserveStock(quantity)) {
            log.warn("재고 부족으로 예약 실패: productId={}, requested={}, available={}", 
                productId.getValue(), quantity, product.getAvailableQuantity());
            return false;
        }
        
        // 예약 엔티티 생성
        StockReservationJpaEntity reservation = StockReservationJpaEntity.createNew(
            product, orderId, orderId + "-item", quantity, 30
        );
        
        product.getReservations().add(reservation);
        
        // 재고 이동 기록
        recordStockMovement(
            product, 
            StockMovementJpaEntity.MovementType.RESERVATION,
            quantity,
            "ORDER",
            orderId,
            "주문에 따른 재고 예약"
        );
        
        productRepository.save(product);
        
        log.info("재고 예약 성공: productId={}, quantity={}, orderId={}, reservationId={}", 
            productId.getValue(), quantity, orderId, reservation.getId());
        
        return true;
    }
    
    @Transactional
    public void releaseStock(ProductId productId, Integer quantity, String orderId) {
        log.debug("재고 예약 해제 시작: productId={}, quantity={}, orderId={}", 
            productId.getValue(), quantity, orderId);
        
        ProductJpaEntity product = productRepository.findByIdWithLock(productId.getValue().toString())
            .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId.getValue()));
        
        // 주문에 대한 예약 찾기
        List<StockReservationJpaEntity> reservations = reservationRepository.findActiveByOrderId(orderId);
        
        for (StockReservationJpaEntity reservation : reservations) {
            if (reservation.getProduct().getId().equals(product.getId()) && reservation.isActive()) {
                // 예약 취소
                reservation.cancel("주문 취소");
                product.releaseStock(reservation.getQuantity());
                
                // 재고 이동 기록
                recordStockMovement(
                    product,
                    StockMovementJpaEntity.MovementType.RETURN,
                    reservation.getQuantity(),
                    "ORDER_CANCEL",
                    orderId,
                    "주문 취소로 인한 재고 복원"
                );
                
                log.info("재고 예약 해제 완료: productId={}, quantity={}, orderId={}, reservationId={}", 
                    productId.getValue(), reservation.getQuantity(), orderId, reservation.getId());
            }
        }
        
        productRepository.save(product);
    }
    
    @Transactional
    public void deductStock(ProductId productId, Integer quantity, String orderId) {
        log.debug("재고 차감 시작: productId={}, quantity={}, orderId={}", 
            productId.getValue(), quantity, orderId);
        
        ProductJpaEntity product = productRepository.findByIdWithLock(productId.getValue().toString())
            .orElseThrow(() -> new ProductNotFoundException("상품을 찾을 수 없습니다: " + productId.getValue()));
        
        // 예약된 재고에서 차감
        if (!product.deductStock(quantity)) {
            throw new IllegalStateException("예약된 재고가 부족합니다");
        }
        
        // 예약 확정
        reservationRepository.confirmOrderReservations(orderId, Instant.now());
        
        // 재고 이동 기록
        recordStockMovement(
            product,
            StockMovementJpaEntity.MovementType.STOCK_OUT,
            quantity,
            "ORDER_COMPLETE",
            orderId,
            "주문 완료로 인한 재고 차감"
        );
        
        productRepository.save(product);
        
        log.info("재고 차감 완료: productId={}, quantity={}, orderId={}", 
            productId.getValue(), quantity, orderId);
    }
    
    /**
     * 만료된 예약 처리
     */
    @Transactional
    public void processExpiredReservations() {
        log.debug("만료된 예약 처리 시작");
        
        List<StockReservationJpaEntity> expiredReservations = 
            reservationRepository.findExpiredReservations(Instant.now());
        
        for (StockReservationJpaEntity reservation : expiredReservations) {
            ProductJpaEntity product = reservation.getProduct();
            
            // 잠금 획득
            productRepository.findByIdWithLock(product.getId()).ifPresent(lockedProduct -> {
                reservation.expire();
                lockedProduct.releaseStock(reservation.getQuantity());
                
                log.info("예약 만료 처리: reservationId={}, productId={}, quantity={}", 
                    reservation.getId(), product.getId(), reservation.getQuantity());
            });
        }
    }
    
    /**
     * 재고 이동 기록
     */
    private void recordStockMovement(ProductJpaEntity existing, ProductJpaEntity updated) {
        int quantityDiff = updated.getTotalQuantity() - existing.getTotalQuantity();
        if (quantityDiff != 0) {
            StockMovementJpaEntity.MovementType type = quantityDiff > 0 ? 
                StockMovementJpaEntity.MovementType.STOCK_IN : 
                StockMovementJpaEntity.MovementType.STOCK_OUT;
            
            recordStockMovement(existing, type, Math.abs(quantityDiff), 
                "SYSTEM", null, "시스템 재고 조정");
        }
    }
    
    private void recordStockMovement(
            ProductJpaEntity product,
            StockMovementJpaEntity.MovementType type,
            Integer quantity,
            String referenceType,
            String referenceId,
            String reason) {
        
        StockMovementJpaEntity movement = StockMovementJpaEntity.recordMovement(
            product, type, quantity, product.getTotalQuantity(), 
            referenceType, referenceId, reason
        );
        
        product.getStockMovements().add(movement);
    }
    
    /**
     * 예약 정보 저장
     */
    private void saveReservations(Product domain, ProductJpaEntity entity) {
        // 도메인 모델의 예약 정보를 엔티티에 반영
        // 실제 구현은 도메인 모델 구조에 따라 조정 필요
    }
    
    /**
     * 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼
     */
    private static class ProductMapper {
        
        /**
         * JPA 엔티티를 도메인 모델로 변환
         */
        public Product toDomainModel(ProductJpaEntity entity) {
            ProductId productId = ProductId.of(entity.getId());
            StockQuantity totalQuantity = StockQuantity.of(entity.getTotalQuantity());
            
            Product product = new Product(productId, entity.getName(), totalQuantity);
            
            // 재고 정보 설정
            Stock stock = product.getStock();
            stock.adjustAvailableQuantity(StockQuantity.of(entity.getAvailableQuantity()));
            stock.adjustReservedQuantity(StockQuantity.of(entity.getReservedQuantity()));
            
            // 추가 정보 설정
            product.updateLowStockThreshold(StockQuantity.of(entity.getMinimumStockLevel()));
            if (!entity.getStatus().equals(ProductJpaEntity.ProductStatus.ACTIVE)) {
                product.deactivate();
            }
            
            // 버전 정보
            product.setVersion(entity.getVersion());
            
            return product;
        }
        
        /**
         * 도메인 모델을 JPA 엔티티로 변환
         */
        public ProductJpaEntity toJpaEntity(Product domain) {
            ProductJpaEntity.Builder builder = new ProductJpaEntity.Builder()
                .id(domain.getProductId().getValue().toString())
                .name(domain.getProductName())
                .totalQuantity(domain.getStock().getTotalQuantity().getValue())
                .availableQuantity(domain.getStock().getAvailableQuantity().getValue())
                .reservedQuantity(domain.getStock().getReservedQuantity().getValue())
                .minimumStockLevel(domain.getLowStockThreshold().getValue())
                .status(domain.isActive() ? 
                    ProductJpaEntity.ProductStatus.ACTIVE : 
                    ProductJpaEntity.ProductStatus.INACTIVE);
            
            // SKU와 카테고리는 기존 엔티티에서 가져오거나 기본값 설정
            builder.sku(domain.getProductId().getValue().toString())
                   .category("DEFAULT")
                   .price(java.math.BigDecimal.ZERO)
                   .currency("KRW");
            
            return builder.build();
        }
    }
    
    /**
     * 재고 통계 조회
     */
    public InventoryStatistics getInventoryStatistics() {
        long activeProducts = productRepository.countActiveProducts();
        Double totalValue = productRepository.calculateTotalInventoryValue();
        
        List<Object[]> categoryStats = productRepository.getCategoryStockStatistics();
        List<Object[]> turnoverData = productRepository.getInventoryTurnoverData(
            Instant.now().minusSeconds(30L * 24 * 60 * 60)
        );
        
        return new InventoryStatistics(activeProducts, totalValue, categoryStats, turnoverData);
    }
    
    @Override
    public Optional<Product> loadProductForUpdate(ProductId productId) {
        return loadProductWithLock(productId);
    }
    
    @Override
    public List<Product> loadProductsForUpdate(Set<ProductId> productIds) {
        return loadProductsByIds(new ArrayList<>(productIds));
    }
    
    @Override
    public List<Product> loadProductsWithExpiredReservations(LocalDateTime before) {
        // This requires complex query implementation
        log.warn("Loading products with expired reservations needs proper implementation");
        return List.of();
    }
    
    @Override
    public List<Product> loadProductsByStockRange(java.math.BigDecimal minQuantity, java.math.BigDecimal maxQuantity) {
        // This requires custom query implementation
        log.warn("Loading products by stock range needs proper implementation");
        return List.of();
    }
    
    @Override
    public List<Product> loadProductsWithActiveReservations() {
        // This requires custom query implementation
        log.warn("Loading products with active reservations needs proper implementation");
        return List.of();
    }
    
    @Override
    public List<Product> saveProducts(List<Product> products) {
        return products.stream()
            .map(this::saveProduct)
            .collect(Collectors.toList());
    }
    
    @Override
    public void deleteProduct(ProductId productId) {
        productRepository.deleteById(productId.getValue().toString());
    }
    
    @Override
    public List<InventoryPersistencePort.StockSummary> loadStockSummary() {
        // This requires custom query to get stock summary
        log.warn("Loading stock summary needs proper implementation");
        return List.of();
    }
    
    /**
     * 재고 통계 DTO
     */
    public record InventoryStatistics(
        long activeProductCount,
        Double totalInventoryValue,
        List<Object[]> categoryStatistics,
        List<Object[]> turnoverData
    ) {}
}