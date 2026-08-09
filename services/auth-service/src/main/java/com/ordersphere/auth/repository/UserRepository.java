package com.ordersphere.auth.repository;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByRole(Role role);
}
