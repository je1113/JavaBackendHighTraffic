package com.hightraffic.ecommerce.order.domain.exception;

/**
 * Order Domain의 기본 예외 클래스
 * 모든 Order 관련 도메인 예외의 상위 클래스
 */
public abstract class OrderDomainException extends RuntimeException {
    
    private final String errorCode;
    
    protected OrderDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    protected OrderDomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}