package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.common.event.payment.PaymentCompletedEvent;

/**
 * 결제 완료 이벤트 처리 Use Case
 * 
 * 결제 서비스에서 발생한 결제 완료 이벤트를 처리합니다.
 */
public interface HandlePaymentCompletedEventUseCase {
    
    /**
     * 결제 완료 이벤트 처리
     * 
     * @param event 결제 완료 이벤트
     */
    void handle(PaymentCompletedEvent event);
}