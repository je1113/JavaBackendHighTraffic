package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 재고 이동 JPA 엔티티
 * 
 * 모든 재고 변동 내역을 추적하기 위한 감사(Audit) 테이블입니다.
 * 재고 추적, 분석, 문제 해결에 활용됩니다.
 */
@Entity
@Table(name = "stock_movements", indexes = {
    @Index(name = "idx_movements_product_id", columnList = "product_id"),
    @Index(name = "idx_movements_type", columnList = "movement_type"),
    @Index(name = "idx_movements_created_at", columnList = "created_at"),
    @Index(name = "idx_movements_reference", columnList = "reference_type,reference_id"),
    @Index(name = "idx_movements_product_date", columnList = "product_id,created_at")
})
@NamedQueries({
    @NamedQuery(
        name = "StockMovementJpaEntity.findByProductId",
        query = "SELECT m FROM StockMovementJpaEntity m " +
                "WHERE m.product.id = :productId ORDER BY m.createdAt DESC"
    ),
    @NamedQuery(
        name = "StockMovementJpaEntity.findByReference",
        query = "SELECT m FROM StockMovementJpaEntity m " +
                "WHERE m.referenceType = :referenceType AND m.referenceId = :referenceId"
    ),
    @NamedQuery(
        name = "StockMovementJpaEntity.calculateStockBalance",
        query = "SELECT SUM(CASE WHEN m.movementType IN ('STOCK_IN', 'RETURN', 'ADJUSTMENT_INCREASE') " +
                "THEN m.quantity ELSE -m.quantity END) " +
                "FROM StockMovementJpaEntity m WHERE m.product.id = :productId"
    )
})
public class StockMovementJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "balance_before", nullable = false)
    private Integer balanceBefore;
    
    @Column(name = "balance_after", nullable = false)
    private Integer balanceAfter;
    
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    
    @Column(name = "reference_id")
    private String referenceId;
    
    @Column(name = "reason", length = 500)
    private String reason;
    
    @Column(name = "performed_by", length = 100)
    private String performedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    /**
     * JPA를 위한 기본 생성자
     */
    protected StockMovementJpaEntity() {
    }
    
    /**
     * 빌더를 통한 생성자
     */
    private StockMovementJpaEntity(Builder builder) {
        this.id = builder.id;
        this.product = builder.product;
        this.movementType = builder.movementType;
        this.quantity = builder.quantity;
        this.balanceBefore = builder.balanceBefore;
        this.balanceAfter = builder.balanceAfter;
        this.referenceType = builder.referenceType;
        this.referenceId = builder.referenceId;
        this.reason = builder.reason;
        this.performedBy = builder.performedBy;
    }
    
    /**
     * 재고 이동 기록 생성
     */
    public static StockMovementJpaEntity recordMovement(
            ProductJpaEntity product,
            MovementType type,
            Integer quantity,
            Integer balanceBefore,
            String referenceType,
            String referenceId,
            String reason) {
        
        Integer balanceAfter = calculateBalanceAfter(balanceBefore, quantity, type);
        
        return new Builder()
            .id(UUID.randomUUID().toString())
            .product(product)
            .movementType(type)
            .quantity(Math.abs(quantity))
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .reason(reason)
            .performedBy("SYSTEM")
            .build();
    }
    
    /**
     * 변경 후 재고 계산
     */
    private static Integer calculateBalanceAfter(Integer balanceBefore, Integer quantity, MovementType type) {
        return switch (type) {
            case STOCK_IN, RETURN, ADJUSTMENT_INCREASE -> balanceBefore + Math.abs(quantity);
            case STOCK_OUT, RESERVATION, ADJUSTMENT_DECREASE -> balanceBefore - Math.abs(quantity);
        };
    }
    
    /**
     * 재고 이동 타입
     */
    public enum MovementType {
        STOCK_IN("입고"),               // 상품 입고
        STOCK_OUT("출고"),              // 상품 출고 (판매)
        RESERVATION("예약"),            // 재고 예약
        RETURN("반품"),                 // 반품으로 인한 재고 증가
        ADJUSTMENT_INCREASE("재고증가조정"), // 재고 조정 (증가)
        ADJUSTMENT_DECREASE("재고감소조정"); // 재고 조정 (감소)
        
        private final String description;
        
        MovementType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
        
        public boolean isIncrease() {
            return this == STOCK_IN || this == RETURN || this == ADJUSTMENT_INCREASE;
        }
        
        public boolean isDecrease() {
            return this == STOCK_OUT || this == RESERVATION || this == ADJUSTMENT_DECREASE;
        }
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public ProductJpaEntity getProduct() {
        return product;
    }
    
    public MovementType getMovementType() {
        return movementType;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public Integer getBalanceBefore() {
        return balanceBefore;
    }
    
    public Integer getBalanceAfter() {
        return balanceAfter;
    }
    
    public String getReferenceType() {
        return referenceType;
    }
    
    public String getReferenceId() {
        return referenceId;
    }
    
    public String getReason() {
        return reason;
    }
    
    public String getPerformedBy() {
        return performedBy;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockMovementJpaEntity that = (StockMovementJpaEntity) o;
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
        private ProductJpaEntity product;
        private MovementType movementType;
        private Integer quantity;
        private Integer balanceBefore;
        private Integer balanceAfter;
        private String referenceType;
        private String referenceId;
        private String reason;
        private String performedBy;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder product(ProductJpaEntity product) {
            this.product = product;
            return this;
        }
        
        public Builder movementType(MovementType movementType) {
            this.movementType = movementType;
            return this;
        }
        
        public Builder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder balanceBefore(Integer balanceBefore) {
            this.balanceBefore = balanceBefore;
            return this;
        }
        
        public Builder balanceAfter(Integer balanceAfter) {
            this.balanceAfter = balanceAfter;
            return this;
        }
        
        public Builder referenceType(String referenceType) {
            this.referenceType = referenceType;
            return this;
        }
        
        public Builder referenceId(String referenceId) {
            this.referenceId = referenceId;
            return this;
        }
        
        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }
        
        public Builder performedBy(String performedBy) {
            this.performedBy = performedBy;
            return this;
        }
        
        public StockMovementJpaEntity build() {
            return new StockMovementJpaEntity(this);
        }
    }
}