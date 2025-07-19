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
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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
public class OrderPersistenceAdapter implements LoadOrderPort, LoadOrdersByCustomerPort, SaveOrderPort, OrderRepository {
    
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
    public Page<Order> loadOrdersByCustomer(CustomerId customerId, OrderStatus statusFilter, 
            LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable) {
        log.debug("고객 주문 목록 조회 (페이징): customerId={}, status={}, from={}, to={}", 
                customerId.getValue(), statusFilter, fromDate, toDate);
        
        // 상태 필터가 있는 경우와 없는 경우 분리
        Page<OrderJpaEntity> entities;
        if (statusFilter != null) {
            entities = orderRepository.findByCustomerIdAndStatusAndCreatedAtBetween(
                customerId.getValue(),
                statusFilter.name(),
                fromDate,
                toDate,
                pageable
            );
        } else {
            entities = orderRepository.findByCustomerIdAndCreatedAtBetween(
                customerId.getValue(),
                fromDate,
                toDate,
                pageable
            );
        }
        
        return entities.map(mapper::toDomainModel);
    }
    
    @Override
    public boolean existsOrder(OrderId orderId) {
        return orderRepository.existsById(orderId.getValue());
    }
    
    @Override
    @Transactional
    public Order saveOrder(Order order) {
        log.debug("주문 저장 시작: orderId={}", order.getOrderId().getValue());
        
        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity savedEntity = orderRepository.save(entity);
        Order savedOrder = mapper.toDomainModel(savedEntity);
        
        log.info("주문 저장 완료: orderId={}, status={}", 
            savedOrder.getOrderId().getValue(), savedOrder.getStatus());
        
        return savedOrder;
    }
    
    // OrderRepository 인터페이스 구현
    
    @Override
    @Transactional
    public Order save(Order order) {
        return saveOrder(order);
    }
    
    @Override
    public Optional<Order> findById(OrderId orderId) {
        return loadOrder(orderId);
    }
    
    @Override
    public Order getById(OrderId orderId) {
        return findById(orderId)
            .orElseThrow(() -> new com.hightraffic.ecommerce.order.domain.exception.OrderNotFoundException(
                "주문을 찾을 수 없습니다: " + orderId.getValue()));
    }
    
    @Override
    public List<Order> findByCustomerId(CustomerId customerId) {
        return loadOrdersByCustomer(customerId, 100);
    }
    
