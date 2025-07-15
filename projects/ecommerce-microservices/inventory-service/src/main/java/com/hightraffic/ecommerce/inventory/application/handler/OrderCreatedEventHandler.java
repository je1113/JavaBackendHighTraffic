package com.hightraffic.ecommerce.inventory.application.handler;

import com.hightraffic.ecommerce.common.event.inventory.InsufficientStockEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCreatedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
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
            List<ReserveStockUseCase.ReserveStockCommand.ReservationItem> reservationItems = 
                event.getOrderItems().stream()
                    .map(item -> new ReserveStockUseCase.ReserveStockCommand.ReservationItem(
                        item.getProductId(),
                        BigDecimal.valueOf(item.getQuantity())
                    ))
                    .collect(Collectors.toList());
            
            // 배치 재고 예약 명령 생성
            ReserveStockUseCase.BatchReserveStockCommand command = 
                new ReserveStockUseCase.BatchReserveStockCommand(
                    event.getOrderId(),
                    reservationItems,
                    DEFAULT_RESERVATION_TIMEOUT
                );
            
            // 재고 예약 실행
            ReserveStockUseCase.BatchReservationResult result = 
                reserveStockUseCase.reserveStockBatch(command);
            
            if (result.isFullySuccessful()) {
                // 모든 재고 예약 성공
                log.info("모든 재고 예약 성공: orderId={}, reservations={}",
                        event.getOrderId(), result.successfulReservations().size());
                
                // 재고 예약 성공 이벤트 발행
                publishStockReservedEvent(event, result);
            } else {
                // 일부 또는 전체 재고 예약 실패
                log.warn("재고 예약 부분 실패: orderId={}, 성공={}, 실패={}",
                        event.getOrderId(), 
                        result.successfulReservations().size(),
                        result.failedReservations().size());
                
                // 성공한 예약들 롤백
                rollbackSuccessfulReservations(result.successfulReservations());
                
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
                reserveStockUseCase.releaseReservation(
                    new ReserveStockUseCase.ReleaseReservationCommand(
                        reservation.productId(),
                        reservation.reservationId()
                    )
                );
                log.debug("예약 롤백 성공: productId={}, reservationId={}",
                        reservation.productId(), reservation.reservationId());
            } catch (Exception e) {
                log.error("예약 롤백 실패: productId={}, reservationId={}",
                        reservation.productId(), reservation.reservationId(), e);
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
            result.successfulReservations().stream()
                .map(reservation -> new StockReservedEvent.ReservedItem(
                    reservation.productId(),
                    reservation.reservedQuantity(),
                    reservation.reservationId()
                ))
                .collect(Collectors.toList());
        
        // 이벤트 생성
        StockReservedEvent event = new StockReservedEvent(
            orderEvent.getOrderId(),
            reservedItems,
            DEFAULT_RESERVATION_TIMEOUT
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
        // 실패한 항목들을 이벤트용 데이터로 변환
        List<InsufficientStockEvent.InsufficientItem> insufficientItems = 
            result.failedReservations().stream()
                .map(failure -> new InsufficientStockEvent.InsufficientItem(
                    failure.productId(),
                    failure.requestedQuantity(),
                    failure.availableQuantity(),
                    failure.reason()
                ))
                .collect(Collectors.toList());
        
        // 이벤트 생성
        InsufficientStockEvent event = new InsufficientStockEvent(
            orderEvent.getOrderId(),
            insufficientItems
        );
        
        // 이벤트 발행
        publishEventPort.publishEvent(event);
        
        log.info("재고 부족 이벤트 발행: orderId={}, insufficientCount={}",
                orderEvent.getOrderId(), insufficientItems.size());
    }
    
    /**
     * 오류 발생 시 재고 부족 이벤트 발행
     */
    private void publishInsufficientStockEventOnError(OrderCreatedEvent orderEvent, Exception e) {
        // 모든 주문 항목을 실패로 처리
        List<InsufficientStockEvent.InsufficientItem> insufficientItems = 
            orderEvent.getOrderItems().stream()
                .map(item -> new InsufficientStockEvent.InsufficientItem(
                    item.getProductId(),
                    BigDecimal.valueOf(item.getQuantity()),
                    BigDecimal.ZERO,
                    e.getMessage()
                ))
                .collect(Collectors.toList());
        
        // 이벤트 생성
        InsufficientStockEvent event = new InsufficientStockEvent(
            orderEvent.getOrderId(),
            insufficientItems
        );
        
        // 이벤트 발행
        publishEventPort.publishEvent(event);
        
        log.error("오류로 인한 재고 부족 이벤트 발행: orderId={}, error={}",
                orderEvent.getOrderId(), e.getMessage());
    }
}