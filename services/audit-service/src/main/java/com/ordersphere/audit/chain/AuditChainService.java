package com.ordersphere.audit.chain;

import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.domain.RecordedEvent;
import com.ordersphere.audit.repository.AuditEntryRepository;
import com.ordersphere.audit.repository.ChainHeadRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appends events to the chain. */
@Service
public class AuditChainService {

  private final AuditEntryRepository entries;
  private final ChainHeadRepository head;
  private final Clock clock;

  public AuditChainService(AuditEntryRepository entries, ChainHeadRepository head, Clock clock) {
    this.entries = entries;
    this.head = head;
    this.clock = clock;
  }

  /**
   * Appends the event as the next entry, or does nothing if an event with this id is already
   * recorded (the broker redelivers). Holds the chain-head row lock from reading the previous hash
   * to writing the new head, so two appends can never chain from the same entry.
   */
  @Transactional
  public Optional<AuditEntry> append(RecordedEvent event) {
    ChainHead current =
        head.lockHead().orElseThrow(() -> new IllegalStateException("audit chain head missing"));
    if (entries.existsByEventId(event.eventId())) {
      return Optional.empty();
    }
    long seq = current.getSeq() + 1;
    String hash = ChainHasher.hash(current.getHash(), seq, event);
    AuditEntry entry =
        entries.save(new AuditEntry(seq, event, clock.instant(), current.getHash(), hash));
    current.advanceTo(seq, hash);
    head.save(current);
    return Optional.of(entry);
  }
}
