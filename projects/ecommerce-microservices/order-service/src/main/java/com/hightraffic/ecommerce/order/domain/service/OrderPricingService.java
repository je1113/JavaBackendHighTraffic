package com.hightraffic.ecommerce.order.domain.service;

import com.hightraffic.ecommerce.order.domain.model.Order;
import com.hightraffic.ecommerce.order.domain.model.OrderItem;
import com.hightraffic.ecommerce.order.domain.model.vo.CustomerId;
import com.hightraffic.ecommerce.order.domain.model.vo.Money;
import com.hightraffic.ecommerce.order.domain.service.OrderPricingPolicy;
import com.hightraffic.ecommerce.order.domain.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 가격 계산 도메인 서비스
 * 할인, 쿠폰, 포인트 등 복잡한 가격 계산 로직을 담당
 * 정책 인터페이스를 통해 가격 정책을 유연하게 관리
 */
public class OrderPricingService {
    
    private final OrderRepository orderRepository;
    private final OrderPricingPolicy pricingPolicy;
    
    public OrderPricingService(OrderRepository orderRepository, OrderPricingPolicy pricingPolicy) {
        this.orderRepository = orderRepository;
        this.pricingPolicy = pricingPolicy;
    }
    
    /**
     * 주문 총액 계산 (모든 할인 및 추가 비용 포함)
     */
    public PricingResult calculateTotalPrice(Order order) {
        Money baseAmount = order.getTotalAmount();
        PricingResult result = new PricingResult(baseAmount);
        
        // 1. 대량 구매 할인 적용
        applyBulkDiscount(order, result);
        
        // 2. VIP 고객 할인 적용
        applyVipDiscount(order, result);
        
        // 3. 충성 고객 할인 적용
        applyLoyaltyDiscount(order, result);
        
        // 4. 주말 할증 적용
        applyWeekendSurcharge(order, result);
        
        // 5. 배송비 계산
        calculateShippingFee(order, result);
        
        return result;
    }
    
    /**
     * 대량 구매 할인 적용
     * 설정된 수량 이상 구매 시 설정된 할인율 적용
     */
    private void applyBulkDiscount(Order order, PricingResult result) {
        Money bulkDiscountAmount = Money.ZERO("KRW");
        int threshold = pricingPolicy.getBulkDiscountThreshold();
        BigDecimal discountRate = pricingPolicy.getBulkDiscountRate();
        
        for (OrderItem item : order.getItems()) {
            if (item.getQuantity() >= threshold) {
                Money itemDiscount = item.getTotalPrice().multiply(discountRate);
                bulkDiscountAmount = bulkDiscountAmount.add(itemDiscount);
            }
        }
        
        if (!bulkDiscountAmount.isZero()) {
            result.addDiscount("대량구매할인", bulkDiscountAmount);
        }
    }
    
    /**
     * VIP 고객 할인 적용
     * 총 구매액이 설정된 금액 이상인 고객에게 설정된 할인율 적용
     */
    private void applyVipDiscount(Order order, PricingResult result) {
        if (isVipCustomer(order.getCustomerId())) {
            Money vipDiscountAmount = order.getTotalAmount()
                .multiply(pricingPolicy.getVipDiscountRate());
            result.addDiscount("VIP할인", vipDiscountAmount);
        }
    }
    
    /**
     * 충성 고객 할인 적용
     * 최근 6개월 내 설정된 횟수 이상 구매한 고객에게 설정된 할인율 적용
     */
    private void applyLoyaltyDiscount(Order order, PricingResult result) {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        long completedOrderCount = orderRepository.countCompletedOrdersByCustomerIdAfter(
            order.getCustomerId(), 
            sixMonthsAgo
        );
        
        if (completedOrderCount >= pricingPolicy.getLoyaltyOrderThreshold()) {
            Money loyaltyDiscountAmount = order.getTotalAmount()
                .multiply(pricingPolicy.getLoyaltyDiscountRate());
            result.addDiscount("단골고객할인", loyaltyDiscountAmount);
        }
    }
    
