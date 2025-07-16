package com.hightraffic.ecommerce.order.adapter.out.persistence;

import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrdersByCustomerPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.OrderItem;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 주문 영속성 어댑터
 * 
 * 도메인 모델과 JPA 엔티티 간의 변환을 담당하며,
 * 영속성 관련 포트 인터페이스를 구현합니다.
 */
@Component
@Transactional(readOnly = true)
public class OrderPersistenceAdapter implements LoadOrderPort, LoadOrdersByCustomerPort, SaveOrderPort {
    
    private static final Logger log = LoggerFactory.getLogger(OrderPersistenceAdapter.class);
    
    private final OrderJpaRepository orderRepository;
    private final OrderMapper mapper;
    
    public OrderPersistenceAdapter(OrderJpaRepository orderRepository) {
        this.orderRepository = orderRepository;
        this.mapper = new OrderMapper();
    }
    
    @Override
    public Optional<Order> loadOrder(OrderId orderId) {
        log.debug("주문 조회 시작: orderId={}", orderId.getValue());
        
        return orderRepository.findByIdWithItems(orderId.getValue())
            .map(mapper::toDomainModel);
    }
    
    @Override
    public List<Order> loadOrdersByCustomer(CustomerId customerId, int limit) {
        log.debug("고객 주문 목록 조회: customerId={}, limit={}", customerId.getValue(), limit);
        
        List<OrderJpaEntity> entities = orderRepository.findRecentOrdersByCustomerId(
            customerId.getValue(),
            PageRequest.of(0, limit)
        );
        
        return entities.stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void save(Order order) {
        log.debug("주문 저장 시작: orderId={}", order.getId().getValue());
        
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        orderRepository.save(entity);
        
        log.info("주문 저장 완료: orderId={}, status={}", 
            order.getId().getValue(), order.getStatus());
    }
    
    /**
     * 주문 도메인 모델과 JPA 엔티티 간 변환을 담당하는 매퍼
     */
    private static class OrderMapper {
        
        /**
         * JPA 엔티티를 도메인 모델로 변환
         */
        public Order toDomainModel(OrderJpaEntity entity) {
            // 주문 ID
            OrderId orderId = new OrderId(entity.getId());
            
            // 고객 ID
            CustomerId customerId = new CustomerId(entity.getCustomerId());
            
            // 주문 상태
            OrderStatus status = mapToDomainStatus(entity.getStatus());
            
            // 금액
            Money totalAmount = Money.of(entity.getTotalAmount(), entity.getCurrency());
            
            // 도메인 모델 생성 (리플렉션 또는 팩토리 메서드 사용)
            Order order = createOrder(orderId, customerId, status, totalAmount);
            
            // 주문 아이템 추가
            for (OrderItemJpaEntity itemEntity : entity.getItems()) {
                OrderItem item = toDomainOrderItem(itemEntity);
                order.addItem(item);
            }
            
            // 추가 정보 설정
            if (entity.getPaymentId() != null) {
                order.setPaymentId(entity.getPaymentId());
            }
            
            // 타임스탬프 설정 (필요한 경우)
            setTimestamps(order, entity);
            
            return order;
        }
        
        /**
         * 도메인 모델을 JPA 엔티티로 변환
         */
        public OrderJpaEntity toJpaEntity(Order order) {
            // 기존 엔티티 조회 또는 새로 생성
            OrderJpaEntity entity = new OrderJpaEntity.Builder()
                .id(order.getId().getValue())
                .customerId(order.getCustomerId().getValue())
                .status(mapToEntityStatus(order.getStatus()))
                .totalAmount(order.getTotalAmount().getAmount())
                .currency(order.getTotalAmount().getCurrency())
                .paymentId(order.getPaymentId())
                .build();
            
            // 주문 아이템 변환 및 추가
            order.getItems().forEach(item -> {
                OrderItemJpaEntity itemEntity = toJpaOrderItem(item);
                entity.addItem(itemEntity);
            });
            
            // 상태별 타임스탬프 설정
            updateStatusTimestamps(entity, order.getStatus());
            
            return entity;
        }
        
        /**
         * 주문 아이템 도메인 모델 변환
         */
        private OrderItem toDomainOrderItem(OrderItemJpaEntity entity) {
            ProductId productId = new ProductId(entity.getProductId());
            Money unitPrice = Money.of(entity.getUnitPrice(), entity.getCurrency());
            
            OrderItem item = new OrderItem(
                productId,
                entity.getProductName(),
                entity.getQuantity(),
                unitPrice
            );
            
            if (entity.getReservationId() != null) {
                item.setReservationId(entity.getReservationId());
            }
            
            return item;
        }
        
        /**
         * 주문 아이템 JPA 엔티티 변환
         */
        private OrderItemJpaEntity toJpaOrderItem(OrderItem item) {
            return OrderItemJpaEntity.createNew(
                item.getProductId().getValue(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice().getAmount(),
                item.getUnitPrice().getCurrency()
            );
        }
        
        /**
         * 도메인 상태를 엔티티 상태로 변환
         */
        private OrderJpaEntity.OrderStatusEntity mapToEntityStatus(OrderStatus domainStatus) {
            return switch (domainStatus) {
                case PENDING -> OrderJpaEntity.OrderStatusEntity.PENDING;
                case CONFIRMED -> OrderJpaEntity.OrderStatusEntity.CONFIRMED;
                case PAID -> OrderJpaEntity.OrderStatusEntity.PAID;
                case COMPLETED -> OrderJpaEntity.OrderStatusEntity.COMPLETED;
                case CANCELLED -> OrderJpaEntity.OrderStatusEntity.CANCELLED;
            };
        }
        
        /**
         * 엔티티 상태를 도메인 상태로 변환
         */
        private OrderStatus mapToDomainStatus(OrderJpaEntity.OrderStatusEntity entityStatus) {
            return switch (entityStatus) {
                case PENDING -> OrderStatus.PENDING;
                case CONFIRMED -> OrderStatus.CONFIRMED;
                case PAID -> OrderStatus.PAID;
                case COMPLETED -> OrderStatus.COMPLETED;
                case CANCELLED -> OrderStatus.CANCELLED;
            };
        }
        
        /**
         * 도메인 객체 생성 (팩토리 메서드 또는 리플렉션 활용)
         */
        private Order createOrder(OrderId orderId, CustomerId customerId, 
                                OrderStatus status, Money totalAmount) {
            // Order 클래스의 생성자나 팩토리 메서드에 따라 구현
            // 여기서는 예시로 작성
            Order order = new Order(orderId, customerId);
            
            // 상태와 금액은 setter나 다른 방법으로 설정
            // (실제 도메인 모델 구조에 맞게 조정 필요)
            
            return order;
        }
        
        /**
         * 타임스탬프 설정
         */
        private void setTimestamps(Order order, OrderJpaEntity entity) {
            // 도메인 모델에 타임스탬프 필드가 있다면 설정
            // 예: order.setCreatedAt(entity.getCreatedAt());
        }
        
        /**
         * 상태별 타임스탬프 업데이트
         */
        private void updateStatusTimestamps(OrderJpaEntity entity, OrderStatus status) {
            Instant now = Instant.now();
            
            switch (status) {
                case CONFIRMED:
                    if (entity.getConfirmedAt() == null) {
                        entity.confirm();
                    }
                    break;
                case PAID:
                    if (entity.getPaidAt() == null && entity.getPaymentId() != null) {
                        entity.markAsPaid(entity.getPaymentId());
                    }
                    break;
                case COMPLETED:
                    if (entity.getCompletedAt() == null) {
                        entity.complete();
                    }
                    break;
                case CANCELLED:
                    if (entity.getCancelledAt() == null) {
                        entity.cancel(entity.getCancelledReason() != null ? 
                            entity.getCancelledReason() : "주문 취소");
                    }
                    break;
            }
        }
    }
    
    /**
     * 특정 기간 내 고객의 주문 수 조회
     */
    public long countCustomerOrdersInPeriod(CustomerId customerId, Instant startDate, Instant endDate) {
        return orderRepository.countByCustomerIdAndCreatedAtBetween(
            customerId.getValue(), 
            startDate, 
            endDate
        );
    }
    
    /**
     * 만료된 대기 상태 주문 조회
     */
    public List<Order> findExpiredPendingOrders(Instant expirationTime) {
        List<OrderJpaEntity> entities = orderRepository.findExpiredPendingOrders(expirationTime);
        
        return entities.stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    /**
     * 주문 존재 여부 확인
     */
    public boolean exists(OrderId orderId) {
        return orderRepository.existsByIdOptimized(orderId.getValue());
    }
}