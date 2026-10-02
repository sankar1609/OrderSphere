package com.ordersphere.security.testing;

import com.ordersphere.security.RsaKeys;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Signs tokens for tests with a fixed, test-only RSA key pair (never used outside tests). {@link
 * TestJwtAutoConfiguration} makes every service's test context trust this key, so tests can mint
 * customer / admin / service tokens without running auth-service.
 */
public final class TestJwtIssuer {

  public static final String KEY_ID = "test-key";

  public static final String ISSUER = "ordersphere-auth";

  /** Base64 X.509 DER - usable as {@code jwt.public-key}. */
  public static final String PUBLIC_KEY =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3Xkm/0KccEfMMuFvrNBg2HVJ8pRqMt+K"
          + "YT4NbledRj7k9wv8fHZ/pn2UWxe1QbsM2BvdC7IM6uzWU49zWD/2Es8eh8sZMu4YfJ/zAuIKlhxf"
          + "6a4/HCC2TUbl4gfpCjSRiFXzffXpkiBnJi1fS8RIE87jBom9F2qiZuR5UJDU0pqftsQNmhySKsqb"
          + "5XprWnwzNVDy88bimfJQyhmczeDQRfrJ1+X3EnCSVfDD6KZGw2UTcSQDX06UH3NEUrPoDZKEa8UD"
          + "kdiNFLRX1bEgBJP4qCciliBF1hhrJ8jFDx9ODXGq0GInN/CF+3or3J+XSd8lHdvk9lkMgj0NWYPT"
          + "sy7RQQIDAQAB";

  private static final String PRIVATE_KEY =
      "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDdeSb/QpxwR8wy4W+s0GDYdUny"
          + "lGoy34phPg1uV51GPuT3C/x8dn+mfZRbF7VBuwzYG90Lsgzq7NZTj3NYP/YSzx6Hyxky7hh8n/MC"
          + "4gqWHF/prj8cILZNRuXiB+kKNJGIVfN99emSIGcmLV9LxEgTzuMGib0XaqJm5HlQkNTSmp+2xA2a"
          + "HJIqypvlemtafDM1UPLzxuKZ8lDKGZzN4NBF+snX5fcScJJV8MPopkbDZRNxJANfTpQfc0RSs+gN"
          + "koRrxQOR2I0UtFfVsSAEk/ioJyKWIEXWGGsnyMUPH04NcarQYic38IX7eivcn5dJ3yUd2+T2WQyC"
          + "PQ1Zg9OzLtFBAgMBAAECggEAA3XhJhvkVdzJVAIxvIGFAdzTSvcGscTMZLiTfSXhTzesXC/Twh4X"
          + "4IIVz1aPfndtu/KzgXtGuGT69FNJeheFwMV7tKN3wVP3Dftx0Jm3kKDg3sXaNpZMQujYrq9w9Lfd"
          + "jkPJii5Nd/sJoY8T7hsjBSw2X+PHEJaZ8wGe46Nfr+qzIx9MxFEwb2dvWsaEByHdCZuA6Z02xgZa"
          + "/YE4trZwa6cYfHS+0WWHOPL8UchUILgSMYNv5ySnHVL3/GoNVzO9bPyXnz8Fu3C34lPV7JugyUp5"
          + "bZXBlEgVZPvbWBSBjQSQtSKpMgW3gnCDZOw7XNrXHqL6xX4gsQ/HYS1XwIPAAQKBgQDxBa3zXtmO"
          + "wqgF3mfl/GFOhKOqFoKUDy6gX8UD20TnyJ6E1W6UJX0ZWS77hYpK3Xu86OiAcaYmSNxG7dOkaYgk"
          + "TD1GP4fnGilsatuX6iYxJ70CVLb7UhuHQXVDT1F8pEh5B4fCkpl/+A5DYv7hhC/BtFQB5A/BmQRf"
          + "yMXn+rL3bQKBgQDrPHohU8XTQirleZrR9KHZ0oqnlbw1u3tvc0cK9tIf93s8HyiRY91RXjR3MfKB"
          + "otycItMoSITdWBGpjF3TU8U38QaWXKdnXO4Wp+d3/gpuRZlyPZ2AAZJ4ogNsw9dpB6HDXALPeZyj"
          + "+WpzHbijgCJlCwGI9A+/HRqRU4+wQZu4pQKBgHU/RwyyInFxPh2EwNQ/fvKcUaWnR6RQ8mSv0f57"
          + "Rzzd+kcyMJW+2ule2h0fLFQZBF20i44YOzQ6k3WpsiAz+jd2IwSuWSVSM757CRIQpL2a0DZ3EYCW"
          + "w697Z1j0F/bjUCIYSLGdcTCwcffUC5uXG0hGueslj4ilMFyMJcIg0bjNAoGBAJSRQpUv4n41iQAb"
          + "WGOH/Hyk03taP180RJY0GUdccYNM+2ESUL3ac5oDRGGPgxjK8kgOWoxTmM5u4+DQTSW9/44AROfM"
          + "JtJKR/i5HKCVKqNQg8Q6E/IXjBlyQXV9Dtw/vLO0tfWxWfPZ/5gqykVBFPf6BoMnmZZbXD8ypkps"
          + "dVwdAoGBAN1UfJe1LuqIssIemnOM8N4iqLv5AwQvCvSzjdVmMIoqOqQvH+PuhTmFCwRwx8wT8hYY"
          + "PZzL0HvAiSIIDQ+2OVJNLMqBm2nUgZKP6p1Xbmn+eQbE1lADil9+W7jEBPHa7Q1jePobvVQzjm3Z"
          + "yG/9ZMgZS0/bTAVPE1d/tIoNjiuk";

  private static final RSAPrivateKey SIGNING_KEY = RsaKeys.parsePrivateKey(PRIVATE_KEY);

  private TestJwtIssuer() {}

  /** A valid one-hour access token for {@code subject} with the given role. */
  public static String token(String subject, String role) {
    return token(subject, role, Duration.ofHours(1));
  }

  public static String token(String subject, String role, Duration validFor) {
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(KEY_ID)
        .and()
        .subject(subject)
        .issuer(ISSUER)
        .claims(Map.of("role", role))
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(validFor)))
        .signWith(SIGNING_KEY, Jwts.SIG.RS256)
        .compact();
  }

  /** {@code "Bearer " + token(subject, role)} - ready for an Authorization header. */
  public static String bearer(String subject, String role) {
    return "Bearer " + token(subject, role);
  }
}
