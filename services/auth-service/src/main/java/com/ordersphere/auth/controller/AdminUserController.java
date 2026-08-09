package com.ordersphere.auth.controller;

import com.ordersphere.auth.dto.RoleChangeRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

  private final AuthService authService;

  public AdminUserController(AuthService authService) {
    this.authService = authService;
  }

  @PatchMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse changeRole(
      @PathVariable Long id, @Valid @RequestBody RoleChangeRequest request) {
    return authService.changeRole(id, request.role());
  }
}
