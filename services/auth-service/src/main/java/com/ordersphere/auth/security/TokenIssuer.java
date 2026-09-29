package com.ordersphere.auth.security;

import com.ordersphere.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The only place OrderSphere JWTs are minted: RS256, signed with auth-service's active key. */
@Component
public class TokenIssuer {

  /** Role carried by tokens issued to other services via client credentials. */
  public static final String SERVICE_ROLE = "SERVICE";

  private final SigningKeyService signingKeys;
  private final JwtProperties jwtProperties;
  private final Duration serviceTokenTtl;
  private final Clock clock;

  @Autowired
  public TokenIssuer(
      SigningKeyService signingKeys,
      JwtProperties jwtProperties,
      @Value("${auth.service-token-ttl:PT5M}") Duration serviceTokenTtl) {
    this(signingKeys, jwtProperties, serviceTokenTtl, Clock.systemUTC());
  }

  TokenIssuer(
      SigningKeyService signingKeys,
      JwtProperties jwtProperties,
      Duration serviceTokenTtl,
      Clock clock) {
    this.signingKeys = signingKeys;
    this.jwtProperties = jwtProperties;
    this.serviceTokenTtl = serviceTokenTtl;
    this.clock = clock;
  }

  /** A user's access token: {@code sub} = username, {@code role} = the user's role. */
  public IssuedToken issueAccessToken(String username, String role) {
    return issue(username, role, "access", Duration.ofMillis(jwtProperties.getExpirationMillis()));
  }

  /** A client-credentials token for another service: {@code sub} = client id, role SERVICE. */
  public IssuedToken issueServiceToken(String clientId) {
    return issue(clientId, SERVICE_ROLE, "service", serviceTokenTtl);
  }

  private IssuedToken issue(String subject, String role, String type, Duration ttl) {
    SigningKeyService.ActiveKey key = signingKeys.activeKey();
    Instant now = clock.instant();
    String token =
        Jwts.builder()
            .header()
            .keyId(key.kid())
            .and()
            .issuer(jwtProperties.getIssuer())
            .subject(subject)
            .claims(Map.of("role", role, "typ", type))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key.privateKey(), Jwts.SIG.RS256)
            .compact();
    return new IssuedToken(token, ttl.toSeconds());
  }

  public record IssuedToken(String token, long expiresInSeconds) {}
}
