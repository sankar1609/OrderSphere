package com.ordersphere.inventory.repository;

import com.ordersphere.inventory.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findBySku(String sku);

  boolean existsBySku(String sku);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.sku = :sku")
  Optional<Product> findWithLockBySku(@Param("sku") String sku);
}
