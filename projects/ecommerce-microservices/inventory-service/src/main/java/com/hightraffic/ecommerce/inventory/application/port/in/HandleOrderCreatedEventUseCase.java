package com.hightraffic.ecommerce.inventory.application.port.in;

/**
 * 주문 생성 이벤트 처리 유스케이스
 */
public interface HandleOrderCreatedEventUseCase {
    
    void handle(OrderCreatedCommand command);
    
    record OrderCreatedCommand(
            String orderId,
            java.util.List<OrderItem> items
    ) {}
    
    record OrderItem(
            String productId,
            int quantity
    ) {}
}