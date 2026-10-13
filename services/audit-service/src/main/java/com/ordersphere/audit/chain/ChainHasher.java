package com.ordersphere.audit.chain;

import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.RecordedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The hash that links one audit entry to the one before it: SHA-256 over the previous entry's hash
 * and this entry's content. Changing any covered field of any entry, removing an entry or swapping
 * two changes the hashes from there on, so the stored ones stop matching.
 *
 * <p>Fields are length-prefixed before hashing, so no two different sets of values can produce the
 * same input (e.g. moving a character from the end of one field to the start of the next).
 */
public final class ChainHasher {

  private ChainHasher() {}

  public static String hash(String prevHash, long seq, RecordedEvent event) {
    return hash(
        prevHash,
        seq,
        event.eventId(),
        event.eventType(),
        event.occurredAt().toEpochMilli(),
        event.sourceService(),
        event.actor(),
        event.payload());
  }

  /** Recomputes a stored entry's hash from its own content. */
  public static String hash(AuditEntry entry) {
    return hash(
        entry.getPrevHash(),
        entry.getSeq(),
        entry.getEventId(),
        entry.getEventType(),
        entry.getOccurredAt().toEpochMilli(),
        entry.getSourceService(),
        entry.getActor(),
        entry.getPayload());
  }

  private static String hash(
      String prevHash,
      long seq,
      String eventId,
      String eventType,
      long occurredAtMillis,
      String sourceService,
      String actor,
      String payload) {
    MessageDigest digest = sha256();
    for (String field :
        new String[] {
          prevHash,
          Long.toString(seq),
          eventId,
          eventType,
          Long.toString(occurredAtMillis),
          sourceService,
          actor,
          payload
        }) {
      // A null field is distinct from an empty one.
      byte[] bytes = field == null ? null : field.getBytes(StandardCharsets.UTF_8);
      digest.update(
          (bytes == null ? "-1" : Integer.toString(bytes.length)).getBytes(StandardCharsets.UTF_8));
      digest.update((byte) ':');
      if (bytes != null) {
        digest.update(bytes);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required", ex);
    }
  }
}
