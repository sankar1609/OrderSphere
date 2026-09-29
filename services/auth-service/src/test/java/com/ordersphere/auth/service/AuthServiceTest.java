package com.ordersphere.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.ordersphere.auth.security.TokenIssuer;
import com.ordersphere.events.UserAuthenticatedEvent;
import com.ordersphere.events.UserRegisteredEvent;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TokenIssuer tokenIssuer;
  @Mock private ApplicationEventPublisher eventPublisher;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userRepository, passwordEncoder, tokenIssuer, eventPublisher);
  }

  @Test
  void registerRejectsAdminSelfSelection() {
    RegisterRequest request = new RegisterRequest("alice", "password123", Role.ADMIN);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(InvalidRoleSelectionException.class);
  }

  @Test
  void registerRejectsDuplicateUsername() {
    RegisterRequest request = new RegisterRequest("alice", "password123", Role.CUSTOMER);
    when(userRepository.existsByUsername("alice")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void registerSavesUserAndPublishesEvent() {
    RegisterRequest request = new RegisterRequest("alice", "password123", Role.CUSTOMER);
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("hashed");
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              User user = invocation.getArgument(0);
              user.setId(1L);
              return user;
            });

    UserResponse response = authService.register(request);

    assertThat(response.username()).isEqualTo("alice");
    assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
  }

  @Test
  void loginRejectsUnknownUsername() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void loginRejectsWrongPassword() {
    User user = new User("alice", "hashed", Role.CUSTOMER);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void loginReturnsTokenAndPublishesEventOnSuccess() {
    User user = new User("alice", "hashed", Role.CUSTOMER);
    user.setId(1L);
    when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
    when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
    when(tokenIssuer.issueAccessToken("alice", "CUSTOMER"))
        .thenReturn(new TokenIssuer.IssuedToken("signed-jwt", 3_600));

    AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

    assertThat(response.token()).isEqualTo("signed-jwt");
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.expiresInSeconds()).isEqualTo(3_600);
    verify(eventPublisher).publishEvent(any(UserAuthenticatedEvent.class));
  }

  @Test
  void getCurrentUserThrowsWhenMissing() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.getCurrentUser("ghost"))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void changeRoleUpdatesAndPersistsRole() {
    User user = new User("alice", "hashed", Role.CUSTOMER);
    user.setId(1L);
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(userRepository.save(user)).thenReturn(user);

    UserResponse response = authService.changeRole(1L, Role.ADMIN);

    assertThat(response.role()).isEqualTo(Role.ADMIN);
  }
}
