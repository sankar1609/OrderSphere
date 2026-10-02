package com.ordersphere.auth.security;

import com.ordersphere.auth.domain.RefreshToken;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.exception.InvalidRefreshTokenException;
import com.ordersphere.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long-lived, single-use refresh tokens. Only a SHA-256 hash is stored. Every refresh rotates the
 * token; presenting one that was already rotated or revoked means it leaked, so the whole family
 * (every token descending from that login) is revoked.
 *
 * <p>One exception: a token rotated only moments ago ({@code auth.refresh-token-reuse-grace},
 * default 30s) by a session that is still live gets a sibling token instead. Two browser tabs
 * sharing one refresh token both refresh when their access token expires; without the grace window
 * the slower tab would look like a thief and log the user out everywhere.
 */
@Service
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

  private final RefreshTokenRepository repository;
  private final Duration ttl;
  private final Duration reuseGrace;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  public RefreshTokenService(
      RefreshTokenRepository repository,
      @Value("${auth.refresh-token-ttl:P30D}") Duration ttl,
      @Value("${auth.refresh-token-reuse-grace:PT30S}") Duration reuseGrace) {
    this(repository, ttl, reuseGrace, Clock.systemUTC());
  }

  RefreshTokenService(
      RefreshTokenRepository repository, Duration ttl, Duration reuseGrace, Clock clock) {
    this.repository = repository;
    this.ttl = ttl;
    this.reuseGrace = reuseGrace;
    this.clock = clock;
  }

  /** Starts a new session (token family) for a fresh login; returns the raw token. */
  @Transactional
  public String issue(User user) {
    return create(user, UUID.randomUUID().toString()).raw();
  }

  /**
   * Exchanges a refresh token for a new one in the same family. Not rolled back on {@link
   * InvalidRefreshTokenException}: a detected reuse must keep the family revoked.
   */
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public Rotation rotate(String rawToken) {
    RefreshToken current =
        repository
            .findByTokenHashForUpdate(hash(rawToken))
            .orElseThrow(InvalidRefreshTokenException::new);
    Instant now = clock.instant();
    if (current.isRevoked() && isConcurrentRefresh(current, now)) {
      return new Rotation(
          current.getUser(), create(current.getUser(), current.getFamilyId()).raw());
    }
    if (current.isRevoked()) {
      int revoked = repository.revokeFamily(current.getFamilyId(), now);
      log.warn(
          "Refresh token reuse for user {} - revoked {} token(s) of the session",
          current.getUser().getUsername(),
          revoked);
      throw new InvalidRefreshTokenException();
    }
    if (!now.isBefore(current.getExpiresAt())) {
      throw new InvalidRefreshTokenException();
    }
    Created next = create(current.getUser(), current.getFamilyId());
    current.setRevokedAt(now);
    current.setReplacedBy(next.entity().getId());
    return new Rotation(current.getUser(), next.raw());
  }

  /**
   * The token was rotated (not revoked by logout or reuse detection) within the grace window, and
   * its session is still live - another tab refreshing at the same moment, not a thief.
   */
  private boolean isConcurrentRefresh(RefreshToken token, Instant now) {
    return token.getReplacedBy() != null
        && Duration.between(token.getRevokedAt(), now).compareTo(reuseGrace) < 0
        && repository.existsByFamilyIdAndRevokedAtIsNull(token.getFamilyId());
  }

  /** Logout: ends the session the token belongs to. Unknown tokens are ignored (idempotent). */
  @Transactional
  public void revokeSession(String rawToken) {
    repository
        .findByTokenHash(hash(rawToken))
        .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
  }

  /** Logout everywhere, and what a role change does: ends every session of the user. */
  @Transactional
  public void revokeAll(User user) {
    repository.revokeAllForUser(user.getId(), clock.instant());
  }

  private Created create(User user, String familyId) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant now = clock.instant();
    RefreshToken entity =
        repository.save(new RefreshToken(user, hash(raw), familyId, now.plus(ttl), now));
    return new Created(raw, entity);
  }

  private static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record Rotation(User user, String refreshToken) {}

  private record Created(String raw, RefreshToken entity) {}
}
