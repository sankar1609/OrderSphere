package com.ordersphere.auth.controller;

import com.ordersphere.auth.dto.RoleChangeRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users (admin)", description = "List users and change roles")
@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

  private final AuthService authService;

  public AdminUserController(AuthService authService) {
    this.authService = authService;
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<UserResponse> listUsers() {
    return authService.listUsers();
  }

  @PatchMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse changeRole(
      @PathVariable Long id, @Valid @RequestBody RoleChangeRequest request, Principal principal) {
    return authService.changeRole(principal.getName(), id, request.role());
  }
}
