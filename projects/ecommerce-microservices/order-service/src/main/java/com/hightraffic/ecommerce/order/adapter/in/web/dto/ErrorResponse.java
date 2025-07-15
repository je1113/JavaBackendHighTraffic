package com.hightraffic.ecommerce.order.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 에러 응답 DTO
 */
public record ErrorResponse(
    @JsonProperty("timestamp")
    LocalDateTime timestamp,
    
    @JsonProperty("status")
    int status,
    
    @JsonProperty("error")
    String error,
    
    @JsonProperty("message")
    String message,
    
    @JsonProperty("path")
    String path,
    
    @JsonProperty("errorCode")
    String errorCode,
    
    @JsonProperty("details")
    Map<String, Object> details,
    
    @JsonProperty("validationErrors")
    List<ValidationError> validationErrors
) {
    
    /**
     * 유효성 검증 에러 정보
     */
    public record ValidationError(
        @JsonProperty("field")
        String field,
        
        @JsonProperty("message")
        String message,
        
        @JsonProperty("rejectedValue")
        Object rejectedValue
    ) {}
    
    /**
     * 일반 에러 응답 생성
     */
    public static ErrorResponse of(int status, String error, String message, 
                                 String path, String errorCode) {
        return new ErrorResponse(
            LocalDateTime.now(),
            status,
            error,
            message,
            path,
            errorCode,
            null,
            null
        );
    }
    
    /**
     * 유효성 검증 에러 응답 생성
     */
    public static ErrorResponse validation(String path, List<ValidationError> errors) {
        return new ErrorResponse(
            LocalDateTime.now(),
            400,
            "Bad Request",
            "입력값 검증에 실패했습니다",
            path,
            "VALIDATION_ERROR",
            null,
            errors
        );
    }
    
    /**
     * 상세 정보를 포함한 에러 응답 생성
     */
    public static ErrorResponse withDetails(int status, String error, String message,
                                          String path, String errorCode,
                                          Map<String, Object> details) {
        return new ErrorResponse(
            LocalDateTime.now(),
            status,
            error,
            message,
            path,
            errorCode,
            details,
            null
        );
    }
}