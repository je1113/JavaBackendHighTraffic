package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.order.application.port.in.GetOrderUseCase;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrdersByCustomerPort;
import com.hightraffic.ecommerce.order.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 조회 Use Case 구현체
 * 
 * 책임:
 * - 단일 주문 조회
 * - 고객별 주문 목록 조회
 * - 조회 결과 DTO 변환
 * 
 * 읽기 전용 트랜잭션으로 성능 최적화
 */
@Service
@Transactional(readOnly = true)
public class GetOrderService implements GetOrderUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(GetOrderService.class);
    
    private final LoadOrderPort loadOrderPort;
    private final LoadOrdersByCustomerPort loadOrdersByCustomerPort;
    
    public GetOrderService(LoadOrderPort loadOrderPort,
                         LoadOrdersByCustomerPort loadOrdersByCustomerPort) {
        this.loadOrderPort = loadOrderPort;
        this.loadOrdersByCustomerPort = loadOrdersByCustomerPort;
    }
    
    @Override
    public OrderResponse getOrder(GetOrderQuery query) {
        log.debug("Getting order: {}", query.getOrderId());
        
        Order order = loadOrderPort.loadOrder(query.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(query.getOrderId()));
        
        return new OrderResponse(order);
    }
    
    @Override
    public OrderListResponse getOrdersByCustomer(GetOrdersByCustomerQuery query) {
        log.debug("Getting orders for customer: {} with filter: {}", 
            query.getCustomerId(), query.getStatusFilter());
        
        // 페이징 정보 생성 (최신 주문부터 조회)
        Pageable pageable = PageRequest.of(
            query.getPage(),
            query.getSize(),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        
        // 주문 조회
        Page<Order> orderPage = loadOrdersByCustomerPort.loadOrdersByCustomer(
            query.getCustomerId(),
            query.getStatusFilter(),
            query.getFromDate(),
            query.getToDate(),
            pageable
        );
        
        // 응답 생성
        return new OrderListResponse(
            orderPage.getContent(),
            (int) orderPage.getTotalElements(),
            orderPage.getTotalPages(),
            orderPage.getNumber()
        );
    }
}