    /**
     * 주말 할증 적용
     * 주말 주문 시 설정된 할증율 적용
     */
    private void applyWeekendSurcharge(Order order, PricingResult result) {
        if (!pricingPolicy.isEnableWeekendSurcharge()) {
            return;
        }
        
        LocalDate today = LocalDate.now();
        if (today.getDayOfWeek().getValue() >= 6) { // 토요일(6), 일요일(7)
            Money surchargeAmount = order.getTotalAmount()
                .multiply(pricingPolicy.getWeekendSurchargeRate());
            result.addSurcharge("주말할증", surchargeAmount);
        }
    }
    
    /**
     * 배송비 계산
     * 주문 금액에 따른 무료 배송 정책 적용
     */
    private void calculateShippingFee(Order order, PricingResult result) {
        Money freeShippingThreshold = new Money(pricingPolicy.getFreeShippingThreshold(), "KRW");
        Money standardShippingFee = new Money(pricingPolicy.getStandardShippingFee(), "KRW");
        Money expressShippingFee = new Money(pricingPolicy.getExpressShippingFee(), "KRW");
        
        // 기본 배송비 계산
        if (order.getTotalAmount().isLessThan(freeShippingThreshold)) {
            result.setShippingFee(standardShippingFee);
        } else {
            result.setShippingFee(Money.ZERO("KRW"));
        }
        
        // 특급 배송 선택 시 추가 비용
        if (isExpressDelivery(order)) {
            result.setShippingFee(expressShippingFee);
        }
    }
    
    /**
     * 쿠폰 할인 적용
     * 쿠폰 코드에 따른 할인 적용
     */
    public void applyCouponDiscount(Order order, String couponCode, PricingResult result) {
        CouponInfo coupon = validateAndGetCoupon(couponCode);
        
        if (coupon == null) {
            throw new IllegalArgumentException("유효하지 않은 쿠폰 코드입니다: " + couponCode);
        }
        
        // 최소 주문 금액 확인
        if (order.getTotalAmount().isLessThan(coupon.minimumOrderAmount)) {
            throw new IllegalArgumentException(
                String.format("최소 주문 금액은 %s입니다", coupon.minimumOrderAmount)
            );
        }
        
        Money discountAmount;
        if (coupon.isPercentageDiscount) {
            // 퍼센트 할인
            discountAmount = order.getTotalAmount()
                .multiply(coupon.discountRate);
            
            // 최대 할인 금액 제한
            if (coupon.maxDiscountAmount != null && 
                discountAmount.isGreaterThan(coupon.maxDiscountAmount)) {
                discountAmount = coupon.maxDiscountAmount;
            }
        } else {
            // 정액 할인
            discountAmount = coupon.discountAmount;
        }
        
        result.addDiscount("쿠폰할인(" + couponCode + ")", discountAmount);
    }
    
    /**
     * 포인트 사용
     * 보유 포인트 내에서 사용 가능
     */
    public void applyPointDiscount(Order order, int pointsToUse, PricingResult result) {
        // 보유 포인트 확인 (실제로는 포인트 서비스에서 조회)
        int availablePoints = getCustomerAvailablePoints(order.getCustomerId());
        
        if (pointsToUse > availablePoints) {
            throw new IllegalArgumentException(
                String.format("사용 가능한 포인트는 %d점입니다", availablePoints)
            );
        }
        
        // 포인트는 1점당 1원
        Money pointDiscountAmount = new Money(new BigDecimal(pointsToUse), "KRW");
        
        // 포인트는 주문 금액의 50%까지만 사용 가능 (합리적인 기본값)
        BigDecimal maxUsageRate = new BigDecimal("0.50");
        Money maxPointUsage = result.getSubtotal().multiply(maxUsageRate);
        if (pointDiscountAmount.isGreaterThan(maxPointUsage)) {
            throw new IllegalArgumentException("포인트는 주문 금액의 50%까지만 사용 가능합니다");
        }
        
        result.addDiscount("포인트사용", pointDiscountAmount);
    }
    
