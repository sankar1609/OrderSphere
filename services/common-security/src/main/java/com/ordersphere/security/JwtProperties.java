package com.ordersphere.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  private String secret;
  private long expirationMillis = 3_600_000;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getExpirationMillis() {
    return expirationMillis;
  }

  public void setExpirationMillis(long expirationMillis) {
    this.expirationMillis = expirationMillis;
  }
}
