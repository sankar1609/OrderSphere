package com.ordersphere.auth.service;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.dto.AuthResponse;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.exception.DuplicateUsernameException;
import com.ordersphere.auth.exception.InvalidRoleSelectionException;
import com.ordersphere.auth.exception.UserNotFoundException;
import com.ordersphere.auth.repository.UserRepository;
import com.ordersphere.events.UserAuthenticatedEvent;
import com.ordersphere.events.UserRegisteredEvent;
import com.ordersphere.security.JwtProperties;
import com.ordersphere.security.JwtTokenProvider;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Set<Role> SELF_ASSIGNABLE_ROLES = EnumSet.of(Role.CUSTOMER, Role.VENDOR);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtProperties jwtProperties;
  private final ApplicationEventPublisher eventPublisher;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      JwtProperties jwtProperties,
      ApplicationEventPublisher eventPublisher) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.jwtProperties = jwtProperties;
    this.eventPublisher = eventPublisher;
  }

  public UserResponse register(RegisterRequest request) {
    if (!SELF_ASSIGNABLE_ROLES.contains(request.role())) {
      throw new InvalidRoleSelectionException(
          "Role " + request.role() + " cannot be self-assigned at registration");
    }
    if (userRepository.existsByUsername(request.username())) {
      throw new DuplicateUsernameException(request.username());
    }

    User user =
        new User(request.username(), passwordEncoder.encode(request.password()), request.role());
    User saved = userRepository.save(user);

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

    String token =
        jwtTokenProvider.generateToken(user.getUsername(), Map.of("role", user.getRole().name()));

    eventPublisher.publishEvent(new UserAuthenticatedEvent(user.getId(), user.getUsername()));

    return AuthResponse.bearer(token, jwtProperties.getExpirationMillis() / 1000);
  }

  public UserResponse getCurrentUser(String username) {
    return userRepository
        .findByUsername(username)
        .map(UserResponse::from)
        .orElseThrow(() -> new UserNotFoundException("No user found for username: " + username));
  }

  public UserResponse changeRole(Long userId, Role newRole) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new UserNotFoundException("No user found with id: " + userId));

    user.setRole(newRole);
    return UserResponse.from(userRepository.save(user));
  }
}
