package com.ordersphere.audit.chain;

import com.ordersphere.audit.domain.ChainAnchor;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.repository.ChainAnchorRepository;
import com.ordersphere.audit.repository.ChainHeadRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Anchors the chain head when it has moved since the last anchor. */
@Service
public class AuditAnchorService {

  private final ChainHeadRepository head;
  private final ChainAnchorRepository anchors;
  private final AuditAnchor anchor;
  private final Clock clock;

  public AuditAnchorService(
      ChainHeadRepository head, ChainAnchorRepository anchors, AuditAnchor anchor, Clock clock) {
    this.head = head;
    this.anchors = anchors;
    this.anchor = anchor;
    this.clock = clock;
  }

  /**
   * Publishes the current head to the anchor and records that it did. Publishing comes first: if it
   * fails nothing is recorded and the next run tries again; if recording fails after publishing,
   * the head is simply published again.
   */
  @Transactional
  public Optional<ChainAnchor> anchorHead() {
    ChainHead current =
        head.findById(ChainHead.ID)
            .orElseThrow(() -> new IllegalStateException("audit chain head missing"));
    if (current.getSeq() == 0) {
      return Optional.empty();
    }
    Optional<ChainAnchor> last = anchors.findTopByOrderBySeqDescIdDesc();
    if (last.isPresent() && last.get().getSeq() >= current.getSeq()) {
      return Optional.empty();
    }
    anchor.anchor(current.getSeq(), current.getHash());
    return Optional.of(
        anchors.save(
            new ChainAnchor(
                current.getSeq(), current.getHash(), anchor.target(), clock.instant())));
  }

  @Transactional(readOnly = true)
  public Optional<ChainAnchor> lastAnchor() {
    return anchors.findTopByOrderBySeqDescIdDesc();
  }
}
