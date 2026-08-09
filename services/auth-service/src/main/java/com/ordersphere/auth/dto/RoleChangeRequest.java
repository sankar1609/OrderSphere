package com.ordersphere.auth.dto;

import com.ordersphere.auth.domain.Role;
import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull Role role) {}
