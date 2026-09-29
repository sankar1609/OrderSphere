package com.ordersphere.security;

import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

  /**
   * auth-service defines its own verifier backed by its local keys, so this one backs off there.
   */
  @Bean
  @ConditionalOnMissingBean
  public JwtVerifier jwtVerifier(JwtProperties properties) {
    if (StringUtils.hasText(properties.getPublicKey())) {
      RSAPublicKey key = RsaKeys.parsePublicKey(properties.getPublicKey());
      return new JwtVerifier(kid -> key, properties.getIssuer());
    }
    if (StringUtils.hasText(properties.getJwksUri())) {
      return new JwtVerifier(new JwksKeySource(properties.getJwksUri()), properties.getIssuer());
    }
    throw new IllegalStateException(
        "No JWT verification key configured: set jwt.jwks-uri (auth-service's JWKS endpoint)"
            + " or jwt.public-key");
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier jwtVerifier) {
    return new JwtAuthenticationFilter(jwtVerifier);
  }
}
