package com.hightraffic.ecommerce.order.application.port.out;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 주문 영속성 통합 Outbound Port
 * 
 * 영속성 어댑터에서 구현해야 하는 주문 관련 모든 데이터 접근 인터페이스
 * LoadOrderPort, SaveOrderPort, LoadOrdersByCustomerPort를 통합
 */
public interface OrderPersistencePort extends LoadOrderPort, SaveOrderPort, LoadOrdersByCustomerPort {
    
    /**
     * 특정 기간 내 고객의 특정 상품 주문 조회
     * 중복 주문 방지를 위해 사용
     * 
     * @param customerId 고객 ID
     * @param productIds 상품 ID 목록
     * @param fromDate 시작 시간
     * @return 해당 조건의 주문 목록
     */
    List<Order> findRecentOrdersByCustomerAndProducts(
        CustomerId customerId,
        List<ProductId> productIds,
        LocalDateTime fromDate
    );
    
    /**
     * 주문 상태별 개수 조회
     * 모니터링 및 통계를 위해 사용
     * 
     * @param customerId 고객 ID (null이면 전체)
     * @param status 주문 상태
     * @return 해당 상태의 주문 개수
     */
    long countOrdersByStatus(CustomerId customerId, OrderStatus status);
    
    /**
     * 주문 삭제 (하드 삭제는 권장하지 않음)
     * 테스트 또는 특수한 경우에만 사용
     * 
     * @param orderId 삭제할 주문 ID
     */
    void deleteOrder(OrderId orderId);
    
    /**
     * 배치 주문 저장
     * 대량 주문 처리 시 성능 최적화
     * 
     * @param orders 저장할 주문 목록
     * @return 저장된 주문 목록
     */
    List<Order> saveAllOrders(List<Order> orders);
}