package com.ordersphere.auth.dto;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;

public record UserResponse(Long id, String username, Role role) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getRole());
  }
}
