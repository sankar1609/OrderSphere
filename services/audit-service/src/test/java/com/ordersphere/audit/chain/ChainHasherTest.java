package com.ordersphere.audit.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordersphere.audit.domain.AuditEntry;
import com.ordersphere.audit.domain.ChainHead;
import com.ordersphere.audit.domain.RecordedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChainHasherTest {

  private static final Instant AT = Instant.parse("2026-10-12T08:00:00.123Z");

  private static RecordedEvent event(String actor, String payload) {
    return new RecordedEvent(
        "evt-1",
        "OrderConfirmedEvent",
        "order.confirmed",
        "ordersphere-orders",
        actor,
        42L,
        "alice",
        null,
        AT,
        payload);
  }

  @Test
  void isDeterministicAndLooksLikeSha256() {
    String first = ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("alice", "{\"orderId\":42}"));
    String second = ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("alice", "{\"orderId\":42}"));

    assertThat(first).isEqualTo(second).matches("[0-9a-f]{64}");
  }

  @Test
  void changesWhenAnyCoveredFieldChanges() {
    RecordedEvent base = event("alice", "{\"orderId\":42}");
    String hash = ChainHasher.hash(ChainHead.GENESIS_HASH, 1, base);

    assertThat(ChainHasher.hash("f".repeat(64), 1, base)).as("previous hash").isNotEqualTo(hash);
    assertThat(ChainHasher.hash(ChainHead.GENESIS_HASH, 2, base)).as("seq").isNotEqualTo(hash);
    assertThat(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("mallory", "{\"orderId\":42}")))
        .as("actor")
        .isNotEqualTo(hash);
    assertThat(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("alice", "{\"orderId\":43}")))
        .as("payload")
        .isNotEqualTo(hash);
    assertThat(
            ChainHasher.hash(
                ChainHead.GENESIS_HASH,
                1,
                new RecordedEvent(
                    "evt-1",
                    "OrderConfirmedEvent",
                    "order.confirmed",
                    "ordersphere-orders",
                    "alice",
                    42L,
                    "alice",
                    null,
                    AT.plusMillis(1),
                    "{\"orderId\":42}")))
        .as("time")
        .isNotEqualTo(hash);
  }

  @Test
  void tellsAMissingActorFromAnEmptyOne() {
    assertThat(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event(null, "{}")))
        .isNotEqualTo(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("", "{}")));
  }

  @Test
  void cannotBeFooledByMovingTextBetweenFields() {
    // "ab" + "c" and "a" + "bc" would be the same bytes without length prefixes.
    assertThat(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("ab", "c")))
        .isNotEqualTo(ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event("a", "bc")));
  }

  @Test
  void recomputesAStoredEntrysHashFromItsContent() {
    RecordedEvent event = event("alice", "{\"orderId\":42}");
    String hash = ChainHasher.hash(ChainHead.GENESIS_HASH, 1, event);

    AuditEntry stored = new AuditEntry(1, event, Instant.now(), ChainHead.GENESIS_HASH, hash);

    assertThat(ChainHasher.hash(stored)).isEqualTo(hash);
  }
}
