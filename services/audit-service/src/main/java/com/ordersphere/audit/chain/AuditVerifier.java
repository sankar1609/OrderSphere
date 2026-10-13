package com.ordersphere.audit.chain;

import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.ChainAnchor;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.repository.AuditEntryRepository;
import com.ordersphere.audit.repository.ChainAnchorRepository;
import com.ordersphere.audit.repository.ChainHeadRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-derives the chain from the stored rows and compares. It catches:
 *
 * <ul>
 *   <li>changed content - the entry's recomputed hash differs from the stored one;
 *   <li>a removed entry - the sequence has a gap, or the next entry's prevHash matches nothing;
 *   <li>reordered or re-linked entries - prevHash doesn't equal the previous entry's hash;
 *   <li>a removed or rebuilt tail - the head row or a recorded anchor no longer matches.
 * </ul>
 */
@Service
public class AuditVerifier {

  private static final Logger log = LoggerFactory.getLogger(AuditVerifier.class);

  private final AuditEntryRepository entries;
  private final ChainHeadRepository head;
  private final ChainAnchorRepository anchors;
  private final Clock clock;
  private final AtomicReference<VerificationResult> lastResult = new AtomicReference<>();

  public AuditVerifier(
      AuditEntryRepository entries,
      ChainHeadRepository head,
      ChainAnchorRepository anchors,
      Clock clock) {
    this.entries = entries;
    this.head = head;
    this.anchors = anchors;
    this.clock = clock;
  }

  /** The most recent verification, or null if none has run since startup. */
  public VerificationResult lastResult() {
    return lastResult.get();
  }

  /**
   * Repeatable read: the head and every page of entries are read from one snapshot, so an event
   * appended while the walk is in progress can't look like a break.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public VerificationResult verify() {
    VerificationResult result = walk();
    lastResult.set(result);
    if (result.valid()) {
      log.info(
          "Audit chain verified: {} entries, head seq {}",
          result.entriesChecked(),
          result.headSeq());
    } else {
      log.error(
          "AUDIT CHAIN BROKEN at seq {}: {} ({} entries checked before it)",
          result.firstBrokenSeq(),
          result.reason(),
          result.entriesChecked());
    }
    return result;
  }

  private VerificationResult walk() {
    Instant now = clock.instant();
    ChainHead storedHead =
        head.findById(ChainHead.ID)
            .orElseThrow(() -> new IllegalStateException("audit chain head missing"));
    long headSeq = storedHead.getSeq();
    String headHash = storedHead.getHash();

    Map<Long, String> anchored = new HashMap<>();
    for (ChainAnchor anchor : anchors.findAllByOrderBySeqAsc()) {
      anchored.put(anchor.getSeq(), anchor.getHash());
    }

    long checked = 0;
    long previousSeq = 0;
    String previousHash = ChainHead.GENESIS_HASH;
    List<AuditEntry> page = entries.findTop500BySeqGreaterThanOrderBySeqAsc(previousSeq);
    while (!page.isEmpty()) {
      for (AuditEntry entry : page) {
        long seq = entry.getSeq();
        if (seq != previousSeq + 1) {
          return VerificationResult.broken(
              checked,
              previousSeq + 1,
              "entry " + (previousSeq + 1) + " is missing (next stored entry is " + seq + ")",
              headSeq,
              headHash,
              now);
        }
        if (!previousHash.equals(entry.getPrevHash())) {
          return VerificationResult.broken(
              checked,
              seq,
              "entry " + seq + " does not link to the entry before it",
              headSeq,
              headHash,
              now);
        }
        if (!ChainHasher.hash(entry).equals(entry.getHash())) {
          return VerificationResult.broken(
              checked,
              seq,
              "entry " + seq + " has been altered: its content no longer matches its hash",
              headSeq,
              headHash,
              now);
        }
        String anchorHash = anchored.remove(seq);
        if (anchorHash != null && !anchorHash.equals(entry.getHash())) {
          return VerificationResult.broken(
              checked,
              seq,
              "entry " + seq + " differs from the hash anchored for it",
              headSeq,
              headHash,
              now);
        }
        previousSeq = seq;
        previousHash = entry.getHash();
        checked++;
      }
      page = entries.findTop500BySeqGreaterThanOrderBySeqAsc(previousSeq);
    }

    if (previousSeq != headSeq || !previousHash.equals(headHash)) {
      return VerificationResult.broken(
          checked,
          previousSeq + 1,
          "the chain ends at entry " + previousSeq + " but the head records entry " + headSeq,
          headSeq,
          headHash,
          now);
    }
    if (!anchored.isEmpty()) {
      long missing = anchored.keySet().stream().min(Long::compare).orElseThrow();
      return VerificationResult.broken(
          checked,
          missing,
          "entry " + missing + " was anchored but is no longer in the chain",
          headSeq,
          headHash,
          now);
    }
    return VerificationResult.ok(checked, headSeq, headHash, now);
  }
}
