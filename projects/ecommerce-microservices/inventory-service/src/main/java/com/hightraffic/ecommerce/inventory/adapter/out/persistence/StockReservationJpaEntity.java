package com.hightraffic.ecommerce.inventory.adapter.out.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 재고 예약 JPA 엔티티
 * 
 * 주문 처리 중 재고를 예약하고 추적하는 역할을 합니다.
 * 동시성 제어와 타임아웃 관리를 통해 안정적인 재고 관리를 지원합니다.
 */
@Entity
@Table(name = "stock_reservations", indexes = {
    @Index(name = "idx_reservations_order_id", columnList = "order_id"),
    @Index(name = "idx_reservations_product_id", columnList = "product_id"),
    @Index(name = "idx_reservations_status", columnList = "status"),
    @Index(name = "idx_reservations_expires_at", columnList = "expires_at"),
    @Index(name = "idx_reservations_order_product", columnList = "order_id,product_id", unique = true),
    @Index(name = "idx_reservations_status_expires", columnList = "status,expires_at")
})
@NamedQueries({
    @NamedQuery(
        name = "StockReservationJpaEntity.findByOrderId",
        query = "SELECT r FROM StockReservationJpaEntity r WHERE r.orderId = :orderId"
    ),
    @NamedQuery(
        name = "StockReservationJpaEntity.findActiveByProductId",
        query = "SELECT r FROM StockReservationJpaEntity r " +
                "WHERE r.product.id = :productId AND r.status = 'ACTIVE'"
    ),
    @NamedQuery(
        name = "StockReservationJpaEntity.findExpiredReservations",
        query = "SELECT r FROM StockReservationJpaEntity r " +
                "WHERE r.status = 'ACTIVE' AND r.expiresAt < :currentTime"
    ),
    @NamedQuery(
        name = "StockReservationJpaEntity.sumActiveReservationsByProductId",
        query = "SELECT COALESCE(SUM(r.quantity), 0) FROM StockReservationJpaEntity r " +
                "WHERE r.product.id = :productId AND r.status = 'ACTIVE'"
    )
})
public class StockReservationJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductJpaEntity product;
    
    @Column(name = "order_id", nullable = false)
    private String orderId;
    
    @Column(name = "order_item_id", nullable = false)
    private String orderItemId;
    
    @Column(name = "quantity", nullable = false)
    private Integer quantity;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    /**
     * JPA를 위한 기본 생성자
     */
    protected StockReservationJpaEntity() {
    }
    
    /**
     * 빌더를 통한 생성자
     */
    private StockReservationJpaEntity(Builder builder) {
        this.id = builder.id;
        this.product = builder.product;
        this.orderId = builder.orderId;
        this.orderItemId = builder.orderItemId;
        this.quantity = builder.quantity;
        this.status = builder.status;
        this.expiresAt = builder.expiresAt;
    }
    
    /**
     * 새로운 예약 생성
     * 기본 예약 시간은 30분
     */
    public static StockReservationJpaEntity createNew(
            ProductJpaEntity product,
            String orderId,
            String orderItemId,
            Integer quantity,
            Integer reservationMinutes) {
        
        return new Builder()
            .id(UUID.randomUUID().toString())
            .product(product)
            .orderId(orderId)
            .orderItemId(orderItemId)
            .quantity(quantity)
            .status(ReservationStatus.ACTIVE)
            .expiresAt(Instant.now().plusSeconds(reservationMinutes * 60L))
            .build();
    }
    
    /**
     * 예약 확정
     */
    public void confirm() {
        if (status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("활성 상태의 예약만 확정할 수 있습니다");
        }
        this.status = ReservationStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }
    
    /**
     * 예약 취소
     */
    public void cancel(String reason) {
        if (status == ReservationStatus.CANCELLED) {
            return; // 이미 취소됨
        }
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.cancellationReason = reason;
    }
    
    /**
     * 예약 만료 처리
     */
    public void expire() {
        if (status != ReservationStatus.ACTIVE) {
            return;
        }
        this.status = ReservationStatus.EXPIRED;
        this.cancelledAt = Instant.now();
        this.cancellationReason = "Reservation expired";
    }
    
    /**
     * 예약 만료 여부 확인
     */
    public boolean isExpired() {
        return status == ReservationStatus.ACTIVE && 
               expiresAt.isBefore(Instant.now());
    }
    
    /**
     * 예약 활성 여부 확인
     */
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE && !isExpired();
    }
    
    /**
     * 예약 상태 enum
     */
    public enum ReservationStatus {
        ACTIVE,     // 활성 (예약중)
        CONFIRMED,  // 확정됨 (재고 차감됨)
        CANCELLED,  // 취소됨
        EXPIRED     // 만료됨
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public ProductJpaEntity getProduct() {
        return product;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getOrderItemId() {
        return orderItemId;
    }
    
    public Integer getQuantity() {
        return quantity;
    }
    
    public ReservationStatus getStatus() {
        return status;
    }
    
    public Instant getExpiresAt() {
        return expiresAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getConfirmedAt() {
        return confirmedAt;
    }
    
    public Instant getCancelledAt() {
        return cancelledAt;
    }
    
    public String getCancellationReason() {
        return cancellationReason;
    }
    
    public Long getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockReservationJpaEntity that = (StockReservationJpaEntity) o;
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
        private String orderId;
        private String orderItemId;
        private Integer quantity;
        private ReservationStatus status;
        private Instant expiresAt;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder product(ProductJpaEntity product) {
            this.product = product;
            return this;
        }
        
        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }
        
        public Builder orderItemId(String orderItemId) {
            this.orderItemId = orderItemId;
            return this;
        }
        
        public Builder quantity(Integer quantity) {
            this.quantity = quantity;
            return this;
        }
        
        public Builder status(ReservationStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }
        
        public StockReservationJpaEntity build() {
            return new StockReservationJpaEntity(this);
        }
    }
}