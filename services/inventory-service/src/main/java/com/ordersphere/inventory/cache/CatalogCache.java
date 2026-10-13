package com.ordersphere.inventory.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.inventory.dto.ProductResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The product catalog as the read endpoints return it (ready-made JSON), held in this instance's
 * memory and kept consistent across instances by one small value in Redis: the catalog's
 * <em>version token</em>.
 *
 * <ul>
 *   <li>A read fetches the token and, if this instance's copy carries the same one, returns the
 *       copy - no database, no serializing, and only a few bytes from Redis.
 *   <li>Every change to a product writes a new token after it commits (see
 *       ProductCacheInvalidator), which makes every instance's copy out of date at once.
 *   <li>A copy is stamped with the token read <em>before</em> the database was read. If a change
 *       commits while a reader is loading, the token has moved on, so that copy is never served as
 *       current: there is no window in which a stale catalog can be stored as fresh.
 *   <li>Only one thread reloads after a change; the others wait for its result instead of all
 *       querying at once.
 * </ul>
 *
 * <p>It only ever speeds reads up - stock is still checked in the database, under a row lock, when
 * an order reserves it - so the cache can never cause an oversell.
 *
 * <p>Why not keep the JSON itself in Redis? Every read would then move ~40 KB over the shared Redis
 * connection; the token is a few bytes. Measurements: loadtest/README.md.
 *
 * <p>Fail open: if Redis is unreachable, reads go to the database. A token that couldn't be written
 * is written as soon as Redis answers again, and copies also expire after a short TTL, which bounds
 * how long another instance could serve an old catalog across such an outage.
 */
@Component
public class CatalogCache {

  static final String KEY_VERSION = "inventory:catalog:version";

  /** The token before any change has ever been recorded (or after Redis lost its data). */
  static final String NO_VERSION = "none";

  /** Counter of cache lookups by result: hit, miss, or error (Redis unavailable). */
  public static final String LOOKUPS_COUNTER = "ordersphere.catalog.cache";

  private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);
  private static final Object PENDING_EVICTION = new Object();
  private static final String LIST = "*";
  private static final int MAX_PRODUCT_ENTRIES = 10_000;

  private record Entry(String version, String json, Instant loadedAt) {}

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meters;
  private final boolean enabled;
  private final Duration ttl;
  private final Clock clock;

  /** Key: {@link #LIST} for the whole catalog, otherwise a SKU. */
  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

  private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();
  private final AtomicBoolean redisDown = new AtomicBoolean(false);

  /** A change whose new token couldn't be written; written on the next successful Redis call. */
  private final AtomicBoolean unpublishedChange = new AtomicBoolean(false);

  @Autowired
  public CatalogCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      MeterRegistry meters,
      @Value("${inventory.catalog-cache.enabled:true}") boolean enabled,
      @Value("${inventory.catalog-cache.ttl:PT30S}") Duration ttl) {
    this(redis, objectMapper, meters, enabled, ttl, Clock.systemUTC());
  }

  CatalogCache(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      MeterRegistry meters,
      boolean enabled,
      Duration ttl,
      Clock clock) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.meters = meters;
    this.enabled = enabled;
    this.ttl = ttl;
    this.clock = clock;
  }

  /** The whole catalog as the JSON the endpoint returns. */
  public String listJson(Supplier<List<ProductResponse>> loader) {
    return json(LIST, loader);
  }

  /** One product as JSON. A loader that throws (unknown SKU) is not cached. */
  public String productJson(String sku, Supplier<ProductResponse> loader) {
    return json(sku, loader);
  }

  private String json(String key, Supplier<?> loader) {
    if (!enabled) {
      return serialize(loader.get());
    }
    String version = currentVersion();
    if (version == null) {
      meters.counter(LOOKUPS_COUNTER, "result", "error").increment();
      return serialize(loader.get());
    }
    Entry entry = entries.get(key);
    if (fresh(entry, version)) {
      meters.counter(LOOKUPS_COUNTER, "result", "hit").increment();
      return entry.json();
    }
    meters.counter(LOOKUPS_COUNTER, "result", "miss").increment();
    // One loader at a time per key: the threads that arrive while it runs use its result.
    synchronized (loadLocks.computeIfAbsent(key, k -> new Object())) {
      entry = entries.get(key);
      if (fresh(entry, version)) {
        return entry.json();
      }
      String loaded = serialize(loader.get());
      if (!LIST.equals(key) && entries.size() >= MAX_PRODUCT_ENTRIES) {
        entries.keySet().removeIf(k -> !LIST.equals(k));
      }
      // Stamped with the token read before loading: if the catalog changed meanwhile, the token
      // has too, and this entry won't be served.
      entries.put(key, new Entry(version, loaded, clock.instant()));
      return loaded;
    }
  }

  private boolean fresh(Entry entry, String version) {
    return entry != null
        && entry.version().equals(version)
        && entry.loadedAt().plus(ttl).isAfter(clock.instant());
  }

  /**
   * Publishes a new version token once the current transaction commits - not before, or readers
   * would reload the old rows while the change is still invisible to them - and not at all if it
   * rolls back. Without a transaction, publishes now.
   */
  public void evictAfterCommit(String sku) {
    if (!enabled) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      evict();
      return;
    }
    if (TransactionSynchronizationManager.getResource(PENDING_EVICTION) == null) {
      // The value only marks "already registered for this transaction".
      TransactionSynchronizationManager.bindResource(PENDING_EVICTION, Boolean.TRUE);
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              evict();
            }

            @Override
            public void afterCompletion(int status) {
              TransactionSynchronizationManager.unbindResourceIfPossible(PENDING_EVICTION);
            }
          });
    }
  }

  /** The catalog changed: every instance's copy is now out of date. */
  void evict() {
    // This instance's copies go regardless of Redis; the token tells the other instances.
    entries.clear();
    publishNewVersion();
  }

  private boolean publishNewVersion() {
    try {
      redis.opsForValue().set(KEY_VERSION, UUID.randomUUID().toString());
      unpublishedChange.set(false);
      recovered();
      return true;
    } catch (RuntimeException ex) {
      unpublishedChange.set(true);
      unavailable(ex);
      return false;
    }
  }

  /** The current token, or null if Redis can't be asked (the caller then reads the database). */
  private String currentVersion() {
    if (unpublishedChange.get() && !publishNewVersion()) {
      return null;
    }
    try {
      String version = redis.opsForValue().get(KEY_VERSION);
      recovered();
      return version != null ? version : NO_VERSION;
    } catch (RuntimeException ex) {
      unavailable(ex);
      return null;
    }
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not serialize the catalog", ex);
    }
  }

  /** One warning per outage, not one per request. */
  private void unavailable(RuntimeException ex) {
    if (redisDown.compareAndSet(false, true)) {
      log.warn(
          "Catalog cache unavailable, serving the catalog from the database: {}", ex.getMessage());
    }
  }

  private void recovered() {
    if (redisDown.compareAndSet(true, false)) {
      log.info("Catalog cache available again");
    }
  }
}
