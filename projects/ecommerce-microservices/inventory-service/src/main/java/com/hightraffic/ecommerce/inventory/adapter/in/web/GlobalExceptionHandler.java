package com.hightraffic.ecommerce.inventory.adapter.in.web;

import com.hightraffic.ecommerce.inventory.adapter.in.web.dto.ErrorResponse;
import com.hightraffic.ecommerce.inventory.domain.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * 
 * 재고 서비스의 도메인 예외를 HTTP 응답으로 변환하고
 * 일관된 에러 응답 형식을 제공합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // === 재고 도메인 예외 처리 ===
    
    /**
     * 재고 부족
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStock(
            InsufficientStockException ex, HttpServletRequest request) {
        
        log.warn("재고 부족: productId={}, requested={}, available={}",
                ex.getProductId(), ex.getRequestedQuantity(), ex.getAvailableQuantity());
        
        ErrorResponse response = ErrorResponse.insufficientStock(
            request.getRequestURI(),
            ex.getProductId(),
            ex.getRequestedQuantity().toString(),
            ex.getAvailableQuantity().toString()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * 상품을 찾을 수 없음
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleProductNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        
        log.warn("상품을 찾을 수 없음: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("productId", ex.getProductId());
        
        ErrorResponse response = ErrorResponse.withDetails(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            "PRODUCT_NOT_FOUND",
            details
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 예약을 찾을 수 없음
     */
    @ExceptionHandler(ReservationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReservationNotFound(
            ReservationNotFoundException ex, HttpServletRequest request) {
        
        log.warn("예약을 찾을 수 없음: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("reservationId", ex.getReservationId());
        
        ErrorResponse response = ErrorResponse.withDetails(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            "RESERVATION_NOT_FOUND",
            details
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 잘못된 재고 작업
     */
    @ExceptionHandler(InvalidStockOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStockOperation(
            InvalidStockOperationException ex, HttpServletRequest request) {
        
        log.warn("잘못된 재고 작업: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "INVALID_STOCK_OPERATION"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 일반 재고 도메인 예외
     */
    @ExceptionHandler(InventoryDomainException.class)
    public ResponseEntity<ErrorResponse> handleInventoryDomainException(
            InventoryDomainException ex, HttpServletRequest request) {
        
        log.error("재고 도메인 예외: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "INVENTORY_DOMAIN_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    // === 동시성 관련 예외 처리 ===
    
    /**
     * 낙관적 락 충돌
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        
        log.warn("낙관적 락 충돌: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            "다른 사용자가 데이터를 수정했습니다. 다시 시도해주세요.",
            request.getRequestURI(),
            "OPTIMISTIC_LOCK_CONFLICT"
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * 타임아웃
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(
            TimeoutException ex, HttpServletRequest request) {
        
        log.error("작업 타임아웃: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.REQUEST_TIMEOUT.value(),
            "Request Timeout",
            "요청 처리 시간이 초과되었습니다",
            request.getRequestURI(),
            "TIMEOUT"
        );
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    // === 유효성 검증 예외 처리 ===
    
    /**
     * Bean Validation 예외 (RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        log.warn("입력값 검증 실패: {}", ex.getMessage());
        
        List<ErrorResponse.ValidationError> validationErrors = 
            ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldError)
                .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.validation(
            request.getRequestURI(),
            validationErrors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Bean Validation 예외 (PathVariable, RequestParam)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        log.warn("제약조건 위반: {}", ex.getMessage());
        
        List<ErrorResponse.ValidationError> validationErrors = 
            ex.getConstraintViolations().stream()
                .map(this::mapConstraintViolation)
                .collect(Collectors.toList());
        
        ErrorResponse response = ErrorResponse.validation(
            request.getRequestURI(),
            validationErrors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    // === 일반 Spring 예외 처리 ===
    
    /**
     * 필수 파라미터 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        log.warn("필수 파라미터 누락: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            String.format("필수 파라미터 '%s'가 누락되었습니다", ex.getParameterName()),
            request.getRequestURI(),
            "MISSING_PARAMETER"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 타입 변환 실패
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        log.warn("타입 변환 실패: {}", ex.getMessage());
        
        String message = String.format(
            "'%s' 파라미터의 값 '%s'을(를) %s 타입으로 변환할 수 없습니다",
            ex.getName(), ex.getValue(), ex.getRequiredType().getSimpleName()
        );
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            message,
            request.getRequestURI(),
            "TYPE_MISMATCH"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * JSON 파싱 실패
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        log.warn("JSON 파싱 실패: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "잘못된 JSON 형식입니다",
            request.getRequestURI(),
            "INVALID_JSON"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 404 Not Found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        log.warn("핸들러를 찾을 수 없음: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            "요청한 리소스를 찾을 수 없습니다",
            request.getRequestURI(),
            "RESOURCE_NOT_FOUND"
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    // === 데이터베이스 예외 처리 ===
    
    /**
     * 데이터 무결성 위반
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        log.error("데이터 무결성 위반: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            "데이터 무결성 제약조건을 위반했습니다",
            request.getRequestURI(),
            "DATA_INTEGRITY_VIOLATION"
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    // === 비동기 예외 처리 ===
    
    /**
     * CompletableFuture 예외
     */
    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletionException(
            CompletionException ex, HttpServletRequest request) {
        
        Throwable cause = ex.getCause();
        if (cause instanceof InventoryDomainException) {
            return handleInventoryDomainException((InventoryDomainException) cause, request);
        }
        if (cause instanceof TimeoutException) {
            return handleTimeout((TimeoutException) cause, request);
        }
        
        return handleGeneralException(ex, request);
    }
    
    // === 일반 예외 처리 ===
    
    /**
     * 처리되지 않은 일반 예외
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            Exception ex, HttpServletRequest request) {
        
        log.error("처리되지 않은 예외 발생: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
            request.getRequestURI(),
            "INTERNAL_SERVER_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    // === Helper Methods ===
    
    private ErrorResponse.ValidationError mapFieldError(FieldError error) {
        return new ErrorResponse.ValidationError(
            error.getField(),
            error.getDefaultMessage(),
            error.getRejectedValue()
        );
    }
    
    private ErrorResponse.ValidationError mapConstraintViolation(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        return new ErrorResponse.ValidationError(
            propertyPath.substring(propertyPath.lastIndexOf('.') + 1),
            violation.getMessage(),
            violation.getInvalidValue()
        );
    }
}