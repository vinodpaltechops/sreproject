package com.srelab.tradeservice.exception;

import com.srelab.tradeservice.model.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors()
                .stream().map(FieldError::getDefaultMessage).toList();
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(ApiError.builder()
                .status(400).error("Validation Failed")
                .messages(errors).timestamp(Instant.now()).build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.builder()
                .status(404).error("Not Found")
                .messages(List.of(ex.getMessage())).timestamp(Instant.now()).build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.builder()
                .status(404).error("Not Found")
                .messages(List.of("No route found for " + ex.getHttpMethod() + " " + ex.getResourcePath()))
                .timestamp(Instant.now()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.builder()
                .status(500).error("Internal Server Error")
                .messages(List.of("An unexpected error occurred"))
                .timestamp(Instant.now()).build());
    }
}
