package com.ordersphere.inventory.cache;

import com.ordersphere.inventory.domain.Product;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener on {@link Product}: whenever a product row is inserted, updated or deleted -
 * by whatever code path (creating, restocking, reserving, releasing, confirming, the expiry job,
 * anything added later) - its cached catalog entries are evicted once the transaction commits.
 * Doing it here rather than in each service method means no write path can forget.
 */
@Component
public class ProductCacheInvalidator {

  private final CatalogCache cache;

  // Lazy: Hibernate creates entity listeners while the EntityManagerFactory is being built.
  public ProductCacheInvalidator(@Lazy CatalogCache cache) {
    this.cache = cache;
  }

  @PostPersist
  @PostUpdate
  @PostRemove
  public void productChanged(Product product) {
    cache.evictAfterCommit(product.getSku());
  }
}
