package com.hightraffic.ecommerce.inventory.domain.exception;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;

/**
 * 상품을 찾을 수 없는 예외
 */
public class ProductNotFoundException extends InventoryDomainException {
    
    private final ProductId productId;
    
    public ProductNotFoundException(String message) {
        super(message);
        this.productId = null;
    }
    
    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.productId = null;
    }
    
    public ProductNotFoundException(ProductId productId) {
        super("Product not found: " + productId);
        this.productId = productId;
    }
    
    public ProductNotFoundException(String message, ProductId productId) {
        super(message);
        this.productId = productId;
    }
    
    public ProductId getProductId() {
        return productId;
    }
}