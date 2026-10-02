package com.ordersphere.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How services verify JWTs. Set exactly one key source: {@code jwt.jwks-uri} (auth-service's JWKS
 * endpoint - the normal case) or {@code jwt.public-key} (a fixed RSA public key, PEM or base64 DER
 * - for tests or deployments that pin the key).
 */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  /** Issuer every token must carry ({@code iss}); auth-service stamps this on what it signs. */
  private String issuer = "ordersphere-auth";

  private String jwksUri;

  private String publicKey;

  /** Access-token lifetime; only read by auth-service, which issues them. */
  private long expirationMillis = 3_600_000;

  public String getIssuer() {
    return issuer;
  }

  public void setIssuer(String issuer) {
    this.issuer = issuer;
  }

  public String getJwksUri() {
    return jwksUri;
  }

  public void setJwksUri(String jwksUri) {
    this.jwksUri = jwksUri;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public long getExpirationMillis() {
    return expirationMillis;
  }

  public void setExpirationMillis(long expirationMillis) {
    this.expirationMillis = expirationMillis;
  }
}
