package com.hightraffic.ecommerce.order.adapter.in.web;

import com.hightraffic.ecommerce.order.adapter.in.web.dto.*;
import com.hightraffic.ecommerce.order.application.port.in.*;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.OrderItem;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 REST API 컨트롤러
 * 
 * 헥사고날 아키텍처의 Inbound Adapter로서
 * HTTP 요청을 Application Service로 전달하는 역할
 */
@Tag(name = "Order API", description = "주문 관리 API")
@RestController
@RequestMapping("/api/v1/orders")
@Validated
public class OrderController {
    
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);
    
    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    
    public OrderController(CreateOrderUseCase createOrderUseCase,
                         GetOrderUseCase getOrderUseCase,
                         ConfirmOrderUseCase confirmOrderUseCase,
                         CancelOrderUseCase cancelOrderUseCase) {
        this.createOrderUseCase = createOrderUseCase;
        this.getOrderUseCase = getOrderUseCase;
        this.confirmOrderUseCase = confirmOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }
    
    /**
     * 주문 생성
     */
    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청", 
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "재고 부족",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        
        log.info("주문 생성 요청: customerId={}, items={}", 
                request.customerId(), request.orderItems().size());
        
        // Request DTO → UseCase Command 변환
        CreateOrderUseCase.CreateOrderCommand command = mapToCommand(request);
        
        // UseCase 실행
        CreateOrderUseCase.CreateOrderResult result = createOrderUseCase.createOrder(command);
        
        // Result → Response DTO 변환
        CreateOrderResponse response = mapToResponse(result);
        
        log.info("주문 생성 성공: orderId={}, status={}", 
                response.orderId(), response.status());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * 주문 조회
     */
    @Operation(summary = "주문 조회", description = "주문 ID로 주문을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<GetOrderResponse> getOrder(
            @Parameter(description = "주문 ID", required = true)
            @PathVariable String orderId) {
        
        log.debug("주문 조회 요청: orderId={}", orderId);
        
        // UseCase 실행
        GetOrderUseCase.GetOrderQuery query = new GetOrderUseCase.GetOrderQuery(orderId);
        GetOrderUseCase.OrderDetail result = getOrderUseCase.getOrder(query);
        
        // Result → Response DTO 변환
        GetOrderResponse response = mapToResponse(result);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 고객별 주문 목록 조회
     */
    @Operation(summary = "고객 주문 목록 조회", description = "고객 ID로 주문 목록을 조회합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<OrderListResponse> getCustomerOrders(
            @Parameter(description = "고객 ID", required = true)
            @PathVariable String customerId,
            @Parameter(description = "주문 상태 필터")
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        log.debug("고객 주문 목록 조회: customerId={}, status={}, page={}", 
                customerId, status, pageable.getPageNumber());
        
        // UseCase 실행
        GetOrderUseCase.GetCustomerOrdersQuery query = 
            new GetOrderUseCase.GetCustomerOrdersQuery(customerId, status, pageable);
        Page<GetOrderUseCase.OrderSummary> result = getOrderUseCase.getCustomerOrders(query);
        
        // Result → Response DTO 변환
        OrderListResponse response = mapToListResponse(result);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 주문 확정
     */
    @Operation(summary = "주문 확정", description = "결제 완료된 주문을 확정합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 확정 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 상태 전이",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Void> confirmOrder(
            @Parameter(description = "주문 ID", required = true)
            @PathVariable String orderId) {
        
        log.info("주문 확정 요청: orderId={}", orderId);
        
        // UseCase 실행
        ConfirmOrderUseCase.ConfirmOrderCommand command = 
            new ConfirmOrderUseCase.ConfirmOrderCommand(orderId);
        confirmOrderUseCase.confirmOrder(command);
        
        log.info("주문 확정 성공: orderId={}", orderId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 주문 취소
     */
    @Operation(summary = "주문 취소", description = "주문을 취소합니다")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "주문 취소 성공"),
        @ApiResponse(responseCode = "400", description = "취소 불가능한 상태",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "주문을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @Parameter(description = "주문 ID", required = true)
            @PathVariable String orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        
        log.info("주문 취소 요청: orderId={}, reason={}", orderId, request.cancelReason());
        
        // UseCase 실행
        CancelOrderUseCase.CancelOrderCommand command = new CancelOrderUseCase.CancelOrderCommand(
            orderId,
            request.cancelReason(),
            request.cancelReasonCode()
        );
        cancelOrderUseCase.cancelOrder(command);
        
        log.info("주문 취소 성공: orderId={}", orderId);
        
        return ResponseEntity.ok().build();
    }
    
    // === Private Mapping Methods ===
    
    private CreateOrderUseCase.CreateOrderCommand mapToCommand(CreateOrderRequest request) {
        // 주문 항목 변환
        List<CreateOrderUseCase.OrderItemCommand> items = request.orderItems().stream()
            .map(item -> new CreateOrderUseCase.OrderItemCommand(
                item.productId(),
                item.quantity(),
                new Money(item.unitPrice(), "KRW")
            ))
            .collect(Collectors.toList());
        
        return new CreateOrderUseCase.CreateOrderCommand(
            request.customerId(),
            items
        );
    }
    
    private CreateOrderResponse mapToResponse(CreateOrderUseCase.CreateOrderResult result) {
        return CreateOrderResponse.success(
            result.orderId(),
            result.orderNumber(),
            result.customerId(),
            result.status(),
            result.totalAmount().getAmount(),
            result.totalAmount().getCurrency(),
            result.createdAt(),
            result.createdAt().plusDays(3), // 예상 배송일
            null // 결제 URL은 별도 서비스에서 처리
        );
    }
    
    private GetOrderResponse mapToResponse(GetOrderUseCase.OrderDetail detail) {
        // 주문 항목 변환
        List<GetOrderResponse.OrderItemResponse> items = detail.orderItems().stream()
            .map(item -> GetOrderResponse.OrderItemResponse.from(
                item.productId(),
                item.productName(),
                item.quantity(),
                item.unitPrice()
            ))
            .collect(Collectors.toList());
        
        // 배송 주소 변환 (간단한 예시)
        GetOrderResponse.ShippingAddressResponse shippingAddress = 
            new GetOrderResponse.ShippingAddressResponse(
                "수령인",
                "010-1234-5678",
                "12345",
                "서울시 강남구",
                "상세주소",
                "배송 메모"
            );
        
        // 결제 정보 변환
        GetOrderResponse.PaymentResponse payment = 
            GetOrderResponse.PaymentResponse.pending("CARD");
        
        return new GetOrderResponse(
            detail.orderId(),
            detail.orderNumber(),
            detail.customerId(),
            detail.status(),
            detail.statusDescription(),
            items,
            detail.totalAmount(),
            "KRW",
            shippingAddress,
            payment,
            detail.createdAt(),
            detail.updatedAt(),
            detail.confirmedAt(),
            detail.cancelledAt(),
            null, // deliveredAt
            detail.createdAt().plusDays(3), // estimatedDeliveryDate
            null, // trackingNumber
            null, // orderNotes
            detail.cancelReason()
        );
    }
    
    private OrderListResponse mapToListResponse(Page<GetOrderUseCase.OrderSummary> page) {
        List<OrderListResponse.OrderSummary> orders = page.getContent().stream()
            .map(summary -> OrderListResponse.OrderSummary.create(
                summary.orderId(),
                summary.orderNumber(),
                summary.status(),
                summary.totalAmount(),
                summary.itemCount(),
                summary.firstProductName(),
                summary.createdAt(),
                null // deliveredAt
            ))
            .collect(Collectors.toList());
        
        return new OrderListResponse(
            orders,
            page.getTotalElements(),
            page.getTotalPages(),
            page.getNumber(),
            page.getSize(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}