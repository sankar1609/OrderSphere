package com.ordersphere.auth.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ordersphere.auth.domain.RefreshToken;
import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.exception.InvalidRefreshTokenException;
import com.ordersphere.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private RefreshTokenRepository repository;

  @Test
  void expiredRefreshTokenIsRejectedWithoutRevokingTheSession() {
    AtomicReference<RefreshToken> saved = new AtomicReference<>();
    when(repository.save(any(RefreshToken.class)))
        .thenAnswer(
            invocation -> {
              saved.set(invocation.getArgument(0));
              return saved.get();
            });
    User user = new User("kim", "hash", Role.CUSTOMER);
    RefreshTokenService atStart =
        new RefreshTokenService(
            repository, Duration.ofDays(30), Clock.fixed(START, ZoneOffset.UTC));
    String raw = atStart.issue(user);

    RefreshTokenService later =
        new RefreshTokenService(
            repository,
            Duration.ofDays(30),
            Clock.fixed(START.plus(Duration.ofDays(31)), ZoneOffset.UTC));
    when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(saved.get()));

    assertThatThrownBy(() -> later.rotate(raw)).isInstanceOf(InvalidRefreshTokenException.class);
    verify(repository, never()).revokeFamily(anyString(), any());
  }
}
