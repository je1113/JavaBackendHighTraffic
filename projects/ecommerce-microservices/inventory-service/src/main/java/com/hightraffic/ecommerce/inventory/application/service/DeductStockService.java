package com.hightraffic.ecommerce.inventory.application.service;

import com.hightraffic.ecommerce.common.event.inventory.StockDeductedEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.DeductStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.out.DistributedLockPort;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.application.port.out.SaveProductPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import com.hightraffic.ecommerce.inventory.domain.model.StockReservation;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ReservationId;
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
 * 재고 차감 Use Case 구현체
 * 
 * 책임:
 * - 예약된 재고 차감 (2-Phase Commit 완료)
 * - 직접 재고 차감
 * - 분산 락을 통한 동시성 제어
 * - 도메인 이벤트 발행
 */
@Service
@Transactional
public class DeductStockService implements DeductStockUseCase {
    
    private static final Logger log = LoggerFactory.getLogger(DeductStockService.class);
    
    // 분산 락 설정
    private static final long LOCK_WAIT_TIME = 3L;
    private static final long LOCK_LEASE_TIME = 5L;
    private static final TimeUnit LOCK_TIME_UNIT = TimeUnit.SECONDS;
    
    private final LoadProductPort loadProductPort;
    private final SaveProductPort saveProductPort;
    private final PublishEventPort publishEventPort;
    private final DistributedLockPort distributedLockPort;
    private final StockDomainService stockDomainService;
    
    public DeductStockService(LoadProductPort loadProductPort,
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
    public void deductReservedStock(DeductReservedStockCommand command) {
        log.info("Deducting reserved stock. Reservation: {}, Order: {}", 
            command.getReservationId(), command.getOrderId());
        
        // 예약 ID로부터 상품 ID를 찾아야 함
        // 실제로는 예약 정보를 별도로 관리하거나 Product에서 찾아야 함
        // 여기서는 간단히 모든 상품을 검색하는 로직으로 구현
        Product product = findProductByReservation(command.getReservationId());
        
        String lockKey = generateLockKey(product.getProductId());
        
        distributedLockPort.executeWithLock(
            lockKey,
            LOCK_WAIT_TIME,
            LOCK_LEASE_TIME,
            LOCK_TIME_UNIT,
            () -> {
                processReservedDeduction(product, command);
                return null;
            }
        );
    }
    
    @Override
    public void deductStockDirectly(DeductStockDirectlyCommand command) {
        log.info("Deducting stock directly for product: {} with quantity: {}", 
            command.getProductId(), command.getQuantity());
        
        String lockKey = generateLockKey(command.getProductId());
        
        distributedLockPort.executeWithLock(
            lockKey,
            LOCK_WAIT_TIME,
            LOCK_LEASE_TIME,
            LOCK_TIME_UNIT,
            () -> {
                processDirectDeduction(command);
                return null;
            }
        );
    }
    
    @Override
    public List<DeductionResult> deductBatchReservedStock(DeductBatchReservedStockCommand command) {
        log.info("Deducting batch reserved stock for order: {} with {} items", 
            command.getOrderId(), command.getDeductionItems().size());
        
        List<DeductionResult> results = new ArrayList<>();
        
        for (DeductionItem item : command.getDeductionItems()) {
            try {
                DeductReservedStockCommand deductCommand = new DeductReservedStockCommand(
                    item.getReservationId(),
                    command.getOrderId(),
                    "Batch deduction for order completion"
                );
                
                deductReservedStock(deductCommand);
                results.add(new DeductionResult(item.getReservationId(), item.getProductId()));
                
            } catch (Exception e) {
                log.error("Failed to deduct stock for reservation: {}", 
                    item.getReservationId(), e);
                results.add(new DeductionResult(
                    item.getReservationId(), 
                    item.getProductId(), 
                    e.getMessage()
                ));
            }
        }
        
        return results;
    }
    
    /**
     * 예약된 재고 차감 처리
     */
    private void processReservedDeduction(Product product, DeductReservedStockCommand command) {
        // 1. 예약 검증
        StockReservation reservation = product.getReservation(command.getReservationId());
        if (reservation == null) {
            throw new ReservationNotFoundException(command.getReservationId());
        }
        
        if (reservation.isExpired()) {
            throw new InvalidReservationException(
                command.getReservationId(), 
                "Reservation has expired"
            );
        }
        
        // 2. 재고 차감 (예약 확정)
        product.deductStock(command.getReservationId(), command.getOrderId());
        
        // 3. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 4. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        // 5. 차감 완료 이벤트 발행
        publishStockDeductedEvent(savedProduct, reservation, command.getOrderId());
        
        log.info("Reserved stock deducted successfully. Product: {}, Reservation: {}", 
            product.getProductId(), command.getReservationId());
    }
    
    /**
     * 직접 재고 차감 처리
     */
    private void processDirectDeduction(DeductStockDirectlyCommand command) {
        // 1. 상품 조회
        Product product = loadProductPort.loadProduct(command.getProductId())
            .orElseThrow(() -> new ProductNotFoundException(command.getProductId()));
        
        // 2. 재고 가용성 검증
        stockDomainService.validateStockAvailability(product, command.getQuantity());
        
        // 3. 재고 차감
        product.deductStockDirectly(command.getQuantity(), command.getReason());
        
        // 4. 변경사항 저장
        Product savedProduct = saveProductPort.saveProduct(product);
        
        // 5. 도메인 이벤트 발행
        publishEventPort.publishEvents(savedProduct.pullDomainEvents());
        
        // 6. 차감 완료 이벤트 발행
        publishDirectDeductionEvent(savedProduct, command);
        
        log.info("Stock deducted directly. Product: {}, Quantity: {}", 
            command.getProductId(), command.getQuantity());
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
     * 재고 차감 완료 이벤트 발행
     */
    private void publishStockDeductedEvent(Product product, StockReservation reservation, 
                                         String orderId) {
        StockDeductedEvent event = new StockDeductedEvent(
            product.getProductId().getValue().toString(),
            orderId,
            reservation.getQuantity().getValue(),
            product.getTotalQuantity().getValue(),
            LocalDateTime.now()
        );
        
        publishEventPort.publishEvent(event);
    }
    
    /**
     * 직접 차감 이벤트 발행
     */
    private void publishDirectDeductionEvent(Product product, DeductStockDirectlyCommand command) {
        StockDeductedEvent event = new StockDeductedEvent(
            product.getProductId().getValue().toString(),
            command.getReferenceId() != null ? command.getReferenceId() : "DIRECT_DEDUCTION",
            command.getQuantity().getValue(),
            product.getTotalQuantity().getValue(),
            LocalDateTime.now()
        );
        
        publishEventPort.publishEvent(event);
    }
    
    /**
     * 락 키 생성
     */
    private String generateLockKey(ProductId productId) {
        return String.format("stock:lock:product:%s", productId.getValue());
    }
}