package com.ordersphere.auth.dto;

import com.ordersphere.auth.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Usernames are 3-50 letters, digits, '.', '_' or '-' - no spaces, so two accounts can't look
 * identical - and are unique regardless of case. Passwords are 8-72 characters: bcrypt only uses
 * the first 72 bytes, so anything longer would be silently truncated.
 */
public record RegisterRequest(
    @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(
            regexp = "[A-Za-z0-9._-]+",
            message = "may only contain letters, digits, '.', '_' and '-'")
        String username,
    @NotBlank @Size(min = 8, max = 72) String password,
    @NotNull Role role) {}
