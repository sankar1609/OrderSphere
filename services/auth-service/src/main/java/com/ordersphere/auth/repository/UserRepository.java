package com.ordersphere.auth.repository;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsername(String username);

  boolean existsByUsername(String username);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByRole(Role role);

  /**
   * Users whose username matches a lower-case LIKE pattern, with backslash as the escape character
   * (AuthService builds it from the search text).
   */
  @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE :pattern ESCAPE '\\'")
  Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
