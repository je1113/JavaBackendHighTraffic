package com.hightraffic.ecommerce.order.adapter.in.web;

import com.hightraffic.ecommerce.order.adapter.in.web.dto.*;
import com.hightraffic.ecommerce.order.application.port.in.*;
import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.OrderItem;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.ProductId;
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
    @GetMapping("/{orderId}")
    public ResponseEntity<GetOrderResponse> getOrder(
            @PathVariable String orderId) {
        
        log.debug("주문 조회 요청: orderId={}", orderId);
        
        // UseCase 실행
        GetOrderUseCase.GetOrderQuery query = new GetOrderUseCase.GetOrderQuery(OrderId.of(orderId));
        GetOrderUseCase.OrderResponse result = getOrderUseCase.getOrder(query);
        
        // Result → Response DTO 변환
        GetOrderResponse response = mapToResponse(result);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 고객별 주문 목록 조회
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<OrderListResponse> getCustomerOrders(
            @PathVariable String customerId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) 
            Pageable pageable) {
        
        log.debug("고객 주문 목록 조회: customerId={}, status={}, page={}", 
                customerId, status, pageable.getPageNumber());
        
        // UseCase 실행
        GetOrderUseCase.GetOrdersByCustomerQuery query = 
            new GetOrderUseCase.GetOrdersByCustomerQuery(CustomerId.of(customerId), status, null, null, 
                pageable.getPageNumber(), pageable.getPageSize());
        GetOrderUseCase.OrderListResponse result = getOrderUseCase.getCustomerOrders(query);
        
        // Result → Response DTO 변환
        OrderListResponse response = mapToListResponse(result);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 주문 확정
     */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<Void> confirmOrder(
            @PathVariable String orderId) {
        
        log.info("주문 확정 요청: orderId={}", orderId);
        
        // UseCase 실행
        ConfirmOrderUseCase.ConfirmOrderCommand command = 
            new ConfirmOrderUseCase.ConfirmOrderCommand(OrderId.of(orderId), "관리자 확정");
        confirmOrderUseCase.confirmOrder(command);
        
        log.info("주문 확정 성공: orderId={}", orderId);
        
        return ResponseEntity.ok().build();
    }
    
    /**
     * 주문 취소
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable String orderId,
            @Valid @RequestBody CancelOrderRequest request) {
        
        log.info("주문 취소 요청: orderId={}, reason={}", orderId, request.cancelReason());
        
        // UseCase 실행
        CancelOrderUseCase.CancelOrderCommand command = new CancelOrderUseCase.CancelOrderCommand(
            OrderId.of(orderId),
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
        List<CreateOrderUseCase.OrderItem> items = request.orderItems().stream()
            .map(item -> new CreateOrderUseCase.OrderItem(
                ProductId.of(item.productId()),
                item.productName(),
                item.quantity(),
                new Money(item.unitPrice(), "KRW")
            ))
            .collect(Collectors.toList());
        
        return new CreateOrderUseCase.CreateOrderCommand(
            CustomerId.of(request.customerId()),
            items,
            request.orderNote()
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
    
    private OrderListResponse mapToListResponse(GetOrderUseCase.OrderListResponse response) {
        List<OrderListResponse.OrderSummary> orders = response.getOrders().stream()
            .map(summary -> OrderListResponse.OrderSummary.create(
                summary.getOrderId().getValue(),
                summary.getOrderId().getValue(), // orderNumber
                summary.getStatus(),
                summary.getTotalAmount(),
                summary.getItemCount(),
                "상품", // firstProductName - TODO: 실제 첫 번째 상품명으로 수정
                summary.getCreatedAt(),
                null // deliveredAt
            ))
            .toList();
        
        return new OrderListResponse(
            orders,
            response.getTotalElements(),
            response.getTotalPages(),
            response.getCurrentPage(),
            10, // pageSize - TODO: 실제 페이지 사이즈로 수정
            response.getCurrentPage() < response.getTotalPages() - 1, // hasNext
            response.getCurrentPage() > 0 // hasPrevious
        );
    }
}