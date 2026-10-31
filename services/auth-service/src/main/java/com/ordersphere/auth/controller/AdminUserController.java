package com.ordersphere.auth.controller;

import com.ordersphere.auth.dto.PageResponse;
import com.ordersphere.auth.dto.RoleChangeRequest;
import com.ordersphere.auth.dto.UserResponse;
import com.ordersphere.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users (admin)", description = "List users and change roles")
@Validated
@RestController
@RequestMapping("/auth/admin/users")
public class AdminUserController {

  private final AuthService authService;

  public AdminUserController(AuthService authService) {
    this.authService = authService;
  }

  @Operation(
      summary = "Users, oldest first, a page at a time",
      description = "q (optional) keeps the users whose username contains it, ignoring case.")
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public PageResponse<UserResponse> listUsers(
      @RequestParam(defaultValue = "0") @Min(0) @Max(10_000_000) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size,
      @RequestParam(required = false) @Size(max = 50) String q) {
    return authService.listUsers(page, size, q == null || q.isBlank() ? null : q.trim());
  }

  @PatchMapping("/{id}/role")
  @PreAuthorize("hasRole('ADMIN')")
  public UserResponse changeRole(
      @PathVariable Long id, @Valid @RequestBody RoleChangeRequest request, Principal principal) {
    return authService.changeRole(principal.getName(), id, request.role());
  }
}
