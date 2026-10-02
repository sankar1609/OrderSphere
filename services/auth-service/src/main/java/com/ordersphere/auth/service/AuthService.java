package com.ordersphere.auth.service;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.dto.AuthResponse;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.exception.DuplicateUsernameException;
import com.ordersphere.auth.exception.InvalidRoleSelectionException;
import com.ordersphere.auth.exception.SelfRoleChangeException;
import com.ordersphere.auth.exception.UserNotFoundException;
import com.ordersphere.auth.repository.UserRepository;
import com.ordersphere.auth.security.RefreshTokenService;
import com.ordersphere.auth.security.TokenIssuer;
import com.ordersphere.events.UserAuthenticatedEvent;
import com.ordersphere.events.UserRegisteredEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Set<Role> SELF_ASSIGNABLE_ROLES = EnumSet.of(Role.CUSTOMER, Role.VENDOR);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final RefreshTokenService refreshTokens;
  private final ApplicationEventPublisher eventPublisher;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      TokenIssuer tokenIssuer,
      RefreshTokenService refreshTokens,
      ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenIssuer = tokenIssuer;
    this.refreshTokens = refreshTokens;
    this.eventPublisher = eventPublisher;
  }

  public UserResponse register(RegisterRequest request) {
    if (!SELF_ASSIGNABLE_ROLES.contains(request.role())) {
      throw new InvalidRoleSelectionException(
          "Role " + request.role() + " cannot be self-assigned at registration");
    }
    // Case-insensitive, so "Alice" can't register alongside "alice" and pass for them.
    if (userRepository.existsByUsernameIgnoreCase(request.username())) {
      throw new DuplicateUsernameException(request.username());
    }

    User user =
        new User(request.username(), passwordEncoder.encode(request.password()), request.role());
    User saved;
    try {
      saved = userRepository.save(user);
    } catch (DataIntegrityViolationException taken) {
      // Registered by a concurrent request between the check above and this insert.
      throw new DuplicateUsernameException(request.username());
    }

    eventPublisher.publishEvent(
        new UserRegisteredEvent(saved.getId(), saved.getUsername(), saved.getRole().name()));

    return UserResponse.from(saved);
  }

  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByUsername(request.username())
            .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new BadCredentialsException("Invalid username or password");
    }

    eventPublisher.publishEvent(new UserAuthenticatedEvent(user.getId(), user.getUsername()));

    return tokensFor(user, refreshTokens.issue(user));
  }

  /** Exchanges a refresh token for a new access token and a new (rotated) refresh token. */
  public AuthResponse refresh(String refreshToken) {
    RefreshTokenService.Rotation rotation = refreshTokens.rotate(refreshToken);
    return tokensFor(rotation.user(), rotation.refreshToken());
  }

  /** Ends the session the refresh token belongs to. */
  public void logout(String refreshToken) {
    refreshTokens.revokeSession(refreshToken);
  }

  /** Ends every session of the user. */
  public void logoutEverywhere(String username) {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(
                () -> new UserNotFoundException("No user found for username: " + username));
    refreshTokens.revokeAll(user);
  }

  private AuthResponse tokensFor(User user, String refreshToken) {
    TokenIssuer.IssuedToken access =
        tokenIssuer.issueAccessToken(user.getUsername(), user.getRole().name());
    return AuthResponse.bearer(access.token(), access.expiresInSeconds(), refreshToken);
  }

  public UserResponse getCurrentUser(String username) {
    return userRepository
        .findByUsername(username)
        .map(UserResponse::from)
        .orElseThrow(() -> new UserNotFoundException("No user found for username: " + username));
  }

  /** Admin view: every user, oldest first. */
  public List<UserResponse> listUsers() {
    return userRepository.findAll(Sort.by("id")).stream().map(UserResponse::from).toList();
  }

  /**
   * Admins can change anyone's role but their own - otherwise the only admin could demote
   * themselves and leave nobody able to manage roles.
   */
  public UserResponse changeRole(String actingUsername, Long userId, Role newRole) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("No user found with id: " + userId));
    if (user.getUsername().equals(actingUsername)) {
      throw new SelfRoleChangeException();
    }

    user.setRole(newRole);
    User saved = userRepository.save(user);
    // Sessions refresh into tokens carrying the old role; end them so the new role applies from
    // the next login (access tokens already out expire within their short lifetime).
    refreshTokens.revokeAll(saved);
    return UserResponse.from(saved);
  }
}
