package com.hightraffic.ecommerce.order.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.order.adapter.in.messaging.dto.*;
import com.hightraffic.ecommerce.order.application.port.in.HandlePaymentCompletedEventUseCase;
import com.hightraffic.ecommerce.order.application.port.in.HandleStockReservedEventUseCase;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.common.event.payment.PaymentCompletedEvent;
import java.util.stream.Collectors;
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
 * 주문 서비스 이벤트 리스너
 * 
 * Kafka를 통해 다른 서비스의 이벤트를 수신하고 처리합니다.
 * 헥사고날 아키텍처의 Inbound Messaging Adapter 역할
 */
@Component
public class OrderEventListener {
    
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    
    private final HandleStockReservedEventUseCase handleStockReservedEventUseCase;
    private final HandlePaymentCompletedEventUseCase handlePaymentCompletedEventUseCase;
    private final ObjectMapper objectMapper;
    
    public OrderEventListener(HandleStockReservedEventUseCase handleStockReservedEventUseCase,
                            HandlePaymentCompletedEventUseCase handlePaymentCompletedEventUseCase,
                            ObjectMapper objectMapper) {
        this.handleStockReservedEventUseCase = handleStockReservedEventUseCase;
        this.handlePaymentCompletedEventUseCase = handlePaymentCompletedEventUseCase;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 재고 예약 완료 이벤트 처리
     */
    @KafkaListener(
        topics = "${app.kafka.topics.stock-reserved}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "orderKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleStockReservedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {
        
        log.info("재고 예약 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            StockReservedEventMessage eventMessage = objectMapper.readValue(message, StockReservedEventMessage.class);
            
            // 메시지 유효성 검증
            validateStockReservedEvent(eventMessage);
            
            // 이벤트 핸들러 호출
            handleStockReservedEventUseCase.handle(mapToEvent(eventMessage));
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("재고 예약 이벤트 처리 완료: orderId={}, reservedItems={}", 
                    eventMessage.orderId(), eventMessage.getTotalReservedItemCount());
            
        } catch (JsonProcessingException e) {
            log.error("재고 예약 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("재고 예약 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    /**
     * 재고 부족 이벤트 처리
     */
    @KafkaListener(
        topics = "${app.kafka.topics.insufficient-stock}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "orderKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleInsufficientStockEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("재고 부족 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            InsufficientStockEventMessage eventMessage = objectMapper.readValue(message, InsufficientStockEventMessage.class);
            
            // 메시지 유효성 검증
            validateInsufficientStockEvent(eventMessage);
            
            // 주문 취소 처리
            handleInsufficientStock(eventMessage);
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("재고 부족 이벤트 처리 완료: orderId={}, insufficientItems={}", 
                    eventMessage.orderId(), eventMessage.insufficientItems().size());
            
        } catch (JsonProcessingException e) {
            log.error("재고 부족 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("재고 부족 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    /**
     * 결제 완료 이벤트 처리
     */
    @KafkaListener(
        topics = "${app.kafka.topics.payment-completed}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "orderKafkaListenerContainerFactory"
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
            
            // 이벤트 핸들러 호출
            handlePaymentCompletedEventUseCase.handle(mapToEvent(eventMessage));
            
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
    
    /**
     * 재고 차감 완료 이벤트 처리
     */
    @KafkaListener(
        topics = "${app.kafka.topics.stock-deducted}",
        groupId = "${app.kafka.consumer.group-id}",
        containerFactory = "orderKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional
    public void handleStockDeductedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("재고 차감 이벤트 수신: topic={}, partition={}, offset={}", topic, partition, offset);
        
        try {
            // JSON 메시지를 DTO로 역직렬화
            StockDeductedEventMessage eventMessage = objectMapper.readValue(message, StockDeductedEventMessage.class);
            
            // 메시지 유효성 검증
            validateStockDeductedEvent(eventMessage);
            
            // 주문 확정 처리
            handleStockDeducted(eventMessage);
            
            // 수동 커밋
            acknowledgment.acknowledge();
            
            log.info("재고 차감 이벤트 처리 완료: orderId={}, deductedItems={}", 
                    eventMessage.orderId(), eventMessage.getTotalDeductedItemCount());
            
        } catch (JsonProcessingException e) {
            log.error("재고 차감 이벤트 역직렬화 실패: message={}", message, e);
            handleDeserializationError(message, topic, partition, offset, e);
            throw new MessageProcessingException("이벤트 역직렬화 실패", e);
            
        } catch (Exception e) {
            log.error("재고 차감 이벤트 처리 실패: orderId={}", 
                    extractOrderIdSafely(message), e);
            throw new MessageProcessingException("이벤트 처리 실패", e);
        }
    }
    
    // === Private Helper Methods ===
    
    private void validateStockReservedEvent(StockReservedEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (!event.isFullyReserved()) {
            throw new IllegalArgumentException("예약된 아이템이 없습니다");
        }
    }
    
    private void validateInsufficientStockEvent(InsufficientStockEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (event.insufficientItems() == null || event.insufficientItems().isEmpty()) {
            throw new IllegalArgumentException("재고 부족 아이템 정보가 없습니다");
        }
    }
    
    private void validatePaymentCompletedEvent(PaymentCompletedEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (!event.isValidAmount()) {
            throw new IllegalArgumentException("유효하지 않은 결제 금액입니다");
        }
    }
    
    private void validateStockDeductedEvent(StockDeductedEventMessage event) {
        if (event.orderId() == null || event.orderId().isBlank()) {
            throw new IllegalArgumentException("주문 ID는 필수입니다");
        }
        if (!event.isFullyDeducted()) {
            throw new IllegalArgumentException("차감된 아이템이 없습니다");
        }
    }
    
    private void handleInsufficientStock(InsufficientStockEventMessage event) {
        // 주문 취소 처리 로직
        log.warn("재고 부족으로 주문 취소: orderId={}", event.orderId());
        // TODO: 주문 취소 Use Case 호출
    }
    
    private void handleStockDeducted(StockDeductedEventMessage event) {
        // 주문 확정 처리 로직
        log.info("재고 차감 완료로 주문 확정: orderId={}", event.orderId());
        // TODO: 주문 확정 Use Case 호출
    }
    
    private StockReservedEvent mapToEvent(StockReservedEventMessage message) {
        // StockReservedEventMessage를 StockReservedEvent로 변환
        return new StockReservedEvent(
            message.aggregateId(),
            "RES_" + message.orderId(),
            message.orderId(),
            "CUSTOMER_" + message.orderId(), // customerId 추출 불가능하므로 임시값
            message.reservedItems().stream()
                .map(item -> new StockReservedEvent.ReservedItem(
                    item.productId(),
                    "Product Name", // productName은 메시지에 없으므로 임시값
                    item.reservedQuantity().intValue(),
                    "WH_01", // warehouseId는 메시지에 없으므로 임시값
                    "SYSTEM", // reservedFrom은 메시지에 없으므로 임시값
                    100.0 // unitPrice는 메시지에 없으므로 임시값
                ))
                .collect(Collectors.toList()),
            message.timestamp().plus(message.reservationTimeout()),
            "ORDER",
            1
        );
    }
    
    private PaymentCompletedEvent mapToEvent(PaymentCompletedEventMessage message) {
        // PaymentCompletedEventMessage를 PaymentCompletedEvent로 변환
        return new PaymentCompletedEvent(
            message.paymentId(),
            message.orderId(),
            message.customerId(),
            message.amount(),
            message.currency(),
            message.paymentMethod(),
            message.transactionId(),
            java.time.LocalDateTime.now() // paidAt 타입 변환
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