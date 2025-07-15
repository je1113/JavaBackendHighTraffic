package com.hightraffic.ecommerce.order.adapter.out.persistence;

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
 * 주문 JPA 엔티티
 * 
 * 주문 애그리게이트의 루트 엔티티를 표현합니다.
 * 도메인 모델과 분리되어 영속성 계층의 관심사를 처리합니다.
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
    @Index(name = "idx_orders_status", columnList = "status"),
    @Index(name = "idx_orders_created_at", columnList = "created_at"),
    @Index(name = "idx_orders_customer_status", columnList = "customer_id,status")
})
@NamedQueries({
    @NamedQuery(
        name = "OrderJpaEntity.findByCustomerIdOrderByCreatedAtDesc",
        query = "SELECT o FROM OrderJpaEntity o WHERE o.customerId = :customerId ORDER BY o.createdAt DESC"
    ),
    @NamedQuery(
        name = "OrderJpaEntity.findByCustomerIdAndStatus",
        query = "SELECT o FROM OrderJpaEntity o WHERE o.customerId = :customerId AND o.status = :status"
    ),
    @NamedQuery(
        name = "OrderJpaEntity.countByCustomerIdAndCreatedAtAfter",
        query = "SELECT COUNT(o) FROM OrderJpaEntity o WHERE o.customerId = :customerId AND o.createdAt > :after"
    )
})
public class OrderJpaEntity {
    
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatusEntity status;
    
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "payment_id")
    private String paymentId;
    
    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;
    
    @OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    @OrderBy("productId ASC")
    private List<OrderItemJpaEntity> items = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    
    @Column(name = "paid_at")
    private Instant paidAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    
    /**
     * JPA를 위한 기본 생성자
     */
    protected OrderJpaEntity() {
    }
    
    /**
     * 빌더를 통한 생성자
     */
    private OrderJpaEntity(Builder builder) {
        this.id = builder.id;
        this.customerId = builder.customerId;
        this.status = builder.status;
        this.totalAmount = builder.totalAmount;
        this.currency = builder.currency;
        this.paymentId = builder.paymentId;
        this.cancelledReason = builder.cancelledReason;
        this.createdAt = builder.createdAt;
        this.confirmedAt = builder.confirmedAt;
        this.paidAt = builder.paidAt;
        this.completedAt = builder.completedAt;
        this.cancelledAt = builder.cancelledAt;
    }
    
    /**
     * 새로운 주문 생성
     */
    public static OrderJpaEntity createNew(String customerId, BigDecimal totalAmount, String currency) {
        return new Builder()
            .id(UUID.randomUUID().toString())
            .customerId(customerId)
            .status(OrderStatusEntity.PENDING)
            .totalAmount(totalAmount)
            .currency(currency)
            .build();
    }
    
    /**
     * 주문 아이템 추가
     */
    public void addItem(OrderItemJpaEntity item) {
        items.add(item);
        item.setOrder(this);
    }
    
    /**
     * 주문 아이템 제거
     */
    public void removeItem(OrderItemJpaEntity item) {
        items.remove(item);
        item.setOrder(null);
    }
    
    /**
     * 주문 확정
     */
    public void confirm() {
        this.status = OrderStatusEntity.CONFIRMED;
        this.confirmedAt = Instant.now();
    }
    
    /**
     * 결제 완료 처리
     */
    public void markAsPaid(String paymentId) {
        this.status = OrderStatusEntity.PAID;
        this.paymentId = paymentId;
        this.paidAt = Instant.now();
    }
    
    /**
     * 주문 완료 처리
     */
    public void complete() {
        this.status = OrderStatusEntity.COMPLETED;
        this.completedAt = Instant.now();
    }
    
    /**
     * 주문 취소 처리
     */
    public void cancel(String reason) {
        this.status = OrderStatusEntity.CANCELLED;
        this.cancelledReason = reason;
        this.cancelledAt = Instant.now();
    }
    
    /**
     * 주문 상태 enum
     */
    public enum OrderStatusEntity {
        PENDING,      // 대기중
        CONFIRMED,    // 확정됨
        PAID,         // 결제완료
        COMPLETED,    // 완료됨
        CANCELLED     // 취소됨
    }
    
    // Getters
    public String getId() {
        return id;
    }
    
    public String getCustomerId() {
        return customerId;
    }
    
    public OrderStatusEntity getStatus() {
        return status;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public String getPaymentId() {
        return paymentId;
    }
    
    public String getCancelledReason() {
        return cancelledReason;
    }
    
    public List<OrderItemJpaEntity> getItems() {
        return new ArrayList<>(items);
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public Instant getConfirmedAt() {
        return confirmedAt;
    }
    
    public Instant getPaidAt() {
        return paidAt;
    }
    
    public Instant getCompletedAt() {
        return completedAt;
    }
    
    public Instant getCancelledAt() {
        return cancelledAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderJpaEntity that = (OrderJpaEntity) o;
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
        private String customerId;
        private OrderStatusEntity status;
        private BigDecimal totalAmount;
        private String currency;
        private String paymentId;
        private String cancelledReason;
        private Instant createdAt;
        private Instant confirmedAt;
        private Instant paidAt;
        private Instant completedAt;
        private Instant cancelledAt;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }
        
        public Builder status(OrderStatusEntity status) {
            this.status = status;
            return this;
        }
        
        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }
        
        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }
        
        public Builder paymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }
        
        public Builder cancelledReason(String cancelledReason) {
            this.cancelledReason = cancelledReason;
            return this;
        }
        
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public Builder confirmedAt(Instant confirmedAt) {
            this.confirmedAt = confirmedAt;
            return this;
        }
        
        public Builder paidAt(Instant paidAt) {
            this.paidAt = paidAt;
            return this;
        }
        
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder cancelledAt(Instant cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }
        
        public OrderJpaEntity build() {
            return new OrderJpaEntity(this);
        }
    }
}