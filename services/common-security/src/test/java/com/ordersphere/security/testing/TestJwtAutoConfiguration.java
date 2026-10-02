package com.ordersphere.security.testing;

import com.ordersphere.security.JwtAutoConfiguration;
import com.ordersphere.security.JwtVerifier;
import com.ordersphere.security.RsaKeys;
import java.security.interfaces.RSAPublicKey;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Test-only: shipped in common-security's test-jar, so any service that has it on its test
 * classpath verifies tokens against {@link TestJwtIssuer}'s key instead of fetching auth-service's
 * JWKS. A service's own {@link JwtVerifier} bean (auth-service) still takes precedence.
 */
@AutoConfiguration(before = JwtAutoConfiguration.class)
public class TestJwtAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JwtVerifier jwtVerifier() {
    RSAPublicKey key = RsaKeys.parsePublicKey(TestJwtIssuer.PUBLIC_KEY);
    return new JwtVerifier(
        kid -> TestJwtIssuer.KEY_ID.equals(kid) ? key : null, TestJwtIssuer.ISSUER);
  }
}
