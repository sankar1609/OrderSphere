package com.ordersphere.inventory.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ProductNotFoundException.class)
  public ResponseEntity<Object> handleProductNotFound(ProductNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ReservationNotFoundException.class)
  public ResponseEntity<Object> handleReservationNotFound(ReservationNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(DuplicateSkuException.class)
  public ResponseEntity<Object> handleDuplicateSku(DuplicateSkuException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidReservationStateException.class)
  public ResponseEntity<Object> handleInvalidReservationState(InvalidReservationStateException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InsufficientStockException.class)
  public ResponseEntity<Object> handleInsufficientStock(InsufficientStockException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(StockLimitExceededException.class)
  public ResponseEntity<Object> handleStockLimitExceeded(StockLimitExceededException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(ProductOwnershipException.class)
  public ResponseEntity<Object> handleProductOwnership(ProductOwnershipException ex) {
    return errorResponse(HttpStatus.FORBIDDEN, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return errorResponse(HttpStatus.BAD_REQUEST, message);
  }

  /** Out-of-range query parameters, e.g. {@code size=0} on the catalog. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'");
  }

  private ResponseEntity<Object> errorResponse(HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
