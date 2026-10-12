package com.ordersphere.audit.repository;

import com.ordersphere.audit.domain.ChainHead;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ChainHeadRepository extends JpaRepository<ChainHead, Short> {

  /** Row lock held until the append commits: one writer at a time, so the chain stays linear. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select h from ChainHead h where h.id = 1")
  Optional<ChainHead> lockHead();
}
