package com.eventrush.api;

import com.eventrush.service.BusinessException;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "VALIDATION_ERROR",
                        firstValidationMessage(exception)
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "VALIDATION_ERROR",
                        "请求字段格式不正确，请检查证件类型和 JSON 格式"
                ));
    }

    private String firstValidationMessage(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .min(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .orElse("request validation failed");
    }
}
