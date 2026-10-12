package com.ordersphere.audit.web;

import com.ordersphere.audit.chain.AuditAnchorService;
import com.ordersphere.audit.chain.AuditVerifier;
import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.exception.AuditEntryNotFoundException;
import com.ordersphere.audit.repository.AuditEntryRepository;
import com.ordersphere.audit.repository.ChainHeadRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the audit log. */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

  private final AuditEntryRepository entries;
  private final ChainHeadRepository head;
  private final AuditVerifier verifier;
  private final AuditAnchorService anchors;

  public AuditQueryService(
      AuditEntryRepository entries,
      ChainHeadRepository head,
      AuditVerifier verifier,
      AuditAnchorService anchors) {
    this.entries = entries;
    this.head = head;
    this.verifier = verifier;
    this.anchors = anchors;
  }

  /** Newest first. Every filter is optional; they combine with AND. */
  public AuditPageResponse search(
      Long orderId,
      String eventType,
      String actor,
      String username,
      Instant from,
      Instant to,
      int page,
      int size) {
    Specification<AuditEntry> filter =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (orderId != null) {
            predicates.add(cb.equal(root.get("orderId"), orderId));
          }
          if (hasText(eventType)) {
            predicates.add(cb.equal(root.get("eventType"), eventType.trim()));
          }
          if (hasText(actor)) {
            predicates.add(cb.equal(root.get("actor"), actor.trim()));
          }
          if (hasText(username)) {
            predicates.add(cb.equal(root.get("subjectUsername"), username.trim()));
          }
          if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
          }
          if (to != null) {
            predicates.add(cb.lessThan(root.get("occurredAt"), to));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };
    Page<AuditEntry> result =
        entries.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "seq")));
    return new AuditPageResponse(
        result.getContent().stream().map(AuditEntryResponse::summary).toList(),
        page,
        size,
        result.getTotalElements());
  }

  public AuditEntryResponse get(long seq) {
    return entries
        .findById(seq)
        .map(AuditEntryResponse::detail)
        .orElseThrow(() -> new AuditEntryNotFoundException(seq));
  }

  /** Everything recorded about one order, oldest first. */
  public List<AuditEntryResponse> orderTimeline(long orderId) {
    return entries.findByOrderIdOrderBySeqAsc(orderId).stream()
        .map(AuditEntryResponse::summary)
        .toList();
  }

  public AuditStatusResponse status() {
    ChainHead current =
        head.findById(ChainHead.ID)
            .orElseThrow(() -> new IllegalStateException("audit chain head missing"));
    return new AuditStatusResponse(
        entries.count(),
        current.getSeq(),
        current.getHash(),
        verifier.lastResult(),
        anchors
            .lastAnchor()
            .map(
                a ->
                    new AuditStatusResponse.Anchor(
                        a.getSeq(), a.getHash(), a.getTarget(), a.getAnchoredAt()))
            .orElse(null));
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
