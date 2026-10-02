package com.ordersphere.auth.dto;

/**
 * {@code token} is the short-lived access token; {@code refreshToken} is exchanged at {@code POST
 * /auth/refresh} for a new pair once it expires.
 */
public record AuthResponse(
    String token, String tokenType, long expiresInSeconds, String refreshToken) {

  public static AuthResponse bearer(String token, long expiresInSeconds, String refreshToken) {
    return new AuthResponse(token, "Bearer", expiresInSeconds, refreshToken);
  }
}
