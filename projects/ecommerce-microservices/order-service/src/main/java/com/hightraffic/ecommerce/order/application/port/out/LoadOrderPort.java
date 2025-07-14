package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;

import java.util.Optional;

/**
 * 주문 조회 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 주문 조회 인터페이스
 * Repository 패턴의 일부로 조회 전용 기능 제공
 */
public interface LoadOrderPort {
    
    /**
     * 주문 ID로 주문 조회
     * 
     * @param orderId 주문 ID
     * @return 주문 정보 (없으면 Optional.empty())
     */
    Optional<Order> loadOrder(OrderId orderId);
    
    /**
     * 주문 존재 여부 확인
     * 
     * @param orderId 주문 ID
     * @return 존재하면 true
     */
    boolean existsOrder(OrderId orderId);
}