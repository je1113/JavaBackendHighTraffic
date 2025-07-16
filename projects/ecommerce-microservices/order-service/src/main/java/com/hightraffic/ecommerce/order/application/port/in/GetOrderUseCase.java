package com.hightraffic.ecommerce.order.application.port.in;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderId;
import com.hightraffic.ecommerce.order.domain.model.vo.OrderStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 조회 Use Case
 * 
 * 책임:
 * - 단일 주문 조회
 * - 고객별 주문 목록 조회
 * - 주문 상태별 필터링
 * - 페이징 처리
 * 
 * Query 전용 Use Case로 도메인 상태를 변경하지 않음
 */
public interface GetOrderUseCase {
    
    /**
     * 주문 ID로 단일 주문 조회
     * 
     * @param query 주문 조회 쿼리
     * @return 주문 정보
     * @throws OrderNotFoundException 주문을 찾을 수 없는 경우
     */
    OrderResponse getOrder(@Valid GetOrderQuery query);
    
    /**
     * 고객 ID로 주문 목록 조회
     * 
     * @param query 고객 주문 목록 조회 쿼리
     * @return 주문 목록
     */
    OrderListResponse getOrdersByCustomer(@Valid GetOrdersByCustomerQuery query);
    
    /**
     * 단일 주문 조회 쿼리
     */
    class GetOrderQuery {
        
        @NotNull(message = "Order ID is required")
        private final OrderId orderId;
        
        public GetOrderQuery(OrderId orderId) {
            this.orderId = orderId;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
    }
    
    /**
     * 고객별 주문 목록 조회 쿼리
     */
    class GetOrdersByCustomerQuery {
        
        @NotNull(message = "Customer ID is required")
        private final CustomerId customerId;
        
        private final OrderStatus statusFilter;
        private final LocalDateTime fromDate;
        private final LocalDateTime toDate;
        private final int page;
        private final int size;
        
        public GetOrdersByCustomerQuery(CustomerId customerId, OrderStatus statusFilter, 
                                       LocalDateTime fromDate, LocalDateTime toDate, 
                                       int page, int size) {
            this.customerId = customerId;
            this.statusFilter = statusFilter;
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.page = Math.max(0, page);
            this.size = Math.min(Math.max(1, size), 100); // 최대 100개
        }
        
        public CustomerId getCustomerId() {
            return customerId;
        }
        
        public OrderStatus getStatusFilter() {
            return statusFilter;
        }
        
        public LocalDateTime getFromDate() {
            return fromDate;
        }
        
        public LocalDateTime getToDate() {
            return toDate;
        }
        
        public int getPage() {
            return page;
        }
        
        public int getSize() {
            return size;
        }
    }
    
    /**
     * 주문 조회 응답
     * 
     * 도메인 모델을 직접 노출하지 않고 DTO로 변환하여 반환
     */
    class OrderResponse {
        private final OrderId orderId;
        private final CustomerId customerId;
        private final OrderStatus status;
        private final String totalAmount;
        private final List<OrderItemResponse> items;
        private final LocalDateTime createdAt;
        private final LocalDateTime lastModifiedAt;
        private final String cancellationReason;
        
        public OrderResponse(Order order) {
            this.orderId = order.getOrderId();
            this.customerId = order.getCustomerId();
            this.status = order.getStatus();
            this.totalAmount = order.getTotalAmount().toString();
            this.items = order.getItems().stream()
                .map(OrderItemResponse::new)
                .toList();
            this.createdAt = order.getCreatedAt();
            this.lastModifiedAt = order.getLastModifiedAt();
            this.cancellationReason = order.getCancellationReason();
        }
        
        // Getters
        public OrderId getOrderId() { return orderId; }
        public CustomerId getCustomerId() { return customerId; }
        public OrderStatus getStatus() { return status; }
        public String getTotalAmount() { return totalAmount; }
        public List<OrderItemResponse> getItems() { return items; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public LocalDateTime getLastModifiedAt() { return lastModifiedAt; }
        public String getCancellationReason() { return cancellationReason; }
        
        /**
         * 주문 항목 응답
         */
        public static class OrderItemResponse {
            private final String productId;
            private final String productName;
            private final Integer quantity;
            private final String unitPrice;
            private final String totalPrice;
            
            public OrderItemResponse(com.hightraffic.ecommerce.order.domain.model.OrderItem item) {
                this.productId = item.getProductId().getValue().toString();
                this.productName = item.getProductName();
                this.quantity = item.getQuantity();
                this.unitPrice = item.getUnitPrice().toString();
                this.totalPrice = item.getTotalPrice().toString();
            }
            
            // Getters
            public String getProductId() { return productId; }
            public String getProductName() { return productName; }
            public Integer getQuantity() { return quantity; }
            public String getUnitPrice() { return unitPrice; }
            public String getTotalPrice() { return totalPrice; }
        }
    }
    
    /**
     * 주문 상세 정보 (OrderResponse 별칭)
     */
    class OrderDetail extends OrderResponse {
        public OrderDetail(Order order) {
            super(order);
        }
    }
    
    /**
     * 주문 목록 조회 응답
     */
    class OrderListResponse {
        private final List<OrderSummary> orders;
        private final int totalElements;
        private final int totalPages;
        private final int currentPage;
        
        public OrderListResponse(List<Order> orders, int totalElements, int totalPages, int currentPage) {
            this.orders = orders.stream()
                .map(OrderSummary::new)
                .toList();
            this.totalElements = totalElements;
            this.totalPages = totalPages;
            this.currentPage = currentPage;
        }
        
        // Getters
        public List<OrderSummary> getOrders() { return orders; }
        public int getTotalElements() { return totalElements; }
        public int getTotalPages() { return totalPages; }
        public int getCurrentPage() { return currentPage; }
        
        /**
         * 주문 요약 정보
         */
        public static class OrderSummary {
            private final OrderId orderId;
            private final OrderStatus status;
            private final String totalAmount;
            private final int itemCount;
            private final LocalDateTime createdAt;
            
            public OrderSummary(Order order) {
                this.orderId = order.getOrderId();
                this.status = order.getStatus();
                this.totalAmount = order.getTotalAmount().toString();
                this.itemCount = order.getItemCount();
                this.createdAt = order.getCreatedAt();
            }
            
            // Getters
            public OrderId getOrderId() { return orderId; }
            public OrderStatus getStatus() { return status; }
            public String getTotalAmount() { return totalAmount; }
            public int getItemCount() { return itemCount; }
            public LocalDateTime getCreatedAt() { return createdAt; }
        }
    }
    
    /**
     * 주문을 찾을 수 없는 경우 발생하는 예외
     */
    class OrderNotFoundException extends RuntimeException {
        private final OrderId orderId;
        
        public OrderNotFoundException(OrderId orderId) {
            super("Order not found: " + orderId);
            this.orderId = orderId;
        }
        
        public OrderId getOrderId() {
            return orderId;
        }
    }
}