package com.hightraffic.ecommerce.order.domain.model;

import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * 주문 아이템 Entity
 * Order Aggregate 내부의 Entity로, Order를 통해서만 접근 가능
 */
@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;  // Local Identity (Aggregate 내부에서만 사용)
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "value", column = @Column(name = "product_id"))
    })
    private ProductId productId;
    
    @Column(name = "product_name", nullable = false)
    private String productName;
    
    @Column(name = "quantity", nullable = false)
    private int quantity;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price")),
        @AttributeOverride(name = "currency.currencyCode", column = @Column(name = "unit_price_currency"))
    })
    private Money unitPrice;
    
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "total_price")),
        @AttributeOverride(name = "currency.currencyCode", column = @Column(name = "total_price_currency"))
    })
    private Money totalPrice;
    
    // JPA용 기본 생성자
    protected OrderItem() {}
    
    /**
     * OrderItem 생성 Factory Method
     */
    public static OrderItem create(ProductId productId, String productName, int quantity, Money unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(productName);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.calculateTotalPrice();
        return item;
    }
    
    /**
     * 수량 변경
     */
    public void changeQuantity(int newQuantity) {
        validateQuantity(newQuantity);
        this.quantity = newQuantity;
        calculateTotalPrice();
    }
    
    /**
     * 단가 변경
     */
    public void changeUnitPrice(Money newUnitPrice) {
        validateUnitPrice(newUnitPrice);
        this.unitPrice = newUnitPrice;
        calculateTotalPrice();
    }
    
    /**
     * 총 가격 계산
     */
    private void calculateTotalPrice() {
        this.totalPrice = unitPrice.multiply(quantity);
    }
    
    /**
     * 수량 유효성 검증
     */
    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 0보다 커야 합니다");
        }
        if (quantity > 1000) {
            throw new IllegalArgumentException("수량은 1000개를 초과할 수 없습니다");
        }
    }
    
    /**
     * 단가 유효성 검증
     */
    private void validateUnitPrice(Money unitPrice) {
        if (unitPrice == null) {
            throw new IllegalArgumentException("단가는 필수입니다");
        }
        if (unitPrice.isNegative()) {
            throw new IllegalArgumentException("단가는 음수일 수 없습니다");
        }
    }
    
    /**
     * 상품명 유효성 검증
     */
    private void validateProductName(String productName) {
        if (productName == null || productName.trim().isEmpty()) {
            throw new IllegalArgumentException("상품명은 필수입니다");
        }
        if (productName.length() > 255) {
            throw new IllegalArgumentException("상품명은 255자를 초과할 수 없습니다");
        }
    }
    
    // Getters
    public Long getId() {
        return id;
    }
    
    public ProductId getProductId() {
        return productId;
    }
    
    public String getProductName() {
        return productName;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public Money getUnitPrice() {
        return unitPrice;
    }
    
    public Money getTotalPrice() {
        return totalPrice;
    }
    
    // Setters (package-private for aggregate control)
    void setProductId(ProductId productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다");
        }
        this.productId = productId;
    }
    
    void setProductName(String productName) {
        validateProductName(productName);
        this.productName = productName;
    }
    
    void setQuantity(int quantity) {
        validateQuantity(quantity);
        this.quantity = quantity;
    }
    
    void setUnitPrice(Money unitPrice) {
        validateUnitPrice(unitPrice);
        this.unitPrice = unitPrice;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        OrderItem orderItem = (OrderItem) obj;
        return quantity == orderItem.quantity &&
               Objects.equals(productId, orderItem.productId) &&
               Objects.equals(productName, orderItem.productName) &&
               Objects.equals(unitPrice, orderItem.unitPrice) &&
               Objects.equals(totalPrice, orderItem.totalPrice);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(productId, productName, quantity, unitPrice, totalPrice);
    }
    
    @Override
    public String toString() {
        return String.format("OrderItem{productId='%s', productName='%s', quantity=%d, unitPrice=%s, totalPrice=%s}", 
                productId, productName, quantity, unitPrice, totalPrice);
    }
}