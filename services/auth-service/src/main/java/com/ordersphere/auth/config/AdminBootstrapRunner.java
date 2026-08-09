package com.ordersphere.auth.config;

import com.ordersphere.auth.domain.Role;
import com.ordersphere.auth.domain.User;
import com.ordersphere.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String bootstrapUsername;
  private final String bootstrapPassword;

  public AdminBootstrapRunner(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${admin.bootstrap.username}") String bootstrapUsername,
      @Value("${admin.bootstrap.password}") String bootstrapPassword) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.bootstrapUsername = bootstrapUsername;
    this.bootstrapPassword = bootstrapPassword;
  }

  @Override
  public void run(String... args) {
    if (userRepository.existsByRole(Role.ADMIN)) {
      return;
    }

    User admin = new User(bootstrapUsername, passwordEncoder.encode(bootstrapPassword), Role.ADMIN);
    userRepository.save(admin);
    log.warn(
        "No ADMIN user found — created bootstrap admin '{}'. Change this password immediately"
            + " in any non-dev environment.",
        bootstrapUsername);
  }
}
