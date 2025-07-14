package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.order.application.port.in.CreateOrderUseCase;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.config.OrderBusinessRulesConfig;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;
import com.hightraffic.ecommerce.order.domain.service.OrderValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 주문 생성 Use Case 구현체
 * 
 * 책임:
 * - 주문 생성 요청 처리
 * - 비즈니스 규칙 검증
 * - 도메인 이벤트 발행
 * - 트랜잭션 관리
 */
@Service
@Transactional
public class CreateOrderService implements CreateOrderUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(CreateOrderService.class);
    
    private final SaveOrderPort saveOrderPort;
    private final PublishEventPort publishEventPort;
    private final OrderRepository orderRepository;
    private final OrderValidationService validationService;
    private final OrderBusinessRulesConfig businessRulesConfig;
    
    public CreateOrderService(SaveOrderPort saveOrderPort,
                            PublishEventPort publishEventPort,
                            OrderRepository orderRepository,
                            OrderValidationService validationService,
                            OrderBusinessRulesConfig businessRulesConfig) {
        this.saveOrderPort = saveOrderPort;
        this.publishEventPort = publishEventPort;
        this.orderRepository = orderRepository;
        this.validationService = validationService;
        this.businessRulesConfig = businessRulesConfig;
    }
    
    @Override
    public OrderId createOrder(CreateOrderCommand command) {
        log.info("Creating order for customer: {}", command.getCustomerId());
        
        // 1. 중복 주문 검증
        validateDuplicateOrder(command);
        
        // 2. 주문 생성
        Order order = Order.create(command.getCustomerId());
        
        // 3. 주문 아이템 추가
        command.getOrderItems().forEach(item -> 
            order.addItem(
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice()
            )
        );
        
        // 4. 주문 유효성 검증
        validationService.validateOrderItems(order);
        
        // 5. 주문 저장
        Order savedOrder = saveOrderPort.saveOrder(order);
        
        // 6. 도메인 이벤트 생성 및 발행
        OrderCreatedEvent event = createOrderCreatedEvent(savedOrder);
        publishEventPort.publishEvent(event);
        
        log.info("Order created successfully: {}", savedOrder.getOrderId());
        
        return savedOrder.getOrderId();
    }
    
    /**
     * 중복 주문 검증
     * 
     * 설정된 시간 내에 동일한 고객이 동일한 상품을 주문하는지 확인
     */
    private void validateDuplicateOrder(CreateOrderCommand command) {
        LocalDateTime duplicatePreventionTime = LocalDateTime.now()
            .minusMinutes(businessRulesConfig.getTime().getDuplicateOrderPreventionMinutes());
        
        boolean isDuplicate = validationService.isDuplicateOrder(
            command.getCustomerId(),
            command.getOrderItems().stream()
                .map(item -> item.getProductId())
                .collect(Collectors.toList()),
            duplicatePreventionTime
        );
        
        if (isDuplicate) {
            throw new DuplicateOrderException(
                "Duplicate order detected within " + 
                businessRulesConfig.getTime().getDuplicateOrderPreventionMinutes() + 
                " minutes"
            );
        }
    }
    
    /**
     * OrderCreatedEvent 생성
     */
    private OrderCreatedEvent createOrderCreatedEvent(Order order) {
        return new OrderCreatedEvent(
            order.getOrderId().getValue().toString(),
            order.getCustomerId().getValue().toString(),
            order.getItems().stream()
                .map(item -> new OrderCreatedEvent.OrderItem(
                    item.getProductId().getValue().toString(),
                    item.getProductName(),
                    item.getQuantity(),
                    item.getUnitPrice().getAmount()
                ))
                .collect(Collectors.toList()),
            order.getTotalAmount().getAmount(),
            LocalDateTime.now()
        );
    }
    
    /**
     * 중복 주문 예외
     */
    public static class DuplicateOrderException extends RuntimeException {
        public DuplicateOrderException(String message) {
            super(message);
        }
    }
}