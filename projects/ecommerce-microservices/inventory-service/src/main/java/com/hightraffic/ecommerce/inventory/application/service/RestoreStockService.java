package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.common.event.inventory.StockReleasedEvent;
import com.hightraffic.ecommerce.common.event.inventory.StockAdjustedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase.ReleaseReservationResult;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase.BatchReleaseResult;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase.ReleaseResult;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.StockQuantity;
import com.hightraffic.ecommerce.inventory.domain.exception.ProductNotFoundException;
import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.application.port.out.SaveProductPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
import com.hightraffic.ecommerce.inventory.domain.service.StockDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 재고 복원 Use Case 구현체
 * 
 * 책임:
 * - 예약 해제 (보상 트랜잭션)
 * - 재고 추가 (입고)
 * - 재고 조정
 * - 만료된 예약 자동 정리
 */
@Service
@Transactional
public class RestoreStockService implements RestoreStockUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(RestoreStockService.class);
    
    // 분산 락 설정
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;
    private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;
    
    private final LoadProductPort loadProductPort;
    private final SaveProductPort saveProductPort;
    private final PublishEventPort publishEventPort;
    private final DistributedLockPort distributedLockPort;
    private final StockDomainService stockDomainService;
    
    public RestoreStockService(LoadProductPort loadProductPort,
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
    public ReleaseReservationResult releaseReservation(ReleaseReservationCommand command) {
        log.info("Releasing reservation: {} for order: {}", 
            command.getReservationId(), command.getOrderId());
        
        // 예약으로 상품 찾기 (실제로는 더 효율적인 방법 필요)
        Product product = findProductByReservation(command.getReservationId());
        String lockKey = generateLockKey(product.getProductId());
        
        return distributedLockPort.executeWithLock(
            lockKey,
            LOCK_WAIT_TIME,
            LOCK_LEASE_TIME,
            LOCK_TIME_UNIT,
            () -> processReservationRelease(product, command)
        );
    }
    
    @Override
    public List<ReleaseResult> releaseBatchReservations(ReleaseBatchReservationsCommand command) {
        log.info("Releasing batch reservations for order: {} with {} items", 
            command.getOrderId(), command.getReleaseItems().size());
        
        List<ReleaseResult> results = new ArrayList<>();
        
        for (RestoreStockUseCase.ReleaseBatchReservationsCommand.ReleaseItem item : command.getReleaseItems()) {
            try {
                ReleaseReservationCommand releaseCommand = new ReleaseReservationCommand(
                    item.getReservationId(),
                    command.getOrderId(),
                    command.getReleaseReason()
                );
                
                releaseReservation(releaseCommand);
                results.add(new ReleaseResult(item.getReservationId(), item.getProductId()));
                
            } catch (Exception e) {
                log.error("Failed to release reservation: {}", item.getReservationId(), e);
                results.add(new ReleaseResult(
                    item.getReservationId(), 
                    item.getProductId(), 
                    e.getMessage()
                ));
            }
        }
        
        return results;
    }
    
    @Override
    public void addStock(AddStockCommand command) {
        log.info("Adding stock to product: {} with quantity: {}", 
            command.getProductId(), command.getQuantity());
        
        String lockKey = generateLockKey(command.getProductId());
        
        distributedLockPort.executeWithLock(
            lockKey,
            LOCK_WAIT_TIME,
            LOCK_LEASE_TIME,
            LOCK_TIME_UNIT,
            () -> {
                processStockAddition(command);
                return null;
            }
        );
    }
    
    @Override
    public void adjustStock(AdjustStockCommand command) {
        log.info("Adjusting stock for product: {} to quantity: {}", 
            command.getProductId(), command.getNewTotalQuantity());
        
        String lockKey = generateLockKey(command.getProductId());
        
        distributedLockPort.executeWithLock(
            lockKey,
            LOCK_WAIT_TIME,
            LOCK_LEASE_TIME,
            LOCK_TIME_UNIT,
            () -> {
                processStockAdjustment(command);
                return null;
            }
        );
    }
    
    @Override
    @Scheduled(fixedDelay = 300000) // 5분마다 실행
    public int cleanupExpiredReservations() {
        log.info("Starting cleanup of expired reservations");
        
        AtomicInteger totalCleaned = new AtomicInteger(0);
        
        // 실제로는 모든 상품을 효율적으로 조회하는 방법 필요
        // 여기서는 예시로만 구현
        log.warn("Expired reservation cleanup needs proper implementation");
        
        return totalCleaned.get();
    }
    
    /**
     * 예약 해제 처리
     */
    private ReleaseReservationResult processReservationRelease(Product product, ReleaseReservationCommand command) {
        // 예약 정보 조회 (해제 전)
        StockReservation reservation = product.getReservation(command.getReservationId());
        if (reservation == null) {
            return new ReleaseReservationResult(
                product.getProductId(), 
                command.getReservationId(), 
                "Reservation not found"
            );
        }
        
        StockQuantity releasedQuantity = reservation.getQuantity();
        
        // 1. 예약 해제
        product.releaseReservation(command.getReservationId(), command.getOrderId());
        
        // 2. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 3. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        log.info("Reservation released successfully. Product: {}, Reservation: {}", 
            product.getProductId(), command.getReservationId());
        
        // 4. 결과 반환
        return new ReleaseReservationResult(
            savedProduct.getProductId(),
            command.getReservationId(),
            releasedQuantity,
            savedProduct.getAvailableQuantity()
        );
    }
    
    /**
     * 재고 추가 처리
     */
    private void processStockAddition(AddStockCommand command) {
        // 1. 상품 조회
        Product product = loadProductPort.loadProduct(command.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        
        // 2. 재고 추가
        product.addStock(command.getQuantity(), command.getReason());
        
        // 3. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 4. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        log.info("Stock added successfully. Product: {}, Quantity: {}", 
            command.getProductId(), command.getQuantity());
    }
    
    /**
     * 재고 조정 처리
     */
    private void processStockAdjustment(AdjustStockCommand command) {
        // 1. 상품 조회
        Product product = loadProductPort.loadProduct(command.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        
        // 2. 조정 안전성 검증
        stockDomainService.validateStockAdjustmentSafety(product, command.getNewTotalQuantity());
        
        // 3. 재고 조정
        product.adjustStock(command.getNewTotalQuantity(), command.getReason());
        
        // 4. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 5. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        log.info("Stock adjusted successfully. Product: {}, New Total: {}", 
            command.getProductId(), command.getNewTotalQuantity());
    }
    
    @Override
    public BatchReleaseResult batchReleaseReservations(BatchReleaseReservationCommand command) {
        log.info("Batch releasing reservations for order: {}", command.getOrderId());
        
        List<ReleaseResult> results = new ArrayList<>();
        
        // 실제로는 orderId로 예약들을 찾는 기능이 필요
        // 여기서는 기본 구현만 제공
        log.warn("Batch release reservations needs proper implementation with reservation lookup by orderId");
        
        return new BatchReleaseResult(results, command.getOrderId());
    }
    
    /**
     * 예약으로 상품 찾기
     * 실제로는 더 효율적인 방법이 필요함
     */
    private Product findProductByReservation(ReservationId reservationId) {
        // 이 부분은 실제로는 예약 정보를 별도로 관리하거나
        // Product에 예약으로 검색하는 기능이 필요
        // 여기서는 예시로만 구현
        throw new UnsupportedOperationException(
            "Finding product by reservation needs to be implemented with proper repository"
        );
    }
    
    /**
     * 락 키 생성
     */
    private String generateLockKey(ProductId productId) {
        return String.format("stock:lock:product:%s", productId.getValue());
    }
}