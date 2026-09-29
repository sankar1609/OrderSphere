package com.ordersphere.auth.security;

import com.ordersphere.security.JwtProperties;
import com.ordersphere.security.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service verifies the tokens it issued (e.g. on /auth/me) against its own keys directly,
 * instead of fetching its own JWKS over HTTP. Replaces common-security's JWKS-backed verifier.
 */
@Configuration
public class AuthJwtConfiguration {

  @Bean
  public JwtVerifier jwtVerifier(SigningKeyService signingKeys, JwtProperties properties) {
    return new JwtVerifier(
        kid -> {
          // The active key signs almost every token; only look up older keys when needed.
          SigningKeyService.ActiveKey active = signingKeys.activeKey();
          return active.kid().equals(kid) ? active.publicKey() : signingKeys.publicKeys().get(kid);
        },
        properties.getIssuer());
  }
}
