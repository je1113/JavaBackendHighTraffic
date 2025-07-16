package com.hightraffic.ecommerce.inventory.domain.exception;

import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;

/**
 * 재고 부족 예외
 */
public class InsufficientStockException extends InventoryDomainException {
    
    private final ProductId productId;
    private final StockQuantity requestedQuantity;
    private final StockQuantity availableQuantity;
    
    public InsufficientStockException(String message) {
        super(message);
        this.productId = null;
        this.requestedQuantity = null;
        this.availableQuantity = null;
    }
    
    public InsufficientStockException(String message, Throwable cause) {
        super(message, cause);
        this.productId = null;
        this.requestedQuantity = null;
        this.availableQuantity = null;
    }
    
    public InsufficientStockException(ProductId productId, StockQuantity requestedQuantity, 
                                     StockQuantity availableQuantity) {
        super(String.format("Insufficient stock for product %s. Requested: %d, Available: %d",
                productId, requestedQuantity.getValue(), availableQuantity.getValue()));
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
    
    public InsufficientStockException(String message, ProductId productId, 
                                     StockQuantity requestedQuantity, StockQuantity availableQuantity) {
        super(message);
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
    
    public ProductId getProductId() {
        return productId;
    }
    
    public StockQuantity getRequestedQuantity() {
        return requestedQuantity;
    }
    
    public StockQuantity getAvailableQuantity() {
        return availableQuantity;
    }
}