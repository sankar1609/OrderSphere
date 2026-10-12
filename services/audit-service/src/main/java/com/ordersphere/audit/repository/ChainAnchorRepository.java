package com.ordersphere.audit.repository;

import com.ordersphere.audit.domain.ChainAnchor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChainAnchorRepository extends JpaRepository<ChainAnchor, Long> {

  Optional<ChainAnchor> findTopByOrderBySeqDescIdDesc();

  List<ChainAnchor> findAllByOrderBySeqAsc();
}
