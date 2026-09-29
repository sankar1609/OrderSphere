package com.ordersphere.auth.exception;

/**
 * The refresh token is unknown, expired, revoked or already used - the client must log in again.
 */
public class InvalidRefreshTokenException extends RuntimeException {

  public InvalidRefreshTokenException() {
    super("Invalid or expired refresh token");
  }
}
