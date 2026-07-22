package com.realtimetilegame.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(BusinessException exception) {
        ErrorCode errorCode = exception.errorCode();
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiErrorResponse.of(errorCode));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceUnavailable(ServiceUnavailableException exception) {
        ErrorCode errorCode = exception.errorCode();
        log.warn("Service unavailable: errorCode={}", errorCode, exception);
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiErrorResponse.FieldErrorBody> fieldErrors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(GlobalExceptionHandler::toFieldErrorBody)
            .toList();

        return ResponseEntity.badRequest().body(ApiErrorResponse.validation(fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("Unexpected server error", exception);
        return ResponseEntity.status(errorCode.httpStatus()).body(ApiErrorResponse.of(errorCode));
    }

    private static ApiErrorResponse.FieldErrorBody toFieldErrorBody(FieldError fieldError) {
        String message = fieldError.getDefaultMessage() == null
            ? ErrorCode.VALIDATION_FAILED.defaultMessage()
            : fieldError.getDefaultMessage();
        return new ApiErrorResponse.FieldErrorBody(fieldError.getField(), message);
    }
}
