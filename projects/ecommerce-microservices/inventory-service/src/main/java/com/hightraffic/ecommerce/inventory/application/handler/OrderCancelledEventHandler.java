package com.hightraffic.ecommerce.inventory.application.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hightraffic.ecommerce.common.event.inventory.StockReleasedEvent;
import com.hightraffic.ecommerce.common.event.order.OrderCancelledEvent;
import com.hightraffic.ecommerce.inventory.application.port.in.RestoreStockUseCase;
import com.hightraffic.ecommerce.inventory.application.port.out.LoadProductsByConditionPort;
import com.hightraffic.ecommerce.inventory.application.port.out.PublishEventPort;
import com.hightraffic.ecommerce.inventory.domain.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 취소 이벤트 핸들러
 * 
 * 주문이 취소되면 해당 주문에 대한 재고 예약을 해제하고
 * 재고를 복원합니다.
 */
@Component
public class OrderCancelledEventHandler {
    
    private static final Logger log = LoggerFactory.getLogger(OrderCancelledEventHandler.class);
    
    private final RestoreStockUseCase restoreStockUseCase;
    private final LoadProductsByConditionPort loadProductsByConditionPort;
    private final PublishEventPort publishEventPort;
    private final ObjectMapper objectMapper;
    
    public OrderCancelledEventHandler(RestoreStockUseCase restoreStockUseCase,
                                    LoadProductsByConditionPort loadProductsByConditionPort,
                                    PublishEventPort publishEventPort,
                                    ObjectMapper objectMapper) {
        this.restoreStockUseCase = restoreStockUseCase;
        this.loadProductsByConditionPort = loadProductsByConditionPort;
        this.publishEventPort = publishEventPort;
        this.objectMapper = objectMapper;
    }
    
    /**
     * 주문 취소 이벤트 처리
     * 
     * @param event 주문 취소 이벤트
     */
    @Transactional
    public void handle(OrderCancelledEvent event) {
        log.info("주문 취소 이벤트 처리 시작: orderId={}, customerId={}, reason={}, cancelledBy={}({})",
                event.getOrderId(), event.getCustomerId(), event.getCancelReason(),
                event.getCancelledBy(), event.getCancelledByType());
        
        try {
            // 보상 액션에서 재고 복원 정보 추출
            List<StockRestoreInfo> stockRestoreInfos = extractStockRestoreInfo(event);
            
            if (stockRestoreInfos.isEmpty()) {
                log.warn("주문 취소 이벤트에 재고 복원 정보가 없습니다: orderId={}", event.getOrderId());
                // 주문 ID로 예약 조회 시도
                handleByOrderId(event);
                return;
            }
            
            // 재고 복원 실행
            List<StockReleasedEvent.ReleasedItem> releasedItems = new ArrayList<>();
            
            for (StockRestoreInfo restoreInfo : stockRestoreInfos) {
                try {
                    // 예약 해제 명령 생성
                    RestoreStockUseCase.ReleaseReservationCommand command = 
                        new RestoreStockUseCase.ReleaseReservationCommand(
                            com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId.of(restoreInfo.productId),
                            restoreInfo.reservationId
                        );
                    
                    // 예약 해제 실행
                    RestoreStockUseCase.ReleaseReservationResult result = 
                        restoreStockUseCase.releaseReservation(command);
                    
                    log.info("재고 예약 해제 성공: productId={}, reservationId={}, releasedQuantity={}",
                            result.productId(), result.reservationId(), result.releasedQuantity());
                    
                    // 해제된 항목 정보 수집
                    releasedItems.add(createReleasedItem(result, restoreInfo));
                    
                } catch (Exception e) {
                    log.error("재고 예약 해제 실패: productId={}, reservationId={}",
                            restoreInfo.productId, restoreInfo.reservationId, e);
                    // 실패한 항목도 이벤트에 포함 (notes에 오류 기록)
                    releasedItems.add(createFailedReleasedItem(restoreInfo, e));
                }
            }
            
            // 재고 해제 이벤트 발행
            if (!releasedItems.isEmpty()) {
                publishStockReleasedEvent(event, releasedItems);
            }
            
        } catch (Exception e) {
            log.error("주문 취소 이벤트 처리 중 오류 발생: orderId={}", event.getOrderId(), e);
            throw new RuntimeException("주문 취소 이벤트 처리 실패", e);
        }
    }
    
    /**
     * 주문 ID로 예약 조회 및 해제
     */
    private void handleByOrderId(OrderCancelledEvent event) {
        try {
            // 배치 예약 해제 명령 생성
            RestoreStockUseCase.BatchReleaseReservationCommand command = 
                new RestoreStockUseCase.BatchReleaseReservationCommand(
                    event.getOrderId()
                );
            
            // 배치 예약 해제 실행
            RestoreStockUseCase.BatchReleaseResult result = 
                restoreStockUseCase.batchReleaseReservations(command);
            
            if (result.totalReleased() > 0) {
                log.info("주문 ID로 재고 예약 해제 성공: orderId={}, releasedCount={}",
                        event.getOrderId(), result.totalReleased());
                
                // 해제된 항목들로 이벤트 생성
                List<StockReleasedEvent.ReleasedItem> releasedItems = 
                    result.releaseResults().stream()
                        .map(this::createReleasedItemFromResult)
                        .collect(Collectors.toList());
                
                publishStockReleasedEvent(event, releasedItems);
            } else {
                log.warn("주문 ID로 해제할 예약을 찾을 수 없습니다: orderId={}", event.getOrderId());
            }
            
        } catch (Exception e) {
            log.error("주문 ID로 재고 예약 해제 중 오류: orderId={}", event.getOrderId(), e);
        }
    }
    
