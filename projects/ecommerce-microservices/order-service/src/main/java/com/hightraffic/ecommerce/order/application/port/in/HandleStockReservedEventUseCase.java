package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;

/**
 * 재고 예약 이벤트 처리 Use Case
 * 
 * 재고 서비스에서 발생한 재고 예약 완료 이벤트를 처리합니다.
 */
public interface HandleStockReservedEventUseCase {
    
    /**
     * 재고 예약 완료 이벤트 처리
     * 
     * @param event 재고 예약 완료 이벤트
     */
    void handle(StockReservedEvent event);
}