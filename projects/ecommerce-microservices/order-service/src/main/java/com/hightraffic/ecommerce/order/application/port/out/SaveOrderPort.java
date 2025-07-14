package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.Order;

/**
 * 주문 저장 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 주문 저장 인터페이스
 * Repository 패턴의 일부로 저장/수정 기능 제공
 */
public interface SaveOrderPort {
    
    /**
     * 주문 저장 또는 수정
     * 
     * @param order 저장할 주문
     * @return 저장된 주문
     */
    Order saveOrder(Order order);
}