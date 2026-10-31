package com.ordersphere.inventory.repository;

import com.ordersphere.inventory.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

  Optional<Product> findBySku(String sku);

  /** Products whose available stock is at or below their reorder threshold. */
  @Query(
      "SELECT COUNT(p) FROM Product p"
          + " WHERE p.quantityOnHand - p.quantityReserved <= p.reorderThreshold")
  long countLowStock();

  boolean existsBySku(String sku);

  /**
   * Products whose SKU or name matches a lower-case LIKE pattern, with backslash as the escape
   * character (CatalogReader builds it from the search text).
   */
  @Query(
      "SELECT p FROM Product p WHERE LOWER(p.sku) LIKE :pattern ESCAPE '\\'"
          + " OR LOWER(p.name) LIKE :pattern ESCAPE '\\'")
  Page<Product> search(@Param("pattern") String pattern, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.sku = :sku")
  Optional<Product> findWithLockBySku(@Param("sku") String sku);
}
