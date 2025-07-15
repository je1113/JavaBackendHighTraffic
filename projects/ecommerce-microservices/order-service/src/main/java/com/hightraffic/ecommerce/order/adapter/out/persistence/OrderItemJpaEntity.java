package com.hightraffic.ecommerce.order.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 주문 아이템 JPA 엔티티
 * 
 * 주문에 포함된 개별 아이템을 표현합니다.
 * OrderJpaEntity와 N:1 관계를 가집니다.
 */
@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_items_order_id", columnList = "order_id"),
    @Index(name = "idx_order_items_product_id", columnList = "product_id"),
    @Index(name = "idx_order_items_order_product", columnList = "order_id,product_id")
})
@NamedQueries({
    @NamedQuery(
        name = "OrderItemJpaEntity.findByOrderId",
        query = "SELECT oi FROM OrderItemJpaEntity oi WHERE oi.order.id = :orderId"
    ),
    @NamedQuery(
        name = "OrderItemJpaEntity.sumQuantityByProductId",
        query = "SELECT SUM(oi.quantity) FROM OrderItemJpaEntity oi " +
                "WHERE oi.productId = :productId AND oi.order.status NOT IN ('CANCELLED')"
    )
})
public class OrderItemJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;
    
    @Column(name = "product_id", nullable = false)
    private String productId;
    
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "reservation_id")
    private String reservationId;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    /**
     * JPA를 위한 기본 생성자
     */
    protected OrderItemJpaEntity() {
    }
    
    /**
     * 빌더를 통한 생성자
     */
    private OrderItemJpaEntity(Builder builder) {
        this.id = builder.id;
        this.productId = builder.productId;
        this.productName = builder.productName;
        this.quantity = builder.quantity;
        this.unitPrice = builder.unitPrice;
        this.totalPrice = builder.totalPrice;
        this.currency = builder.currency;
        this.reservationId = builder.reservationId;
    }
    
    /**
     * 새로운 주문 아이템 생성
     */
    public static OrderItemJpaEntity createNew(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            String currency) {
        
        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
        
        return new Builder()
            .id(UUID.randomUUID().toString())
            .productId(productId)
            .productName(productName)
            .quantity(quantity)
            .unitPrice(unitPrice)
            .totalPrice(totalPrice)
            .currency(currency)
            .build();
    }
    
    /**
     * 재고 예약 ID 설정
     */
    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }
    
    /**
     * 총 가격 재계산
     */
    public void recalculateTotalPrice() {
        this.totalPrice = this.unitPrice.multiply(BigDecimal.valueOf(this.quantity));
    }
    
    /**
     * 수량 업데이트
     */
    public void updateQuantity(Integer newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        this.quantity = newQuantity;
        recalculateTotalPrice();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public OrderJpaEntity getOrder() {
        return order;
    }
    
    void setOrder(OrderJpaEntity order) {
        this.order = order;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
    
    public BigDecimal getTotalPrice() {
        return totalPrice;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getReservationId() {
        return reservationId;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderItemJpaEntity that = (OrderItemJpaEntity) o;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "OrderItemJpaEntity{" +
                "id='" + id + '\'' +
                ", productId='" + productId + '\'' +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", totalPrice=" + totalPrice +
                ", currency='" + currency + '\'' +
                '}';
    }
    
    /**
     * 빌더 클래스
     */
    public static class Builder {
        private String id;
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String currency;
        private String reservationId;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder productId(String productId) {
            this.productId = productId;
            return this;
        }
        
        public Builder productName(String productName) {
            this.productName = productName;
            return this;
        }
        
        public Builder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder unitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
            return this;
        }
        
        public Builder totalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder reservationId(String reservationId) {
            this.reservationId = reservationId;
            return this;
        }
        
        public OrderItemJpaEntity build() {
            return new OrderItemJpaEntity(this);
        }
    }
}