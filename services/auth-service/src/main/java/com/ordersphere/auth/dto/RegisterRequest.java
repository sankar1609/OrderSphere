package com.ordersphere.auth.dto;

import com.ordersphere.auth.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank String username, @NotBlank @Size(min = 8) String password, @NotNull Role role) {}