    @Override
    public List<Order> findActiveOrdersByCustomerId(CustomerId customerId) {
        List<OrderJpaEntity> entities = orderRepository.findByCustomerIdWithItems(customerId.getValue());
        return entities.stream()
            .map(mapper::toDomainModel)
            .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByStatus(OrderStatus status) {
        OrderJpaEntity.OrderStatusEntity entityStatus = mapper.mapToEntityStatus(status);
        if (entityStatus == null) {
            return List.of();
        }
        List<OrderJpaEntity> entities = orderRepository.findByStatusOrderByCreatedAtDesc(entityStatus);
        return entities.stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByStatus(OrderStatus status, int page, int size) {
        OrderJpaEntity.OrderStatusEntity entityStatus = mapper.mapToEntityStatus(status);
        if (entityStatus == null) {
            return List.of();
        }
        Page<OrderJpaEntity> entities = orderRepository.findAll(PageRequest.of(page, size));
        return entities.stream()
            .filter(e -> e.getStatus() == entityStatus)
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        List<OrderJpaEntity> entities = orderRepository.findOrdersCreatedBetween(
            start.toInstant(java.time.ZoneOffset.UTC),
            end.toInstant(java.time.ZoneOffset.UTC)
        );
        return entities.stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public long countByCustomerIdAndStatus(CustomerId customerId, OrderStatus status) {
        OrderJpaEntity.OrderStatusEntity entityStatus = mapper.mapToEntityStatus(status);
        if (entityStatus == null) {
            return 0;
        }
        return orderRepository.findByCustomerIdAndStatus(customerId.getValue(), entityStatus).size();
    }
    
    @Override
    public long countByCustomerId(CustomerId customerId) {
        return orderRepository.findByCustomerId(customerId.getValue(), PageRequest.of(0, 1))
            .getTotalElements();
    }
    
    @Override
    public long countByStatus(OrderStatus status) {
        OrderJpaEntity.OrderStatusEntity entityStatus = mapper.mapToEntityStatus(status);
        if (entityStatus == null) {
            return 0;
        }
        return orderRepository.findByStatusOrderByCreatedAtDesc(entityStatus).size();
    }
    
    @Override
    @Transactional
    public void delete(Order order) {
        orderRepository.deleteById(order.getOrderId().getValue());
    }
    
    @Override
    @Transactional
    public void deleteById(OrderId orderId) {
        orderRepository.deleteById(orderId.getValue());
    }
    
    @Override
    public boolean existsById(OrderId orderId) {
        return orderRepository.existsById(orderId.getValue());
    }
    
    @Override
    public List<Order> findAll() {
        return orderRepository.findAll().stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public long count() {
        return orderRepository.count();
    }
    
    @Override
    public int countByCustomerIdAndCreatedAtAfter(CustomerId customerId, LocalDateTime dateTime) {
        Instant instant = dateTime.toInstant(java.time.ZoneOffset.UTC);
        Instant endInstant = Instant.now();
        return (int) orderRepository.countByCustomerIdAndCreatedAtBetween(
            customerId.getValue(), instant, endInstant);
    }
    
    @Override
    public int countByCustomerIdAndStatusIn(CustomerId customerId, List<OrderStatus> statuses) {
        int count = 0;
        for (OrderStatus status : statuses) {
            count += countByCustomerIdAndStatus(customerId, status);
        }
        return count;
    }
    
    @Override
    public List<Order> findByCustomerIdAndStatus(CustomerId customerId, OrderStatus status) {
        OrderJpaEntity.OrderStatusEntity entityStatus = mapper.mapToEntityStatus(status);
        if (entityStatus == null) {
            return List.of();
        }
        List<OrderJpaEntity> entities = orderRepository.findByCustomerIdAndStatus(
            customerId.getValue(), entityStatus);
        return entities.stream()
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByCustomerIdAndCreatedAtAfter(CustomerId customerId, LocalDateTime dateTime) {
        List<OrderJpaEntity> entities = orderRepository.findRecentOrdersByCustomerId(
            customerId.getValue(), PageRequest.of(0, 100));
        Instant instant = dateTime.toInstant(java.time.ZoneOffset.UTC);
        return entities.stream()
            .filter(e -> e.getCreatedAt().isAfter(instant))
            .map(mapper::toDomainModel)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Order> findByCustomerIdAndCreatedAtAfterOrderByCreatedAtDesc(
            CustomerId customerId, LocalDateTime dateTime) {
        return findByCustomerIdAndCreatedAtAfter(customerId, dateTime);
    }
    
    @Override
    public long countUnpaidOrdersByCustomerId(CustomerId customerId) {
        return countByCustomerIdAndStatus(customerId, OrderStatus.PENDING) +
               countByCustomerIdAndStatus(customerId, OrderStatus.CONFIRMED) +
               countByCustomerIdAndStatus(customerId, OrderStatus.PAYMENT_PENDING);
    }
    
    @Override
    public long countCompletedOrdersByCustomerIdAfter(CustomerId customerId, LocalDateTime dateTime) {
        List<Order> completedOrders = findByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED);
        Instant instant = dateTime.toInstant(java.time.ZoneOffset.UTC);
        return completedOrders.stream()
            .filter(order -> {
                // Order에 getCreatedAt() 메서드가 있다고 가정
                // 실제로는 도메인 모델에 맞게 수정 필요
                return true; // TODO: 날짜 필터링 구현
            })
            .count();
    }
    
    @Override
    public long countCompletedOrdersByCustomerId(CustomerId customerId) {
        return countByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED);
    }
    
    @Override
    public Money calculateTotalPurchaseAmount(CustomerId customerId) {
        List<Order> completedOrders = findByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED);
        BigDecimal total = completedOrders.stream()
            .map(order -> order.getTotalAmount().getAmount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(total, "KRW");
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
            OrderId orderId = OrderId.of(entity.getId());
            
            // 고객 ID
            CustomerId customerId = CustomerId.of(entity.getCustomerId());
            
            // 주문 상태
            OrderStatus status = mapToDomainStatus(entity.getStatus());
            
            // 금액
            Money totalAmount = Money.of(entity.getTotalAmount(), entity.getCurrency());
            
            // 도메인 모델 생성 (리플렉션 또는 팩토리 메서드 사용)
            Order order = createOrder(orderId, customerId, status, totalAmount);
            
            // 주문 아이템 추가
            for (OrderItemJpaEntity itemEntity : entity.getItems()) {
                OrderItem item = toDomainOrderItem(itemEntity);
                order.addItem(item.getProductId(), item.getProductName(), 
                            item.getQuantity(), item.getUnitPrice());
            }
            
            // 추가 정보 설정 (필요 시 도메인 모델에 메서드 추가)
            // TODO: setPaymentId 메서드가 필요한 경우 Order 도메인 모델에 추가
            
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
                .currency(order.getTotalAmount().getCurrency().getCurrencyCode())
                // .paymentId(order.getPaymentId()) // TODO: Order에 getPaymentId() 메서드 추가 필요
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
            ProductId productId = ProductId.of(entity.getProductId());
            Money unitPrice = Money.of(entity.getUnitPrice(), entity.getCurrency());
            
            OrderItem item = OrderItem.create(
                productId,
                entity.getProductName(),
                entity.getQuantity(),
                unitPrice
            );
            
            // TODO: setReservationId 메서드가 필요한 경우 OrderItem에 추가
            // if (entity.getReservationId() != null) {
            //     item.setReservationId(entity.getReservationId());
            // }
            
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
                item.getUnitPrice().getCurrency().getCurrencyCode()
            );
        }
        
        /**
         * 도메인 상태를 엔티티 상태로 변환
         */
        public OrderJpaEntity.OrderStatusEntity mapToEntityStatus(OrderStatus domainStatus) {
            return switch (domainStatus) {
                //TODO: STATUS 등록 후 연결
                case PENDING -> OrderJpaEntity.OrderStatusEntity.PENDING;
                case CONFIRMED -> OrderJpaEntity.OrderStatusEntity.CONFIRMED;
                case PAYMENT_PENDING -> null;
                case PAYMENT_PROCESSING -> null;
                case PAID -> OrderJpaEntity.OrderStatusEntity.PAID;
                case PREPARING -> null;
                case SHIPPED -> null;
                case DELIVERED -> null;
                case COMPLETED -> OrderJpaEntity.OrderStatusEntity.COMPLETED;
                case CANCELLED -> OrderJpaEntity.OrderStatusEntity.CANCELLED;
                case REFUNDING -> null;
                case REFUNDED -> null;
                case FAILED -> null;
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
            // Order 클래스의 팩토리 메서드 사용
            Order order = Order.create(customerId);
            
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