    /**
     * 예상 적립 포인트 계산
     * 결제 금액의 1% 적립
     */
    public int calculateEarnedPoints(PricingResult pricingResult) {
        BigDecimal earnRate = new BigDecimal("0.01"); // 1%
        BigDecimal earnedPoints = pricingResult.getFinalAmount()
            .getAmount()
            .multiply(earnRate)
            .setScale(0, RoundingMode.DOWN);
        
        return earnedPoints.intValue();
    }
    
    /**
     * 세금 계산
     * 부가가치세 10% 포함
     */
    public Money calculateTax(Money amount) {
        BigDecimal taxRate = new BigDecimal("0.10"); // 10%
        return amount.multiply(taxRate);
    }
    
    // Helper methods
    
    private boolean isVipCustomer(CustomerId customerId) {
        // VIP 고객 판단 로직 (설정된 금액 이상)
        Money vipThreshold = new Money(pricingPolicy.getVipThreshold(), "KRW");
        Money totalPurchaseAmount = orderRepository.calculateTotalPurchaseAmount(customerId);
        return totalPurchaseAmount.isGreaterThanOrEqual(vipThreshold);
    }
    
    private boolean isExpressDelivery(Order order) {
        // 특급 배송 여부 확인 (실제로는 주문 옵션에서 확인)
        return false;
    }
    
    private CouponInfo validateAndGetCoupon(String couponCode) {
        // 실제로는 쿠폰 서비스나 DB에서 조회
        return null;
    }
    
    private int getCustomerAvailablePoints(CustomerId customerId) {
        // 실제로는 포인트 서비스에서 조회
        return 0;
    }
    
    /**
     * 쿠폰 정보 클래스
     */
    private static class CouponInfo {
        String code;
        boolean isPercentageDiscount;
        BigDecimal discountRate;
        Money discountAmount;
        Money minimumOrderAmount;
        Money maxDiscountAmount;
        LocalDate expiryDate;
    }
    
    /**
     * 가격 계산 결과 클래스
     */
    public static class PricingResult {
        private final Money originalAmount;
        private Money totalDiscountAmount;
        private Money totalSurchargeAmount;
        private Money shippingFee;
        private final List<PriceAdjustment> discounts;
        private final List<PriceAdjustment> surcharges;
        
        public PricingResult(Money originalAmount) {
            this.originalAmount = originalAmount;
            this.totalDiscountAmount = Money.ZERO("KRW");
            this.totalSurchargeAmount = Money.ZERO("KRW");
            this.shippingFee = Money.ZERO("KRW");
            this.discounts = new java.util.ArrayList<>();
            this.surcharges = new java.util.ArrayList<>();
        }
        
        public void addDiscount(String reason, Money amount) {
            discounts.add(new PriceAdjustment(reason, amount));
            totalDiscountAmount = totalDiscountAmount.add(amount);
        }
        
        public void addSurcharge(String reason, Money amount) {
            surcharges.add(new PriceAdjustment(reason, amount));
            totalSurchargeAmount = totalSurchargeAmount.add(amount);
        }
        
        public void setShippingFee(Money shippingFee) {
            this.shippingFee = shippingFee;
        }
        
        public Money getSubtotal() {
            return originalAmount
                .subtract(totalDiscountAmount)
                .add(totalSurchargeAmount);
        }
        
        public Money getFinalAmount() {
            return getSubtotal().add(shippingFee);
        }
        
        // Getters
        public Money getOriginalAmount() { return originalAmount; }
        public Money getTotalDiscountAmount() { return totalDiscountAmount; }
        public Money getTotalSurchargeAmount() { return totalSurchargeAmount; }
        public Money getShippingFee() { return shippingFee; }
        public List<PriceAdjustment> getDiscounts() { return discounts; }
        public List<PriceAdjustment> getSurcharges() { return surcharges; }
    }
    
    /**
     * 가격 조정 내역 클래스
     */
    public static class PriceAdjustment {
        private final String reason;
        private final Money amount;
        
        public PriceAdjustment(String reason, Money amount) {
            this.reason = reason;
            this.amount = amount;
        }
        
        public String getReason() { return reason; }
        public Money getAmount() { return amount; }
    }
}