package com.pb.synth.tradecapture.exception;

import com.pb.synth.tradecapture.model.ErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.ZonedDateTime;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {
        
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        ErrorDetail errorDetail = ErrorDetail.builder()
            .code("VALIDATION_ERROR")
            .message("Validation failed: " + errors)
            .timestamp(ZonedDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(errorDetail));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex, WebRequest request) {
        
        ErrorDetail errorDetail = ErrorDetail.builder()
            .code("VALIDATION_ERROR")
            .message(ex.getMessage())
            .timestamp(ZonedDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(errorDetail));
    }

    @ExceptionHandler(DuplicateTradeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateTradeException(
            DuplicateTradeException ex, WebRequest request) {
        
        ErrorDetail errorDetail = ErrorDetail.builder()
            .code("DUPLICATE_TRADE_ID")
            .message(ex.getMessage())
            .timestamp(ZonedDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse(errorDetail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error", ex);
        
        ErrorDetail errorDetail = ErrorDetail.builder()
            .code("INTERNAL_SERVER_ERROR")
            .message("An unexpected error occurred")
            .timestamp(ZonedDateTime.now())
            .path(request.getDescription(false).replace("uri=", ""))
            .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse(errorDetail));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ErrorResponse {
        private ErrorDetail error;
    }
}

