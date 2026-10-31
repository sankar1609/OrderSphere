package com.ordersphere.auth.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DuplicateUsernameException.class)
  public ResponseEntity<Object> handleDuplicateUsername(DuplicateUsernameException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(InvalidRoleSelectionException.class)
  public ResponseEntity<Object> handleInvalidRoleSelection(InvalidRoleSelectionException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
  }

  @ExceptionHandler(SelfRoleChangeException.class)
  public ResponseEntity<Object> handleSelfRoleChange(SelfRoleChangeException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<Object> handleUserNotFound(UserNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<Object> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
    return errorResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
    return errorResponse(HttpStatus.UNAUTHORIZED, "Invalid username or password");
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
