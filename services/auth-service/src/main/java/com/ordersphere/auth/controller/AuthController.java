package com.ordersphere.auth.controller;

import com.ordersphere.auth.dto.AuthResponse;
import com.ordersphere.auth.dto.LoginRequest;
import com.ordersphere.auth.dto.RefreshRequest;
import com.ordersphere.auth.dto.RegisterRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Register, log in, refresh and end sessions")
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @Operation(
      summary = "Exchange a refresh token for a new pair",
      description =
          "Refresh tokens are single-use. Reusing a spent one ends the whole session, except within 30s of its rotation while the session is live (two tabs refreshing at once).")
  @PostMapping("/refresh")
  public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
    return authService.refresh(request.refreshToken());
  }

  /** Ends this session. Always 204, so it can't be used to probe which tokens exist. */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  /** Ends every session of the logged-in user. */
  @Operation(summary = "End all of the caller's sessions")
  @PostMapping("/logout-all")
  public ResponseEntity<Void> logoutAll(Principal principal) {
    authService.logoutEverywhere(principal.getName());
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  public UserResponse me(Principal principal) {
    return authService.getCurrentUser(principal.getName());
  }
}
