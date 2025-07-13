package com.hightraffic.ecommerce.order.domain.repository;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Order Aggregate Repository Interface
 * 주문 도메인의 영속성 추상화
 */
public interface OrderRepository {
    
    /**
     * 주문 저장
     */
    Order save(Order order);
    
    /**
     * 주문 ID로 조회
     */
    Optional<Order> findById(OrderId orderId);
    
    /**
     * 주문 ID로 조회 (존재하지 않으면 예외)
     */
    Order getById(OrderId orderId);
    
    /**
     * 고객별 주문 목록 조회
     */
    List<Order> findByCustomerId(CustomerId customerId);
    
    /**
     * 고객별 활성 주문 목록 조회 (취소되지 않은 주문)
     */
    List<Order> findActiveOrdersByCustomerId(CustomerId customerId);
    
    /**
     * 상태별 주문 목록 조회
     */
    List<Order> findByStatus(OrderStatus status);
    
    /**
     * 상태별 주문 목록 조회 (페이징)
     */
    List<Order> findByStatus(OrderStatus status, int page, int size);
    
    /**
     * 특정 기간 내 생성된 주문 목록 조회
     */
    List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    /**
     * 고객의 특정 상태 주문 개수 조회
     */
    long countByCustomerIdAndStatus(CustomerId customerId, OrderStatus status);
    
    /**
     * 고객의 총 주문 개수 조회
     */
    long countByCustomerId(CustomerId customerId);
    
    /**
     * 특정 상태의 주문 개수 조회
     */
    long countByStatus(OrderStatus status);
    
    /**
     * 주문 삭제 (물리적 삭제)
     */
    void delete(Order order);
    
    /**
     * 주문 ID로 삭제
     */
    void deleteById(OrderId orderId);
    
    /**
     * 주문 존재 여부 확인
     */
    boolean existsById(OrderId orderId);
    
    /**
     * 모든 주문 조회 (테스트용)
     */
    List<Order> findAll();
    
    /**
     * 전체 주문 개수 조회
     */
    long count();
}