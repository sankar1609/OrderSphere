package com.ordersphere.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;

public class JwtTokenProvider {

  private final SecretKey key;
  private final long expirationMillis;

  public JwtTokenProvider(String secret, long expirationMillis) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes());
    this.expirationMillis = expirationMillis;
  }

  public String generateToken(String subject) {
    return generateToken(subject, Map.of());
  }

  public String generateToken(String subject, Map<String, Object> claims) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMillis);

    return Jwts.builder()
        .subject(subject)
        .claims(claims)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(key)
        .compact();
  }

  public String getSubject(String token) {
    return getClaims(token).getSubject();
  }

  public Claims getClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  public boolean isValid(String token) {
    try {
      getClaims(token);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }
}
