package com.hightraffic.ecommerce.order.domain.model;

import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 주문 Aggregate Root
 * 주문과 관련된 모든 비즈니스 로직을 담당하는 핵심 도메인 객체
 */
@Entity
@Table(name = "orders")
@EntityListeners(AuditingEntityListener.class)
public class Order implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final int MAX_ITEMS = 100;
    
    @EmbeddedId
    private OrderId id;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "customer_id"))
    })
    private CustomerId customerId;
    
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items = new ArrayList<>();
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_amount")),
        @AttributeOverride(name = "currency.currencyCode", column = @Column(name = "currency"))
    })
    private Money totalAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    
    @Column(name = "notes")
    private String notes;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // 재고 예약 정보 (productId -> reservationId)
    @ElementCollection
    @CollectionTable(name = "order_stock_reservations", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "reservation_id")
    private Map<String, String> stockReservations = new HashMap<>();
    
    // JPA용 기본 생성자
    protected Order() {}
    
    /**
     * 새로운 주문 생성 Factory Method
     */
    public static Order create(CustomerId customerId) {
        Order order = new Order();
        order.id = OrderId.generate();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = Money.ZERO("KRW");
        order.items = new ArrayList<>();
        order.notes = "";
        return order;
    }
    
    /**
     * 주문 아이템 추가
     */
    public void addItem(ProductId productId, String productName, int quantity, Money unitPrice) {
        validateCanModify();
        validateMaxItems();
        validateDuplicateProduct(productId);
        
        OrderItem item = OrderItem.create(productId, productName, quantity, unitPrice);
        items.add(item);
        recalculateTotalAmount();
    }
    
    /**
     * 주문 아이템 제거
     */
    public void removeItem(ProductId productId) {
        validateCanModify();
        
        boolean removed = items.removeIf(item -> item.getProductId().equals(productId));
        if (!removed) {
            throw new IllegalArgumentException("해당 상품이 주문에 없습니다: " + productId);
        }
        recalculateTotalAmount();
    }
    
    /**
     * 주문 아이템 수량 변경
     */
    public void changeItemQuantity(ProductId productId, int newQuantity) {
        validateCanModify();
        
        OrderItem item = findItemByProductId(productId);
        item.changeQuantity(newQuantity);
        recalculateTotalAmount();
    }
    
    /**
     * 주문 확정
     */
    public void confirm() {
        validateCanConfirm();
        validateHasItems();
        
        this.status = OrderStatus.CONFIRMED;
        // TODO: 도메인 이벤트 발행 (OrderConfirmedEvent)
    }
    
    /**
     * 주문 취소
     */
    public void cancel(String reason) {
        validateCanCancel();
        
        this.status = OrderStatus.CANCELLED;
        this.notes = (notes != null ? notes + "\n" : "") + "취소 사유: " + reason;
        // TODO: 도메인 이벤트 발행 (OrderCancelledEvent)
    }
    
    /**
     * 결제 완료 처리
     */
    public void markAsPaid() {
        validateCanPay();
        
        this.status = OrderStatus.PAID;
        // TODO: 도메인 이벤트 발행 (OrderPaidEvent)
    }
    
    /**
     * 배송 시작 처리
     */
    public void markAsShipped() {
        validateCanShip();
        
        this.status = OrderStatus.SHIPPED;
        // TODO: 도메인 이벤트 발행 (OrderShippedEvent)
    }
    
    /**
     * 배송 완료 처리
     */
    public void markAsDelivered() {
        validateCanDeliver();
        
        this.status = OrderStatus.DELIVERED;
        // TODO: 도메인 이벤트 발행 (OrderDeliveredEvent)
    }
    
    /**
     * 주문 완료 처리
     */
    public void complete() {
        validateCanComplete();
        
        this.status = OrderStatus.COMPLETED;
        // TODO: 도메인 이벤트 발행 (OrderCompletedEvent)
    }
    
    /**
     * 노트 추가
     */
    public void addNotes(String additionalNotes) {
        if (additionalNotes != null && !additionalNotes.trim().isEmpty()) {
            this.notes = (notes != null ? notes + "\n" : "") + additionalNotes.trim();
        }
    }
    
    /**
     * 재고 예약 정보 추가
     */
    public void addReservationInfo(String reservationId, String productId) {
        if (reservationId != null && productId != null) {
            this.stockReservations.put(productId, reservationId);
        }
    }
    
    /**
     * 결제 대기 상태로 변경
     */
    public void markAsPaymentPending() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed orders can be marked as payment pending");
        }
        this.status = OrderStatus.PAYMENT_PENDING;
    }
    
    /**
     * 주문 실패 처리
     */
    public void markAsFailed(String reason) {
        this.status = OrderStatus.FAILED;
        addNotes("Order failed: " + reason);
    }
    
    /**
     * 총 금액 재계산
     */
    private void recalculateTotalAmount() {
        this.totalAmount = items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(Money.ZERO("KRW"), Money::add);
    }
    
    /**
     * 상품 ID로 주문 아이템 찾기
     */
    private OrderItem findItemByProductId(ProductId productId) {
        return items.stream()
            .filter(item -> item.getProductId().equals(productId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("해당 상품이 주문에 없습니다: " + productId));
    }
    
    // 검증 메서드들
    private void validateCanModify() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("대기 상태의 주문만 수정할 수 있습니다");
        }
    }
    
    private void validateCanConfirm() {
        if (!status.canTransitionTo(OrderStatus.CONFIRMED)) {
            throw new IllegalStateException("현재 상태에서는 주문을 확정할 수 없습니다: " + status);
        }
    }
    
    private void validateCanCancel() {
        if (!status.isCancellable()) {
            throw new IllegalStateException("현재 상태에서는 주문을 취소할 수 없습니다: " + status);
        }
    }
    
    private void validateCanPay() {
        if (!status.canTransitionTo(OrderStatus.PAID)) {
            throw new IllegalStateException("현재 상태에서는 결제할 수 없습니다: " + status);
        }
    }
    
    private void validateCanShip() {
        if (!status.canTransitionTo(OrderStatus.SHIPPED)) {
            throw new IllegalStateException("현재 상태에서는 배송을 시작할 수 없습니다: " + status);
        }
    }
    
    private void validateCanDeliver() {
        if (!status.canTransitionTo(OrderStatus.DELIVERED)) {
            throw new IllegalStateException("현재 상태에서는 배송 완료할 수 없습니다: " + status);
        }
    }
    
    private void validateCanComplete() {
        if (!status.canTransitionTo(OrderStatus.COMPLETED)) {
            throw new IllegalStateException("현재 상태에서는 주문을 완료할 수 없습니다: " + status);
        }
    }
    
    private void validateMaxItems() {
        if (items.size() >= MAX_ITEMS) {
            throw new IllegalArgumentException("주문 아이템은 " + MAX_ITEMS + "개를 초과할 수 없습니다");
        }
    }
    
    private void validateHasItems() {
        if (items.isEmpty()) {
            throw new IllegalStateException("주문 아이템이 없는 주문은 확정할 수 없습니다");
        }
    }
    
    private void validateDuplicateProduct(ProductId productId) {
        boolean exists = items.stream()
            .anyMatch(item -> item.getProductId().equals(productId));
        if (exists) {
            throw new IllegalArgumentException("이미 주문에 포함된 상품입니다: " + productId);
        }
    }
    
    // 조회 메서드들
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    public int getItemCount() {
        return items.size();
    }
    
    public int getTotalQuantity() {
        return items.stream()
            .mapToInt(OrderItem::getQuantity)
            .sum();
    }
    
    public boolean hasItem(ProductId productId) {
        return items.stream()
            .anyMatch(item -> item.getProductId().equals(productId));
    }
    
    public boolean isModifiable() {
        return status == OrderStatus.PENDING;
    }
    
    public boolean isPaid() {
        return status.isPaid();
    }
    
    public boolean isActive() {
        return status.isActive();
    }
    
    public boolean isFinalStatus() {
        return status.isFinalStatus();
    }
    
    // Getters
    public OrderId getId() {
        return id;
    }
    
    public CustomerId getCustomerId() {
        return customerId;
    }
    
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
    
    public Money getTotalAmount() {
        return totalAmount;
    }
    
    public OrderStatus getStatus() {
        return status;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public Long getVersion() {
        return version;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Order order = (Order) obj;
        return Objects.equals(id, order.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Order{id='%s', customerId='%s', status=%s, totalAmount=%s, itemCount=%d}", 
                id, customerId, status, totalAmount, items.size());
    }
}