package com.hightraffic.ecommerce.order.adapter.in.web;

import com.hightraffic.ecommerce.order.adapter.in.web.dto.ErrorResponse;
import com.hightraffic.ecommerce.order.domain.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.stream.Collectors;

/**
 * 전역 예외 처리기
 * 
 * 도메인 예외를 HTTP 응답으로 변환하고
 * 일관된 에러 응답 형식을 제공합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    // === 도메인 예외 처리 ===
    
    /**
     * 주문을 찾을 수 없음
     */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(
            OrderNotFoundException ex, HttpServletRequest request) {
        
        log.warn("주문을 찾을 수 없음: {}", ex.getMessage());
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            "ORDER_NOT_FOUND"
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * 잘못된 주문 상태
     */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderState(
            InvalidOrderStateException ex, HttpServletRequest request) {
        
        log.warn("잘못된 주문 상태: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("currentState", ex.getCurrentState());
        details.put("targetState", ex.getTargetState());
        details.put("allowedTransitions", ex.getAllowedTransitions());
        
        ErrorResponse response = ErrorResponse.withDetails(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "INVALID_ORDER_STATE",
            details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 주문 한도 초과
     */
    @ExceptionHandler(OrderLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleOrderLimitExceeded(
            OrderLimitExceededException ex, HttpServletRequest request) {
        
        log.warn("주문 한도 초과: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("currentLimit", ex.getCurrentLimit());
        details.put("requestedAmount", ex.getRequestedAmount());
        
        ErrorResponse response = ErrorResponse.withDetails(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "ORDER_LIMIT_EXCEEDED",
            details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 중복 주문 항목
     */
    @ExceptionHandler(DuplicateOrderItemException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrderItem(
            DuplicateOrderItemException ex, HttpServletRequest request) {
        
        log.warn("중복 주문 항목: {}", ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        details.put("productId", ex.getProductId());
        
        ErrorResponse response = ErrorResponse.withDetails(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "DUPLICATE_ORDER_ITEM",
            details
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * 일반 주문 도메인 예외
     */
    @ExceptionHandler(OrderDomainException.class)
    public ResponseEntity<ErrorResponse> handleOrderDomainException(
            OrderDomainException ex, HttpServletRequest request) {
        
        log.error("주문 도메인 예외: {}", ex.getMessage(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            "ORDER_DOMAIN_ERROR"
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
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
        if (cause instanceof OrderDomainException) {
            return handleOrderDomainException((OrderDomainException) cause, request);
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