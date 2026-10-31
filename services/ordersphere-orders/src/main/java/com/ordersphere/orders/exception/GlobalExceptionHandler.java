package com.ordersphere.orders.exception;

import com.ordersphere.orders.client.ServiceTokenProvider;
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

  @ExceptionHandler(CompensationNotFoundException.class)
  public ResponseEntity<Object> handleCompensationNotFound(CompensationNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(CompensationNotRetryableException.class)
  public ResponseEntity<Object> handleCompensationNotRetryable(
      CompensationNotRetryableException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(ShipmentRetryNotAllowedException.class)
  public ResponseEntity<Object> handleShipmentRetryNotAllowed(ShipmentRetryNotAllowedException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(OrderNotFoundException.class)
  public ResponseEntity<Object> handleOrderNotFound(OrderNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(InventoryReservationException.class)
  public ResponseEntity<Object> handleInventoryReservationFailure(
      InventoryReservationException ex) {
    return errorResponse(HttpStatus.FAILED_DEPENDENCY, ex.getMessage());
  }

  @ExceptionHandler(ServiceTokenProvider.ServiceTokenException.class)
  public ResponseEntity<Object> handleServiceTokenUnavailable(
      ServiceTokenProvider.ServiceTokenException ex) {
    return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Order service temporarily unavailable");
  }

  @ExceptionHandler(OrderCancellationNotAllowedException.class)
  public ResponseEntity<Object> handleOrderCancellationNotAllowed(
      OrderCancellationNotAllowedException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
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

  /** Out-of-range query parameters, e.g. {@code size=0} on a list. */
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
