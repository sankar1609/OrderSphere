package com.ordersphere.payment.exception;

import com.ordersphere.payment.gateway.PaymentGatewayException;
import com.ordersphere.payment.reconciliation.ReconciliationService;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(PaymentGatewayException.class)
  public ResponseEntity<Object> handlePaymentGatewayFailure(PaymentGatewayException ex) {
    return errorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<Object> handlePaymentNotFound(PaymentNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(InvalidPaymentStateException.class)
  public ResponseEntity<Object> handleInvalidPaymentState(InvalidPaymentStateException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(ReconciliationService.FindingNotFoundException.class)
  public ResponseEntity<Object> handleFindingNotFound(
      ReconciliationService.FindingNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ReconciliationService.FindingStateException.class)
  public ResponseEntity<Object> handleFindingState(ReconciliationService.FindingStateException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  /** Invalid request parameters (e.g. a reconciliation window outside 1-744 hours). */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
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

  private ResponseEntity<Object> errorResponse(HttpStatus status, String message) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
  }
}
