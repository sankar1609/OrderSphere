package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

  private static final String SECRET = "this-is-a-test-secret-key-that-is-long-enough-for-hs256";

  @Test
  void generatesTokenThatValidatesAndReturnsOriginalSubject() {
    JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

    String token = provider.generateToken("user-123");

    assertThat(provider.isValid(token)).isTrue();
    assertThat(provider.getSubject(token)).isEqualTo("user-123");
  }

  @Test
  void rejectsExpiredToken() throws InterruptedException {
    JwtTokenProvider provider = new JwtTokenProvider(SECRET, 1);

    String token = provider.generateToken("user-123");
    Thread.sleep(10);

    assertThat(provider.isValid(token)).isFalse();
  }

  @Test
  void rejectsTokenSignedWithADifferentSecret() {
    JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 60_000);
    JwtTokenProvider verifier =
        new JwtTokenProvider("a-completely-different-secret-key-of-sufficient-length", 60_000);

    String token = issuer.generateToken("user-123");

    assertThat(verifier.isValid(token)).isFalse();
  }

  @Test
  void rejectsMalformedToken() {
    JwtTokenProvider provider = new JwtTokenProvider(SECRET, 60_000);

    assertThat(provider.isValid("not-a-real-token")).isFalse();
  }
}
