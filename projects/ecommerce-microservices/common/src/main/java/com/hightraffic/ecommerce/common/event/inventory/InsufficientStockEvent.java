package com.hightraffic.ecommerce.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

/**
 * 재고 부족 이벤트
 */
public class InsufficientStockEvent extends DomainEvent {
    
    @JsonProperty("orderId")
    private final String orderId;
    
    @JsonProperty("productId")
    private final String productId;
    
    @JsonProperty("requestedQuantity")
    private final int requestedQuantity;
    
    @JsonProperty("availableQuantity")
    private final int availableQuantity;
    
    @JsonProperty("reason")
    private final String reason;
    
    public InsufficientStockEvent() {
        super();
        this.orderId = null;
        this.productId = null;
        this.requestedQuantity = 0;
        this.availableQuantity = 0;
        this.reason = null;
    }
    
    public InsufficientStockEvent(String orderId, String productId, int requestedQuantity, 
                                 int availableQuantity, String reason) {
        super(productId);
        this.orderId = orderId;
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
        this.reason = reason;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public int getRequestedQuantity() {
        return requestedQuantity;
    }
    
    public int getAvailableQuantity() {
        return availableQuantity;
    }
    
    public String getReason() {
        return reason;
    }
}