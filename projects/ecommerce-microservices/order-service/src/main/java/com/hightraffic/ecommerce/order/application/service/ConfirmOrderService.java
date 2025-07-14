package com.hightraffic.ecommerce.order.application.service;

import com.hightraffic.ecommerce.common.event.order.OrderConfirmedEvent;
import com.hightraffic.ecommerce.order.application.port.in.ConfirmOrderUseCase;
import com.hightraffic.ecommerce.order.application.port.out.LoadOrderPort;
import com.hightraffic.ecommerce.order.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.order.application.port.out.SaveOrderPort;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * 주문 확정 Use Case 구현체
 * 
 * 책임:
 * - 주문 확정 요청 처리
 * - 주문 상태 검증
 * - 도메인 이벤트 발행
 * - 결제 프로세스 트리거
 */
@Service
@Transactional
public class ConfirmOrderService implements ConfirmOrderUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(ConfirmOrderService.class);
    
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;
    private final PublishEventPort publishEventPort;
    
    public ConfirmOrderService(LoadOrderPort loadOrderPort,
                             SaveOrderPort saveOrderPort,
                             PublishEventPort publishEventPort) {
        this.loadOrderPort = loadOrderPort;
        this.saveOrderPort = saveOrderPort;
        this.publishEventPort = publishEventPort;
    }
    
    @Override
    public void confirmOrder(ConfirmOrderCommand command) {
        log.info("Confirming order: {}", command.getOrderId());
        
        // 1. 주문 조회
        Order order = loadOrderPort.loadOrder(command.getOrderId())
            .orElseThrow(() -> new OrderNotFoundException(command.getOrderId()));
        
        // 2. 이미 확정된 주문인지 확인
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new OrderAlreadyConfirmedException(command.getOrderId());
        }
        
        // 3. 확정 가능한 상태인지 검증
        if (!order.canBeConfirmed()) {
            throw new InvalidOrderStateException(
                command.getOrderId(), 
                order.getStatus().name()
            );
        }
        
        // 4. 주문 확정
        order.confirm();
        
        // 5. 변경사항 저장
        Order confirmedOrder = saveOrderPort.saveOrder(order);
        
        // 6. 도메인 이벤트 생성 및 발행
        OrderConfirmedEvent event = createOrderConfirmedEvent(confirmedOrder);
        publishEventPort.publishEvent(event);
        
        log.info("Order confirmed successfully: {}", confirmedOrder.getOrderId());
    }
    
    /**
     * OrderConfirmedEvent 생성
     * 
     * 이 이벤트는 결제 서비스와 재고 서비스에서 소비됨
     */
    private OrderConfirmedEvent createOrderConfirmedEvent(Order order) {
        return new OrderConfirmedEvent(
            order.getOrderId().getValue().toString(),
            order.getCustomerId().getValue().toString(),
            order.getItems().stream()
                .map(item -> new OrderConfirmedEvent.OrderItem(
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
}