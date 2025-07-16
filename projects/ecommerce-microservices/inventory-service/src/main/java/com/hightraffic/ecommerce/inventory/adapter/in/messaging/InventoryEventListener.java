package com.hightraffic.ecommerce.inventory.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.inventory.adapter.in.messaging.dto.*;
import com.hightraffic.ecommerce.inventory.application.handler.OrderCancelledEventHandler;
import com.hightraffic.ecommerce.inventory.application.handler.OrderCreatedEventHandler;
import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCancelledEvent;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 서비스 이벤트 리스너
 * 
 * Kafka를 통해 다른 서비스의 이벤트를 수신하고 처리합니다.
 * 헥사고날 아키텍처의 Inbound Messaging Adapter 역할
 */
@Component
public class InventoryEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);
    
    private final OrderCreatedEventHandler orderCreatedEventHandler;
    private final OrderCancelledEventHandler orderCancelledEventHandler;
    private final ObjectMapper objectMapper;
    
    public InventoryEventListener(OrderCreatedEventHandler orderCreatedEventHandler,
                                OrderCancelledEventHandler orderCancelledEventHandler,
                                ObjectMapper objectMapper) {
        this.orderCreatedEventHandler = orderCreatedEventHandler;
        this.orderCancelledEventHandler = orderCancelledEventHandler;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 주문 생성 이벤트 처리
     * 주문이 생성되면 재고 예약을 수행합니다.
     */
    @KafkaListener(
        topics = "${app.kafka.topics.order-created}",
        groupId = "${app.kafka.consumer.group-id.inventory}",
        containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleOrderCreatedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        log.info("주문 생성 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            OrderCreatedEventMessage eventMessage = objectMapper.readValue(message, OrderCreatedEventMessage.class);
            
            // 메시지 유효성 검증
            validateOrderCreatedEvent(eventMessage);
            
            // 이벤트 핸들러 호출
            orderCreatedEventHandler.handle(mapToEvent(eventMessage));
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("주문 생성 이벤트 처리 완료: orderId={}, itemCount={}", 
                    eventMessage.orderId(), eventMessage.orderItems().size());
            
        } catch (JsonProcessingException e) {
            log.error("주문 생성 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("주문 생성 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    /**
     * 주문 취소 이벤트 처리
     * 주문이 취소되면 예약된 재고를 복원합니다.
     */
    @KafkaListener(
        topics = "${app.kafka.topics.order-cancelled}",
        groupId = "${app.kafka.consumer.group-id.inventory}",
        containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleOrderCancelledEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("주문 취소 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            OrderCancelledEventMessage eventMessage = objectMapper.readValue(message, OrderCancelledEventMessage.class);
            
            // 메시지 유효성 검증
            validateOrderCancelledEvent(eventMessage);
            
            // 이벤트 핸들러 호출
            orderCancelledEventHandler.handle(mapToEvent(eventMessage));
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("주문 취소 이벤트 처리 완료: orderId={}, restoredItemCount={}", 
                    eventMessage.orderId(), eventMessage.cancelledItems().size());
            
        } catch (JsonProcessingException e) {
            log.error("주문 취소 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("주문 취소 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    /**
     * 결제 완료 이벤트 처리
     * 결제가 완료되면 예약된 재고를 실제로 차감합니다.
     */
    @KafkaListener(
        topics = "${app.kafka.topics.payment-completed}",
        groupId = "${app.kafka.consumer.group-id.inventory}",
        containerFactory = "inventoryKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handlePaymentCompletedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("결제 완료 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            PaymentCompletedEventMessage eventMessage = objectMapper.readValue(message, PaymentCompletedEventMessage.class);
            
            // 메시지 유효성 검증
            validatePaymentCompletedEvent(eventMessage);
            
            // 재고 차감 처리
            handlePaymentCompleted(eventMessage);
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("결제 완료 이벤트 처리 완료: orderId={}, paymentId={}, amount={}", 
                    eventMessage.orderId(), eventMessage.paymentId(), eventMessage.amount());
            
        } catch (JsonProcessingException e) {
            log.error("결제 완료 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("결제 완료 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    // === Private Helper Methods ===
    
    private void validateOrderCreatedEvent(OrderCreatedEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (event.orderItems() == null || event.orderItems().isEmpty()) {
            throw new IllegalArgumentException("주문 아이템이 없습니다");
        }
        if (event.customerId() == null || event.customerId().isBlank()) {
            throw new IllegalArgumentException("고객 ID는 필수입니다");
        }
    }
    
    private void validateOrderCancelledEvent(OrderCancelledEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (event.cancelledItems() == null || event.cancelledItems().isEmpty()) {
            throw new IllegalArgumentException("취소된 아이템 정보가 없습니다");
        }
    }
    
    private void validatePaymentCompletedEvent(PaymentCompletedEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (event.paymentId() == null || event.paymentId().isBlank()) {
            throw new IllegalArgumentException("결제 ID는 필수입니다");
        }
        if (event.amount() == null || event.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("유효하지 않은 결제 금액입니다");
        }
    }
    
    private void handlePaymentCompleted(PaymentCompletedEventMessage event) {
        // 예약된 재고를 실제로 차감하는 로직
        log.info("결제 완료로 재고 차감 시작: orderId={}", event.orderId());
        // TODO: 재고 차감 Use Case 호출
    }
    
    private OrderCreatedEvent mapToEvent(OrderCreatedEventMessage message) {
        // OrderItemData 변환
        List<OrderCreatedEvent.OrderItemData> orderItems = message.orderItems().stream()
            .map(item -> new OrderCreatedEvent.OrderItemData(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice(),
                item.totalPrice()
            ))
            .collect(java.util.stream.Collectors.toList());
            
        return new OrderCreatedEvent(
            message.orderId(),
            message.customerId(),
            orderItems,
            message.totalAmount(),
            message.currency()
        );
    }
    
    private OrderCancelledEvent mapToEvent(OrderCancelledEventMessage message) {
        // OrderCancelledEvent의 생성자에 맞춰 변환
        return new OrderCancelledEvent(
            message.orderId(),
            message.customerId(),
            "CONFIRMED", // previousStatus - 기본값
            message.cancelReason(),
            message.cancelReasonCode(),
            message.cancelledBy(),
            message.cancelledByType(),
            java.math.BigDecimal.ZERO, // refundAmount - 기본값
            message.compensationActions() != null ? 
                message.compensationActions().stream()
                    .map(action -> new OrderCancelledEvent.CompensationAction(
                        action.actionType(),
                        action.targetService(),
                        action.actionData(),
                        action.priority()
                    ))
                    .collect(java.util.stream.Collectors.toList()) : 
                java.util.List.of(),
            message.cancellationReason() // cancellationNotes
        );
    }
    
    private void handleDeserializationError(String message, String topic, int partition, long offset, Exception e) {
        // 역직렬화 실패한 메시지를 DLQ로 전송하거나 별도 처리
        log.error("메시지 역직렬화 실패 - DLQ 처리 필요: topic={}, partition={}, offset={}, message={}", 
                topic, partition, offset, message);
        // TODO: DLQ 전송 로직
    }
    
    private String extractOrderIdSafely(String message) {
        try {
            // 간단한 정규식으로 orderId 추출
            if (message.contains("\"orderId\"")) {
                int start = message.indexOf("\"orderId\"") + 11;
                int end = message.indexOf("\"", start + 1);
                if (end > start) {
                    return message.substring(start + 1, end);
                }
            }
            return "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 메시지 처리 예외
     */
    public static class MessageProcessingException extends RuntimeException {
        public MessageProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}