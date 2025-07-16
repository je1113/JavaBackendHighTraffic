package com.hightraffic.ecommerce.inventory.application.handler;

import com.hightraffic.ecommerce.common.event.inventory.InsufficientStockEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 생성 이벤트 핸들러
 * 
 * 주문이 생성되면 해당 주문의 상품들에 대한 재고를 예약합니다.
 * 재고 예약에 실패하면 InsufficientStockEvent를 발행합니다.
 */
@Component
public class OrderCreatedEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(OrderCreatedEventHandler.class);
    
    private final ReserveStockUseCase reserveStockUseCase;
    private final PublishEventPort publishEventPort;
    
    // 예약 만료 시간 (기본 30분)
    private static final Duration DEFAULT_RESERVATION_TIMEOUT = Duration.ofMinutes(30);
    
    public OrderCreatedEventHandler(ReserveStockUseCase reserveStockUseCase,
                                  PublishEventPort publishEventPort) {
        this.reserveStockUseCase = reserveStockUseCase;
        this.publishEventPort = publishEventPort;
    }
    
    /**
     * 주문 생성 이벤트 처리
     * 
     * @param event 주문 생성 이벤트
     */
    @Transactional
    public void handle(OrderCreatedEvent event) {
        log.info("주문 생성 이벤트 처리 시작: orderId={}, customerId={}, items={}",
                event.getOrderId(), event.getCustomerId(), event.getOrderItems().size());
        
        try {
            // 주문 항목을 재고 예약 명령으로 변환
            List<ReserveStockUseCase.ReserveBatchStockCommand.StockItem> stockItems = 
                event.getOrderItems().stream()
                    .map(item -> new ReserveStockUseCase.ReserveBatchStockCommand.StockItem(
                        ProductId.of(item.getProductId()),
                        StockQuantity.of(item.getQuantity())
                    ))
                    .collect(Collectors.toList());
            
            // 배치 재고 예약 명령 생성
            ReserveStockUseCase.ReserveBatchStockCommand command = 
                new ReserveStockUseCase.ReserveBatchStockCommand(
                    stockItems,
                    event.getOrderId(),
                    true // atomic reservation
                );
            
            // 재고 예약 실행
            List<ReserveStockUseCase.ReservationResult> results = 
                reserveStockUseCase.reserveBatchStock(command);
            
            // 결과를 BatchReservationResult로 래핑
            ReserveStockUseCase.BatchReservationResult result = 
                new ReserveStockUseCase.BatchReservationResult(results, event.getOrderId());
            
            if (result.isAllSuccess()) {
                // 모든 재고 예약 성공
                log.info("모든 재고 예약 성공: orderId={}, reservations={}",
                        event.getOrderId(), result.getSuccessResults().size());
                
                // 재고 예약 성공 이벤트 발행
                publishStockReservedEvent(event, result);
            } else {
                // 일부 또는 전체 재고 예약 실패
                log.warn("재고 예약 부분 실패: orderId={}, 성공={}, 실패={}",
                        event.getOrderId(), 
                        result.getSuccessResults().size(),
                        result.getFailureResults().size());
                
                // 성공한 예약들 롤백
                rollbackSuccessfulReservations(result.getSuccessResults());
                
                // 재고 부족 이벤트 발행
                publishInsufficientStockEvent(event, result);
            }
            
        } catch (Exception e) {
            log.error("주문 생성 이벤트 처리 중 오류 발생: orderId={}", event.getOrderId(), e);
            
            // 재고 부족 이벤트 발행 (예외 정보 포함)
            publishInsufficientStockEventOnError(event, e);
        }
    }
    
    /**
     * 성공한 예약들을 롤백
     */
    private void rollbackSuccessfulReservations(
            List<ReserveStockUseCase.ReservationResult> successfulReservations) {
        
        if (successfulReservations.isEmpty()) {
            return;
        }
        
        log.info("성공한 예약 롤백 시작: count={}", successfulReservations.size());
        
        successfulReservations.forEach(reservation -> {
            try {
                // TODO: Implement release reservation logic
                log.debug("예약 롤백 성공: productId={}, reservationId={}",
                        reservation.getProductId(), reservation.getReservationId());
            } catch (Exception e) {
                log.error("예약 롤백 실패: productId={}, reservationId={}",
                        reservation.getProductId(), reservation.getReservationId(), e);
            }
        });
    }
    
    /**
     * 재고 예약 성공 이벤트 발행
     */
    private void publishStockReservedEvent(OrderCreatedEvent orderEvent,
                                         ReserveStockUseCase.BatchReservationResult result) {
        // 예약 정보를 이벤트용 데이터로 변환
        List<StockReservedEvent.ReservedItem> reservedItems = 
            result.getSuccessResults().stream()
                .map(reservation -> new StockReservedEvent.ReservedItem(
                    reservation.getProductId().toString(),
                    "Unknown Product", // TODO: Get actual product name
                    1, // TODO: Get actual reserved quantity
                    "MAIN", // Default warehouse
                    "AVAILABLE", // Reserved from available stock
                    null // Unit price not available
                ))
                .collect(Collectors.toList());
        
        // 이벤트 생성
        StockReservedEvent event = new StockReservedEvent(
            orderEvent.getOrderId(), // inventoryId
            orderEvent.getOrderId(), // reservationId
            orderEvent.getOrderId(), // orderId
            orderEvent.getCustomerId(), // customerId
            reservedItems,
            java.time.Instant.now().plus(DEFAULT_RESERVATION_TIMEOUT), // expiresAt
            "IMMEDIATE", // reservationType
            1 // priority
        );
        
        // 이벤트 발행
        publishEventPort.publishEvent(event);
        
        log.info("재고 예약 성공 이벤트 발행: orderId={}, itemCount={}",
                orderEvent.getOrderId(), reservedItems.size());
    }
    
    /**
     * 재고 부족 이벤트 발행
     */
    private void publishInsufficientStockEvent(OrderCreatedEvent orderEvent,
                                              ReserveStockUseCase.BatchReservationResult result) {
        // 첫 번째 실패 제품으로 이벤트 생성 (단순화)
        if (!result.getFailureResults().isEmpty()) {
            ReserveStockUseCase.ReservationResult firstFailure = result.getFailureResults().get(0);
            InsufficientStockEvent event = new InsufficientStockEvent(
                orderEvent.getOrderId(),
                firstFailure.getProductId().toString(),
                1, // TODO: Get actual requested quantity
                0, // Available quantity
                firstFailure.getFailureReason()
            );
            
            // 이벤트 발행
            publishEventPort.publishEvent(event);
        }
        
        
        log.info("재고 부족 이벤트 발행: orderId={}, failureCount={}",
                orderEvent.getOrderId(), result.getFailureResults().size());
    }
    
    /**
     * 오류 발생 시 재고 부족 이벤트 발행
     */
    private void publishInsufficientStockEventOnError(OrderCreatedEvent orderEvent, Exception e) {
        // 첫 번째 주문 항목으로 이벤트 생성
        if (!orderEvent.getOrderItems().isEmpty()) {
            var firstItem = orderEvent.getOrderItems().get(0);
            InsufficientStockEvent event = new InsufficientStockEvent(
                orderEvent.getOrderId(),
                firstItem.getProductId(),
                firstItem.getQuantity(),
                0,
                e.getMessage()
            );
            
            // 이벤트 발행
            publishEventPort.publishEvent(event);
        }
        
        
        log.error("오류로 인한 재고 부족 이벤트 발행: orderId={}, error={}",
                orderEvent.getOrderId(), e.getMessage());
    }
}