package com.hightraffic.ecommerce.common.event.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.common.event.base.DomainEvent;

/**
 * 재고 차감 완료 이벤트
 */
public class StockDeductedEvent extends DomainEvent {
    
    @JsonProperty("orderId")
    private final String orderId;
    
    @JsonProperty("productId")
    private final String productId;
    
    @JsonProperty("deductedQuantity")
    private final int deductedQuantity;
    
    @JsonProperty("remainingQuantity")
    private final int remainingQuantity;
    
    public StockDeductedEvent() {
        super();
        this.orderId = null;
        this.productId = null;
        this.deductedQuantity = 0;
        this.remainingQuantity = 0;
    }
    
    public StockDeductedEvent(String orderId, String productId, int deductedQuantity, int remainingQuantity) {
        super(productId);
        this.orderId = orderId;
        this.productId = productId;
        this.deductedQuantity = deductedQuantity;
        this.remainingQuantity = remainingQuantity;
    }
    
    public String getOrderId() {
        return orderId;
    }
    
    public String getProductId() {
        return productId;
    }
    
    public int getDeductedQuantity() {
        return deductedQuantity;
    }
    
    public int getRemainingQuantity() {
        return remainingQuantity;
    }
}