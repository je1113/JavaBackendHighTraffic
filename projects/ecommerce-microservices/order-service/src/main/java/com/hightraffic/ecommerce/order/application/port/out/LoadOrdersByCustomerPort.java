package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * 고객별 주문 조회 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 고객별 주문 조회 인터페이스
 * 페이징과 필터링을 지원하는 조회 기능 제공
 */
public interface LoadOrdersByCustomerPort {
    
    /**
     * 고객 ID로 주문 목록 조회
     * 
     * @param customerId 고객 ID
     * @param statusFilter 주문 상태 필터 (null이면 전체)
     * @param fromDate 시작 날짜 (null이면 제한 없음)
     * @param toDate 종료 날짜 (null이면 제한 없음)
     * @param pageable 페이징 정보
     * @return 페이징된 주문 목록
     */
    Page<Order> loadOrdersByCustomer(
        CustomerId customerId,
        OrderStatus statusFilter,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        Pageable pageable
    );
}