    /**
     * 보상 액션에서 재고 복원 정보 추출
     */
    private List<StockRestoreInfo> extractStockRestoreInfo(OrderCancelledEvent event) {
        List<StockRestoreInfo> restoreInfos = new ArrayList<>();
        
        for (OrderCancelledEvent.CompensationAction action : event.getCompensationActions()) {
            if ("STOCK_RESTORE".equals(action.getActionType()) && 
                "inventory-service".equals(action.getTargetService())) {
                
                try {
                    // actionData는 JSON 형식으로 저장된 복원 정보
                    StockRestoreData data = objectMapper.readValue(
                        action.getActionData(), StockRestoreData.class);
                    
                    restoreInfos.addAll(data.items.stream()
                        .map(item -> new StockRestoreInfo(
                            item.productId,
                            item.productName,
                            item.reservationId,
                            item.quantity
                        ))
                        .collect(Collectors.toList()));
                    
                } catch (JsonProcessingException e) {
                    log.error("재고 복원 정보 파싱 실패: actionData={}", action.getActionData(), e);
                }
            }
        }
        
        return restoreInfos;
    }
    
    /**
     * 해제된 항목 정보 생성
     */
    private StockReleasedEvent.ReleasedItem createReleasedItem(
            RestoreStockUseCase.ReleaseReservationResult result,
            StockRestoreInfo restoreInfo) {
        
        return new StockReleasedEvent.ReleasedItem(
            result.productId(),
            restoreInfo.productName != null ? restoreInfo.productName : "Unknown Product",
            result.releasedQuantity().intValue(),
            "MAIN", // 기본 창고
            "AVAILABLE", // 가용 재고로 복원
            result.availableQuantity().intValue(),
            "주문 취소로 인한 재고 복원"
        );
    }
    
    /**
     * 실패한 해제 항목 정보 생성
     */
    private StockReleasedEvent.ReleasedItem createFailedReleasedItem(
            StockRestoreInfo restoreInfo, Exception e) {
        
        return new StockReleasedEvent.ReleasedItem(
            restoreInfo.productId,
            restoreInfo.productName != null ? restoreInfo.productName : "Unknown Product",
            0, // 해제 실패
            "MAIN",
            "FAILED",
            0,
            "재고 복원 실패: " + e.getMessage()
        );
    }
    
    /**
     * 배치 해제 결과로부터 해제 항목 생성
     */
    private StockReleasedEvent.ReleasedItem createReleasedItemFromResult(
            RestoreStockUseCase.ReleaseReservationResult result) {
        
        return new StockReleasedEvent.ReleasedItem(
            result.productId(),
            "Unknown Product", // 배치 해제 시 상품명 정보 없음
            result.releasedQuantity().intValue(),
            "MAIN",
            "AVAILABLE",
            result.availableQuantity().intValue(),
            "주문 취소로 인한 재고 복원"
        );
    }
    
    /**
     * 재고 해제 이벤트 발행
     */
    private void publishStockReleasedEvent(OrderCancelledEvent orderEvent,
                                         List<StockReleasedEvent.ReleasedItem> releasedItems) {
        
        // 첫 번째 상품의 ID를 aggregate ID로 사용 (또는 "BATCH" 사용)
        String inventoryId = releasedItems.isEmpty() ? "BATCH" : releasedItems.get(0).getProductId();
        
        // 이벤트 생성
        StockReleasedEvent event = new StockReleasedEvent(
            inventoryId,
            orderEvent.getOrderId() + "-CANCEL", // 예약 ID 대신 주문ID-CANCEL 사용
            orderEvent.getOrderId(),
            "ORDER_CANCELLED",
            orderEvent.getCancelReasonCode(),
            releasedItems,
            orderEvent.getCancelledBy(),
            orderEvent.getCancelledByType(),
            false, // 주문 취소는 추가 보상 불필요
            Instant.now().minusSeconds(1800) // 예약 시간 추정 (30분 전)
        );
        
        // 이벤트 발행
        publishEventPort.publishEvent(event);
        
        log.info("재고 해제 이벤트 발행: orderId={}, releasedCount={}",
                orderEvent.getOrderId(), releasedItems.size());
    }
    
    /**
     * 재고 복원 정보
     */
    private static class StockRestoreInfo {
        final String productId;
        final String productName;
        final String reservationId;
        final BigDecimal quantity;
        
        StockRestoreInfo(String productId, String productName, 
                        String reservationId, BigDecimal quantity) {
            this.productId = productId;
            this.productName = productName;
            this.reservationId = reservationId;
            this.quantity = quantity;
        }
    }
    
    /**
     * 재고 복원 데이터 (JSON 파싱용)
     */
    private static class StockRestoreData {
        public List<StockRestoreItem> items;
        
        public static class StockRestoreItem {
            public String productId;
            public String productName;
            public String reservationId;
            public BigDecimal quantity;
        }
    }
}