package com.hightraffic.ecommerce.inventory.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hightraffic.ecommerce.inventory.adapter.in.web.dto.*;
import com.hightraffic.ecommerce.inventory.application.port.in.*;
import com.hightraffic.ecommerce.inventory.domain.model.vo.ProductId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재고 관리 REST API 컨트롤러
 * 
 * 헥사고날 아키텍처의 Inbound Adapter로서
 * HTTP 요청을 Application Service로 전달하는 역할
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Validated
public class InventoryController {
    
    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);
    
    private final ReserveStockUseCase reserveStockUseCase;
    private final DeductStockUseCase deductStockUseCase;
    private final RestoreStockUseCase restoreStockUseCase;
    private final GetStockUseCase getStockUseCase;
    
    public InventoryController(ReserveStockUseCase reserveStockUseCase,
                             DeductStockUseCase deductStockUseCase,
                             RestoreStockUseCase restoreStockUseCase,
                             GetStockUseCase getStockUseCase) {
        this.reserveStockUseCase = reserveStockUseCase;
        this.deductStockUseCase = deductStockUseCase;
        this.restoreStockUseCase = restoreStockUseCase;
        this.getStockUseCase = getStockUseCase;
    }
    
    /**
     * 재고 조회
     */
    @GetMapping("/products/{productId}/stock")
    public ResponseEntity<GetStockResponse> getStock(
            @PathVariable @NotBlank @Size(max = 50) String productId) {
        
        log.debug("재고 조회 요청: productId={}", productId);
        
        // UseCase 실행
        GetStockUseCase.GetStockQuery query = 
            new GetStockUseCase.GetStockQuery(ProductId.of(productId), true);
        GetStockUseCase.StockResponse result = getStockUseCase.getStock(query);
        
        // Result → Response DTO 변환
        GetStockResponse response = mapToResponse(result);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 배치 재고 조회
     */
    @Operation(summary = "배치 재고 조회", description = "여러 상품의 재고를 한번에 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @PostMapping("/products/stock/batch")
    public ResponseEntity<GetStockResponse.BatchGetStockResponse> getBatchStock(
            @RequestBody @Valid BatchStockQuery query) {
        
        log.debug("배치 재고 조회 요청: productIds={}", query.productIds().size());
        
        // UseCase 실행
        List<ProductId> productIdList = query.productIds().stream()
            .map(ProductId::of)
            .collect(Collectors.toList());
        GetStockUseCase.GetBatchStockQuery useCaseQuery = 
            new GetStockUseCase.GetBatchStockQuery(productIdList, false);
        List<GetStockUseCase.StockResponse> results = getStockUseCase.getBatchStock(useCaseQuery);
        
        // Results → Response DTO 변환
        GetStockResponse.BatchGetStockResponse response = mapToBatchResponse(results);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 재고 예약
     */
    @Operation(summary = "재고 예약", description = "재고를 예약합니다 (2PC Phase 1)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "예약 성공"),
        @ApiResponse(responseCode = "409", description = "재고 부족",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/products/{productId}/reservations")
    public ResponseEntity<ReserveStockResponse> reserveStock(
            @Parameter(description = "상품 ID", required = true)
            @PathVariable @NotBlank String productId,
            @Valid @RequestBody ReserveStockRequest request) {
        
        log.info("재고 예약 요청: productId={}, quantity={}, reservationId={}",
                productId, request.quantity(), request.reservationId());
        
        // Request DTO → UseCase Command 변환
        ReserveStockUseCase.ReserveStockCommand command = 
            new ReserveStockUseCase.ReserveStockCommand(
                ProductId.of(productId),
                request.quantity(),
                request.reservationId(),
                Duration.ofMinutes(request.timeoutMinutes() != null ? request.timeoutMinutes() : 30)
            );
        
        // UseCase 실행
        ReserveStockUseCase.ReservationResult result = reserveStockUseCase.reserveStock(command);
        
        // Result → Response DTO 변환
        ReserveStockResponse response = ReserveStockResponse.success(
            result.reservationId(),
            result.productId(),
            result.reservedQuantity(),
            result.availableQuantity(),
            result.expiresAt()
        );
        
        log.info("재고 예약 성공: reservationId={}", result.reservationId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * 배치 재고 예약
     */
    @Operation(summary = "배치 재고 예약", description = "여러 상품의 재고를 한번에 예약합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "예약 처리 완료"),
        @ApiResponse(responseCode = "207", description = "부분 성공")
    })
    @PostMapping("/reservations/batch")
    public ResponseEntity<ReserveStockResponse.BatchReserveStockResponse> batchReserveStock(
            @Valid @RequestBody ReserveStockRequest.BatchReserveStockRequest request) {
        
        log.info("배치 재고 예약 요청: reservationId={}, items={}",
                request.reservationId(), request.items().size());
        
        // Request DTO → UseCase Command 변환
        List<ReserveStockUseCase.ReserveStockCommand.ReservationItem> items = 
            request.items().stream()
                .map(item -> new ReserveStockUseCase.ReserveStockCommand.ReservationItem(
                    ProductId.of(item.productId()),
                    item.quantity()
                ))
                .collect(Collectors.toList());
        
        ReserveStockUseCase.BatchReserveStockCommand command = 
            new ReserveStockUseCase.BatchReserveStockCommand(
                request.reservationId(),
                items,
                Duration.ofMinutes(request.timeoutMinutes() != null ? request.timeoutMinutes() : 30)
            );
        
        // UseCase 실행
        ReserveStockUseCase.BatchReservationResult result = 
            reserveStockUseCase.reserveStockBatch(command);
        
        // Result → Response DTO 변환
        ReserveStockResponse.BatchReserveStockResponse response = mapToBatchResponse(result);
        
        // 상태 코드 결정
        HttpStatus status = result.isFullySuccessful() ? HttpStatus.CREATED : 
                          result.failedReservations().isEmpty() ? HttpStatus.CREATED : 
                          HttpStatus.MULTI_STATUS;
        
        return ResponseEntity.status(status).body(response);
    }
    
    /**
     * 재고 차감 (예약 확정)
     */
    @Operation(summary = "재고 차감", description = "예약된 재고를 실제로 차감합니다 (2PC Phase 2)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "차감 성공"),
        @ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/products/{productId}/reservations/{reservationId}/deduct")
    public ResponseEntity<Void> deductStock(
            @Parameter(description = "상품 ID", required = true)
            @PathVariable String productId,
            @Parameter(description = "예약 ID", required = true)
            @PathVariable String reservationId) {
        
        log.info("재고 차감 요청: productId={}, reservationId={}", productId, reservationId);
        
        // UseCase 실행
        DeductStockUseCase.DeductReservedStockCommand command = 
            new DeductStockUseCase.DeductReservedStockCommand(ProductId.of(productId), reservationId);
        deductStockUseCase.deductReservedStock(command);
        
        log.info("재고 차감 성공: reservationId={}", reservationId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 예약 해제
     */
    @Operation(summary = "예약 해제", description = "예약된 재고를 해제합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "해제 성공"),
        @ApiResponse(responseCode = "404", description = "예약을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/products/{productId}/reservations/{reservationId}")
    public ResponseEntity<Void> releaseReservation(
            @Parameter(description = "상품 ID", required = true)
            @PathVariable String productId,
            @Parameter(description = "예약 ID", required = true)
            @PathVariable String reservationId) {
        
        log.info("예약 해제 요청: productId={}, reservationId={}", productId, reservationId);
        
        // UseCase 실행
        RestoreStockUseCase.ReleaseReservationCommand command = 
            new RestoreStockUseCase.ReleaseReservationCommand(ProductId.of(productId), reservationId);
        restoreStockUseCase.releaseReservation(command);
        
        log.info("예약 해제 성공: reservationId={}", reservationId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 재고 조정
     */
    @Operation(summary = "재고 조정", description = "재고를 수동으로 조정합니다 (관리자 전용)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/products/{productId}/adjustments")
    public ResponseEntity<Void> adjustStock(
            @Parameter(description = "상품 ID", required = true)
            @PathVariable String productId,
            @Valid @RequestBody AdjustStockRequest request) {
        
        log.info("재고 조정 요청: productId={}, type={}, quantity={}, reason={}",
                productId, request.adjustmentType(), request.quantity(), request.reason());
        
        // 조정 타입에 따른 처리
        switch (request.adjustmentType()) {
            case "ADD" -> {
                RestoreStockUseCase.AddStockCommand command = 
                    new RestoreStockUseCase.AddStockCommand(
                        ProductId.of(productId),
                        request.quantity(),
                        request.reason(),
                        request.reasonCode()
                    );
                restoreStockUseCase.addStock(command);
            }
            case "SUBTRACT" -> {
                DeductStockUseCase.DeductStockCommand command = 
                    new DeductStockUseCase.DeductStockCommand(
                        ProductId.of(productId),
                        request.quantity()
                    );
                deductStockUseCase.deductStock(command);
            }
            case "SET" -> {
                RestoreStockUseCase.AdjustStockCommand command = 
                    new RestoreStockUseCase.AdjustStockCommand(
                        ProductId.of(productId),
                        request.quantity(),
                        request.reason(),
                        request.reasonCode()
                    );
                restoreStockUseCase.adjustStock(command);
            }
        }
        
        log.info("재고 조정 성공: productId={}", productId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 낮은 재고 상품 조회
     */
    @Operation(summary = "낮은 재고 상품 조회", description = "재고가 임계값 이하인 상품을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/products/low-stock")
    public ResponseEntity<List<GetStockResponse>> getLowStockProducts(
            @Parameter(description = "임계값 (기본: 10)")
            @RequestParam(defaultValue = "10") int threshold) {
        
        log.debug("낮은 재고 상품 조회: threshold={}", threshold);
        
        // UseCase 실행
        GetStockUseCase.GetLowStockQuery query = 
            new GetStockUseCase.GetLowStockQuery(StockQuantity.of(threshold), false, 100);
        List<GetStockUseCase.LowStockResponse> results = getStockUseCase.getLowStockProducts(query);
        
        // Results → Response DTOs 변환
        List<GetStockResponse> responses = results.stream()
            .map(this::mapLowStockToResponse)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(responses);
    }
    
    // === Private Mapping Methods ===
    
    private GetStockResponse mapToResponse(GetStockUseCase.StockResponse info) {
        // 예약 정보 변환 (비어있는 리스트로 처리 - 예약 정보는 따로 조회 해야 함)
        List<GetStockResponse.ReservationInfo> reservations = 
            List.of(); // 빈 리스트
        
        return new GetStockResponse(
            info.getProductId().toString(),
            info.getProductName(),
            info.getTotalQuantity().getValue(),
            info.getAvailableQuantity().getValue(),
            info.getReservedQuantity().getValue(),
            info.getLowStockThreshold().getValue(),
            info.isLowStock(),
            info.isOutOfStock(),
            reservations,
            info.getLastModifiedAt()
        );
    }
    
    private GetStockResponse mapLowStockToResponse(GetStockUseCase.LowStockResponse lowStockInfo) {
        // 낮은 재고 응답을 일반 재고 응답으로 변환
        List<GetStockResponse.ReservationInfo> reservations = List.of();
        
        return new GetStockResponse(
            lowStockInfo.getProductId().toString(),
            lowStockInfo.getProductName(),
            lowStockInfo.getAvailableQuantity().getValue(), // total은 available과 같다고 가정
            lowStockInfo.getAvailableQuantity().getValue(),
            0, // reserved quantity 정보가 없으므로 0으로 처리
            lowStockInfo.getLowStockThreshold().getValue(),
            true, // 낮은 재고 상품이므로 true
            lowStockInfo.getAvailableQuantity().isZero(),
            reservations,
            java.time.LocalDateTime.now() // 현재 시간으로 설정
        );
    }
    
    private GetStockResponse.BatchGetStockResponse mapToBatchResponse(
            List<GetStockUseCase.StockResponse> results) {
        
        List<GetStockResponse.BatchGetStockResponse.ProductStock> products = 
            results.stream()
                .map(info -> GetStockResponse.BatchGetStockResponse.ProductStock.of(
                    info.getProductId().toString(),
                    info.getProductName(),
                    info.getAvailableQuantity().getValue(),
                    info.getReservedQuantity().getValue()
                ))
                .collect(Collectors.toList());
        
        return new GetStockResponse.BatchGetStockResponse(
            products,
            products.size(),
            java.time.LocalDateTime.now()
        );
    }
    
    private ReserveStockResponse.BatchReserveStockResponse mapToBatchResponse(
            ReserveStockUseCase.BatchReservationResult result) {
        
        // 성공 결과 변환
        List<ReserveStockResponse.BatchReserveStockResponse.ReservationResult> results = 
            result.successfulReservations().stream()
                .map(res -> ReserveStockResponse.BatchReserveStockResponse.ReservationResult.success(
                    res.productId(),
                    res.reservedQuantity(),
                    res.availableQuantity()
                ))
                .collect(Collectors.toList());
        
        // 실패 결과 추가
        results.addAll(
            result.failedReservations().stream()
                .map(fail -> ReserveStockResponse.BatchReserveStockResponse.ReservationResult.failed(
                    fail.productId(),
                    fail.reason()
                ))
                .collect(Collectors.toList())
        );
        
        String status = result.isFullySuccessful() ? "SUCCESS" : 
                       result.failedReservations().isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS";
        
        String message = result.isFullySuccessful() ? "모든 재고 예약이 성공했습니다" :
                        String.format("%d개 성공, %d개 실패", 
                            result.successfulReservations().size(),
                            result.failedReservations().size());
        
        return new ReserveStockResponse.BatchReserveStockResponse(
            result.reservationId(),
            result.successfulReservations().size(),
            result.failedReservations().size(),
            results,
            status,
            message
        );
    }
    
    /**
     * 배치 재고 조회 요청 DTO
     */
    public record BatchStockQuery(
        @JsonProperty("productIds")
        @NotEmpty(message = "상품 ID 목록은 필수입니다")
        @Size(max = 100, message = "최대 100개까지 조회 가능합니다")
        Set<String> productIds
    ) {}
}