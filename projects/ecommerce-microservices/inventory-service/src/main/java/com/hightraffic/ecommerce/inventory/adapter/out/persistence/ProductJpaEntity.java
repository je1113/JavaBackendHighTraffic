package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 상품 JPA 엔티티
 * 
 * 재고 관리의 핵심이 되는 상품 정보를 영속화합니다.
 * 높은 동시성 환경에서의 재고 관리를 위해 최적화된 설계를 적용했습니다.
 */
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_products_sku", columnList = "sku", unique = true),
    @Index(name = "idx_products_category", columnList = "category"),
    @Index(name = "idx_products_status", columnList = "status"),
    @Index(name = "idx_products_available_quantity", columnList = "available_quantity"),
    @Index(name = "idx_products_category_status", columnList = "category,status"),
    @Index(name = "idx_products_low_stock", columnList = "available_quantity,minimum_stock_level")
})
@NamedQueries({
    @NamedQuery(
        name = "ProductJpaEntity.findBySku",
        query = "SELECT p FROM ProductJpaEntity p WHERE p.sku = :sku"
    ),
    @NamedQuery(
        name = "ProductJpaEntity.findLowStockProducts",
        query = "SELECT p FROM ProductJpaEntity p WHERE p.availableQuantity <= p.minimumStockLevel " +
                "AND p.status = 'ACTIVE' ORDER BY p.availableQuantity ASC"
    ),
    @NamedQuery(
        name = "ProductJpaEntity.findByCategory",
        query = "SELECT p FROM ProductJpaEntity p WHERE p.category = :category AND p.status = 'ACTIVE'"
    ),
    @NamedQuery(
        name = "ProductJpaEntity.updateStock",
        query = "UPDATE ProductJpaEntity p SET p.availableQuantity = :newQuantity, " +
                "p.reservedQuantity = :reservedQuantity, p.version = p.version + 1 " +
                "WHERE p.id = :productId AND p.version = :currentVersion"
    )
})
@EntityListeners(StockEventListener.class)
public class ProductJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;
    
    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category", nullable = false, length = 100)
    private String category;
    
    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;
    
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;
    
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;
    
    @Column(name = "minimum_stock_level", nullable = false)
    private Integer minimumStockLevel;
    
    @Column(name = "maximum_stock_level")
    private Integer maximumStockLevel;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;
    
    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    private List<StockReservationJpaEntity> reservations = new ArrayList<>();
    
    @OneToMany(
        mappedBy = "product",
        cascade = CascadeType.ALL,
        fetch = FetchType.LAZY
    )
    @OrderBy("createdAt DESC")
    private List<StockMovementJpaEntity> stockMovements = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "last_restock_at")
    private Instant lastRestockAt;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    /**
     * JPA를 위한 기본 생성자
     */
    protected ProductJpaEntity() {
    }
    
    /**
     * 빌더를 통한 생성자
     */
    private ProductJpaEntity(Builder builder) {
        this.id = builder.id;
        this.sku = builder.sku;
        this.name = builder.name;
        this.description = builder.description;
        this.category = builder.category;
        this.price = builder.price;
        this.currency = builder.currency;
        this.totalQuantity = builder.totalQuantity;
        this.availableQuantity = builder.availableQuantity;
        this.reservedQuantity = builder.reservedQuantity;
        this.minimumStockLevel = builder.minimumStockLevel;
        this.maximumStockLevel = builder.maximumStockLevel;
        this.status = builder.status;
    }
    
    /**
     * 새로운 상품 생성
     */
    public static ProductJpaEntity createNew(
            String sku,
            String name,
            String category,
            BigDecimal price,
            String currency,
            Integer initialStock,
            Integer minimumStock) {
        
        return new Builder()
            .id(UUID.randomUUID().toString())
            .sku(sku)
            .name(name)
            .category(category)
            .price(price)
            .currency(currency)
            .totalQuantity(initialStock)
            .availableQuantity(initialStock)
            .reservedQuantity(0)
            .minimumStockLevel(minimumStock)
            .status(ProductStatus.ACTIVE)
            .build();
    }
    
    /**
     * 재고 예약
     */
    public boolean reserveStock(Integer quantity) {
        if (availableQuantity >= quantity) {
            availableQuantity -= quantity;
            reservedQuantity += quantity;
            return true;
        }
        return false;
    }
    
    /**
     * 재고 예약 해제
     */
    public void releaseStock(Integer quantity) {
        availableQuantity += quantity;
        reservedQuantity = Math.max(0, reservedQuantity - quantity);
    }
    
    /**
     * 재고 차감 (예약된 재고에서)
     */
    public boolean deductStock(Integer quantity) {
        if (reservedQuantity >= quantity) {
            reservedQuantity -= quantity;
            totalQuantity -= quantity;
            return true;
        }
        return false;
    }
    
    /**
     * 재고 추가
     */
    public void addStock(Integer quantity) {
        totalQuantity += quantity;
        availableQuantity += quantity;
        lastRestockAt = Instant.now();
    }
    
    /**
     * 재고 조정
     */
    public void adjustStock(Integer newTotalQuantity) {
        int difference = newTotalQuantity - totalQuantity;
        totalQuantity = newTotalQuantity;
        availableQuantity = Math.max(0, availableQuantity + difference);
    }
    
    /**
     * 낮은 재고 여부 확인
     */
    public boolean isLowStock() {
        return availableQuantity <= minimumStockLevel;
    }
    
    /**
     * 재고 부족 여부 확인
     */
    public boolean isOutOfStock() {
        return availableQuantity <= 0;
    }
    
    /**
     * 과재고 여부 확인
     */
    public boolean isOverStock() {
        return maximumStockLevel != null && totalQuantity > maximumStockLevel;
    }
    
    /**
     * 상품 상태 enum
     */
    public enum ProductStatus {
        ACTIVE,         // 활성
        INACTIVE,       // 비활성
        DISCONTINUED,   // 단종
        OUT_OF_STOCK    // 품절
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public String getSku() {
        return sku;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getCategory() {
        return category;
    }
    
    public BigDecimal getPrice() {
        return price;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public Integer getTotalQuantity() {
        return totalQuantity;
    }
    
    public Integer getAvailableQuantity() {
        return availableQuantity;
    }
    
    public Integer getReservedQuantity() {
        return reservedQuantity;
    }
    
    public Integer getMinimumStockLevel() {
        return minimumStockLevel;
    }
    
    public Integer getMaximumStockLevel() {
        return maximumStockLevel;
    }
    
    public ProductStatus getStatus() {
        return status;
    }
    
    public List<StockReservationJpaEntity> getReservations() {
        return new ArrayList<>(reservations);
    }
    
    public List<StockMovementJpaEntity> getStockMovements() {
        return new ArrayList<>(stockMovements);
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Instant getLastRestockAt() {
        return lastRestockAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    // Setters for specific fields
    public void setStatus(ProductStatus status) {
        this.status = status;
    }
    
    public void setMinimumStockLevel(Integer minimumStockLevel) {
        this.minimumStockLevel = minimumStockLevel;
    }
    
    public void setMaximumStockLevel(Integer maximumStockLevel) {
        this.maximumStockLevel = maximumStockLevel;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductJpaEntity that = (ProductJpaEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    /**
     * 빌더 클래스
     */
    public static class Builder {
        private String id;
        private String sku;
        private String name;
        private String description;
        private String category;
        private BigDecimal price;
        private String currency;
        private Integer totalQuantity;
        private Integer availableQuantity;
        private Integer reservedQuantity;
        private Integer minimumStockLevel;
        private Integer maximumStockLevel;
        private ProductStatus status;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder sku(String sku) {
            this.sku = sku;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder totalQuantity(Integer totalQuantity) {
            this.totalQuantity = totalQuantity;
            return this;
        }
        
        public Builder availableQuantity(Integer availableQuantity) {
            this.availableQuantity = availableQuantity;
            return this;
        }
        
        public Builder reservedQuantity(Integer reservedQuantity) {
            this.reservedQuantity = reservedQuantity;
            return this;
        }
        
        public Builder minimumStockLevel(Integer minimumStockLevel) {
            this.minimumStockLevel = minimumStockLevel;
            return this;
        }
        
        public Builder maximumStockLevel(Integer maximumStockLevel) {
            this.maximumStockLevel = maximumStockLevel;
            return this;
        }
        
        public Builder status(ProductStatus status) {
            this.status = status;
            return this;
        }
        
        public ProductJpaEntity build() {
            return new ProductJpaEntity(this);
        }
    }
}