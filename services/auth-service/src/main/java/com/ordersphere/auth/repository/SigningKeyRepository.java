package com.ordersphere.auth.repository;

import com.ordersphere.auth.domain.SigningKey;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

  Optional<SigningKey> findFirstByActiveTrueOrderByCreatedAtDesc();

  List<SigningKey> findAllByOrderByCreatedAtDesc();
}
