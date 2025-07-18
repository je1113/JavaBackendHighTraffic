package com.hightraffic.ecommerce.inventory.application.port.in;

/**
 * 주문 취소 이벤트 처리 유스케이스
 */
public interface HandleOrderCancelledEventUseCase {
    
    void handle(OrderCancelledCommand command);
    
    record OrderCancelledCommand(
            String orderId
    ) {}
}