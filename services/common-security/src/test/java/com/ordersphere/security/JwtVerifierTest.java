package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ordersphere.security.testing.TestJwtIssuer;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

  private static final RSAPublicKey TEST_KEY = RsaKeys.parsePublicKey(TestJwtIssuer.PUBLIC_KEY);

  private final JwtVerifier verifier =
      new JwtVerifier(
          kid -> TestJwtIssuer.KEY_ID.equals(kid) ? TEST_KEY : null, TestJwtIssuer.ISSUER);

  @Test
  void acceptsTokenSignedByTheKnownKey() {
    assertThat(verifier.verify(TestJwtIssuer.token("alice", "CUSTOMER")).getSubject())
        .isEqualTo("alice");
  }

  @Test
  void rejectsTokenSignedWithTheOldSharedHmacSecret() {
    String forged =
        Jwts.builder()
            .header()
            .keyId(TestJwtIssuer.KEY_ID)
            .and()
            .subject("mallory")
            .issuer(TestJwtIssuer.ISSUER)
            .claims(Map.of("role", "ADMIN"))
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(
                Keys.hmacShaKeyFor(
                    "dev-only-secret-key-change-me-before-any-real-deployment"
                        .getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsTokenSignedByAnotherRsaKeyUnderTheSameKid() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair other = generator.generateKeyPair();
    String forged =
        Jwts.builder()
            .header()
            .keyId(TestJwtIssuer.KEY_ID)
            .and()
            .subject("mallory")
            .issuer(TestJwtIssuer.ISSUER)
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(other.getPrivate(), Jwts.SIG.RS256)
            .compact();

    assertThatThrownBy(() -> verifier.verify(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsExpiredToken() {
    String expired = TestJwtIssuer.token("alice", "CUSTOMER", Duration.ofSeconds(-5));

    assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(JwtException.class);
  }

  @Test
  void rejectsWrongIssuerAndUnknownKeyId() {
    JwtVerifier otherIssuer = new JwtVerifier(kid -> TEST_KEY, "someone-else");
    JwtVerifier noKeys = new JwtVerifier(kid -> null, TestJwtIssuer.ISSUER);
    String token = TestJwtIssuer.token("alice", "CUSTOMER");

    assertThatThrownBy(() -> otherIssuer.verify(token)).isInstanceOf(JwtException.class);
    assertThatThrownBy(() -> noKeys.verify(token)).isInstanceOf(JwtException.class);
  }
}
