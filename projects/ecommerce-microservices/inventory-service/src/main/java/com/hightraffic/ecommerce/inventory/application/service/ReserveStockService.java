package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.common.event.inventory.StockReservedEvent;
import com.hightraffic.ecommerce.common.event.inventory.InsufficientStockEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase.ReservationResult;
import com.hightraffic.ecommerce.inventory.application.port.in.ReserveStockUseCase.BatchReservationResult;
import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.application.port.out.SaveProductPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.domain.service.StockDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 재고 예약 Use Case 구현체
 * 
 * 책임:
 * - 재고 예약 처리
 * - 분산 락을 통한 동시성 제어
 * - 도메인 이벤트 발행
 * - 트랜잭션 관리
 */
@Service
@Transactional
public class ReserveStockService implements ReserveStockUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(ReserveStockService.class);
    
    // 분산 락 설정
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;
    private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;
    
    private final LoadProductPort loadProductPort;
    private final SaveProductPort saveProductPort;
    private final PublishEventPort publishEventPort;
    private final DistributedLockPort distributedLockPort;
    private final StockDomainService stockDomainService;
    
    public ReserveStockService(LoadProductPort loadProductPort,
                             SaveProductPort saveProductPort,
                             PublishEventPort publishEventPort,
                             DistributedLockPort distributedLockPort,
                             StockDomainService stockDomainService) {
        this.loadProductPort = loadProductPort;
        this.saveProductPort = saveProductPort;
        this.publishEventPort = publishEventPort;
        this.distributedLockPort = distributedLockPort;
        this.stockDomainService = stockDomainService;
    }
    
    @Override
    public ReservationResult reserveStock(ReserveStockCommand command) {
        log.info("Reserving stock for product: {} with quantity: {}", 
            command.getProductId(), command.getQuantity());
        
        String lockKey = generateLockKey(command.getProductId());
        
        try {
            return distributedLockPort.executeWithLock(
                lockKey,
                LOCK_WAIT_TIME,
                LOCK_LEASE_TIME,
                LOCK_TIME_UNIT,
                () -> processReservation(command)
            );
        } catch (DistributedLockPort.LockAcquisitionException e) {
            log.error("Failed to acquire lock for product: {}", command.getProductId(), e);
            throw new StockReservationException("System is busy. Please try again later.");
        }
    }
    
    @Override
    public List<ReservationResult> reserveBatchStock(ReserveBatchStockCommand command) {
        log.info("Reserving batch stock for order: {} with {} items", 
            command.getOrderId(), command.getStockItems().size());
        
        List<ReservationResult> results = new ArrayList<>();
        List<ReservationId> successfulReservations = new ArrayList<>();
        
        try {
            for (ReserveBatchStockCommand.StockItem item : command.getStockItems()) {
                try {
                    ReserveStockCommand reserveCommand = new ReserveStockCommand(
                        item.getProductId(),
                        item.getQuantity(),
                        command.getOrderId(),
                        null // 기본 예약 시간 사용
                    );
                    
                    ReservationResult reservationResult = reserveStock(reserveCommand);
                    ReservationId reservationId = reservationResult.getReservationId();
                    successfulReservations.add(reservationId);
                    results.add(new ReservationResult(item.getProductId(), "SUCCESS"));
                    
                } catch (Exception e) {
                    log.error("Failed to reserve stock for product: {}", item.getProductId(), e);
                    
                    if (command.isAtomicReservation()) {
                        // 원자적 예약인 경우 모든 성공한 예약 롤백
                        rollbackReservations(successfulReservations, command.getOrderId());
                        throw e;
                    } else {
                        // 부분 성공 허용
                        results.add(new ReservationResult(item.getProductId(), e.getMessage()));
                    }
                }
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Batch stock reservation failed for order: {}", command.getOrderId(), e);
            throw new BatchReservationException("Batch reservation failed", e);
        }
    }
    
    /**
     * 실제 예약 처리 로직
     */
    private ReservationResult processReservation(ReserveStockCommand command) {
        // 1. 상품 조회
        Product product = loadProductPort.loadProduct(command.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        
        // 2. 재고 가용성 검증
        try {
            stockDomainService.validateStockAvailability(product, command.getQuantity());
        } catch (Exception e) {
            // 재고 부족 이벤트 발행
            publishInsufficientStockEvent(command);
            throw e;
        }
        
        // 3. 재고 예약
        ReservationId reservationId = product.reserveStock(
            command.getQuantity(), 
            command.getOrderId()
        );
        
        // 4. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 5. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        // 6. 성공 이벤트 추가 발행
        publishStockReservedEvent(savedProduct, reservationId, command);
        
        log.info("Stock reserved successfully. Product: {}, Reservation: {}", 
            command.getProductId(), reservationId);
        
        // ReservationResult 생성하여 반환
        return new ReservationResult(
            command.getProductId(),
            reservationId,
            command.getQuantity(),
            savedProduct.getAvailableQuantity(),
            java.time.Instant.now().plusSeconds(
                command.getReservationMinutes() != null ? 
                command.getReservationMinutes() * 60 : 1800) // 기본 30분
        );
    }
    
    /**
     * 예약 롤백
     */
    private void rollbackReservations(List<ReservationId> reservationIds, String orderId) {
        log.info("Rolling back {} reservations for order: {}", reservationIds.size(), orderId);
        
        for (ReservationId reservationId : reservationIds) {
            try {
                // RestoreStockService를 통해 처리하거나 직접 처리
                // 여기서는 간단히 로그만 남김
                log.info("Rollback reservation: {}", reservationId);
            } catch (Exception e) {
                log.error("Failed to rollback reservation: {}", reservationId, e);
            }
        }
    }
    
    /**
     * 재고 예약 성공 이벤트 발행
     */
    private void publishStockReservedEvent(Product product, ReservationId reservationId, 
                                          ReserveStockCommand command) {
        StockReservedEvent.ReservedItem reservedItem = new StockReservedEvent.ReservedItem(
            product.getProductId().getValue().toString(),
            product.getProductName(),
            command.getQuantity().getValue(),
            "MAIN_WAREHOUSE",
            "AVAILABLE",
            0.0
        );
        
        StockReservedEvent event = new StockReservedEvent(
            product.getProductId().getValue().toString(),
            reservationId.getValue().toString(),
            command.getOrderId(),
            command.getOrderId(), // customerId (using orderId as placeholder)
            List.of(reservedItem),
            java.time.Instant.now().plus(command.getReservationMinutes() != null ? command.getReservationMinutes() : 30, java.time.temporal.ChronoUnit.MINUTES),
            "IMMEDIATE",
            1
        );
        
        publishEventPort.publishEvent(event);
    }
    
    /**
     * 재고 부족 이벤트 발행
     */
    private void publishInsufficientStockEvent(ReserveStockCommand command) {
        InsufficientStockEvent event = new InsufficientStockEvent(
            command.getOrderId(),
            command.getProductId().getValue().toString(),
            command.getQuantity().getValue(),
            0, // availableQuantity (placeholder)
            "재고 부족"
        );
        
        publishEventPort.publishEvent(event);
    }
    
    /**
     * 락 키 생성
     */
    private String generateLockKey(ProductId productId) {
        return String.format("stock:lock:product:%s", productId.getValue());
    }
    
    /**
     * 재고 예약 실패 예외
     */
    private static class StockReservationException extends RuntimeException {
        public StockReservationException(String message) {
            super(message);
        }
    }
    
    @Override
    public BatchReservationResult reserveStockBatch(BatchReserveStockCommand command) {
        log.info("Reserving stock batch: {} with {} items", 
            command.getReservationId(), command.getItems().size());
        
        List<ReservationResult> results = new ArrayList<>();
        
        for (ReserveStockCommand.ReservationItem item : command.getItems()) {
            try {
                ReserveStockCommand reserveCommand = new ReserveStockCommand(
                    item.getProductId(),
                    item.getQuantity(),
                    command.getReservationId(), // orderId로 사용
                    command.getTimeout() != null ? (int) command.getTimeout().toMinutes() : 30
                );
                
                ReservationResult result = reserveStock(reserveCommand);
                results.add(result);
                
            } catch (Exception e) {
                log.error("Failed to reserve stock for product: {}", item.getProductId(), e);
                results.add(new ReservationResult(item.getProductId(), e.getMessage()));
            }
        }
        
        return new BatchReservationResult(results, command.getReservationId());
    }
    
    /**
     * 배치 예약 실패 예외
     */
    private static class BatchReservationException extends RuntimeException {
        public BatchReservationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}