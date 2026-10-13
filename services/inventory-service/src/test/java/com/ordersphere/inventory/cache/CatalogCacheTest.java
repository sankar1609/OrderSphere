package com.ordersphere.inventory.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.inventory.dto.ProductResponse;
import com.ordersphere.inventory.exception.ProductNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class CatalogCacheTest {

  private static final Duration TTL = Duration.ofSeconds(30);
  private static final ProductResponse WIDGET =
      new ProductResponse(1L, "WIDGET-1", "Widget", 10, 2, 8, 3, new BigDecimal("4.50"), "vera");

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final AtomicInteger loads = new AtomicInteger();
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-13T08:00:00Z"));
  private final Clock clock =
      new Clock() {
        @Override
        public Instant instant() {
          return now.get();
        }

        @Override
        public java.time.ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
          return this;
        }
      };

  @BeforeEach
  void valueOperations() {
    lenient().when(redis.opsForValue()).thenReturn(values);
  }

  private CatalogCache cache(boolean enabled) {
    return new CatalogCache(redis, objectMapper, meters, enabled, TTL, clock);
  }

  private List<ProductResponse> load() {
    loads.incrementAndGet();
    return List.of(WIDGET);
  }

  private String listJson() throws Exception {
    return objectMapper.writeValueAsString(List.of(WIDGET));
  }

  private double lookups(String result) {
    return meters.counter(CatalogCache.LOOKUPS_COUNTER, "result", result).count();
  }

  @Test
  void theFirstReadLoadsAndLaterReadsAtTheSameVersionDoNot() throws Exception {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);

    assertThat(cache.listJson(this::load)).isEqualTo(listJson());
    assertThat(cache.listJson(this::load)).isEqualTo(listJson());
    assertThat(cache.listJson(this::load)).isEqualTo(listJson());

    assertThat(loads).hasValue(1);
    assertThat(lookups("miss")).isEqualTo(1);
    assertThat(lookups("hit")).isEqualTo(2);
    // Only the small version token ever crosses the wire - never the catalog itself.
    verify(values, never()).set(anyString(), eq(listJson()));
  }

  @Test
  void aNewVersionTokenMakesTheNextReadReload() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1", "v1", "v2", "v2");
    CatalogCache cache = cache(true);

    cache.listJson(this::load);
    cache.listJson(this::load);
    assertThat(loads).hasValue(1);

    cache.listJson(this::load);
    cache.listJson(this::load);
    assertThat(loads).hasValue(2);
  }

  @Test
  void aCopyLoadedWhileTheCatalogWasChangingIsNotServedAsCurrent() {
    // The reader saw v1, and the change (v2) committed while it was loading: its copy carries v1.
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1", "v2");
    CatalogCache cache = cache(true);

    cache.listJson(this::load);
    cache.listJson(this::load);

    assertThat(loads).hasValue(2);
  }

  @Test
  void beforeAnyChangeHasBeenRecordedTheCatalogIsStillCached() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn(null);
    CatalogCache cache = cache(true);

    cache.listJson(this::load);
    cache.listJson(this::load);

    assertThat(loads).hasValue(1);
  }

  @Test
  void aCopyExpiresAfterTheTtlEvenIfTheVersionHasNotChanged() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);

    cache.listJson(this::load);
    now.set(now.get().plus(TTL).plusSeconds(1));
    cache.listJson(this::load);

    assertThat(loads).hasValue(2);
  }

  @Test
  void productsAreCachedSeparatelyFromTheList() throws Exception {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);
    AtomicInteger productLoads = new AtomicInteger();

    for (int i = 0; i < 3; i++) {
      assertThat(
              cache.productJson(
                  "WIDGET-1",
                  () -> {
                    productLoads.incrementAndGet();
                    return WIDGET;
                  }))
          .isEqualTo(objectMapper.writeValueAsString(WIDGET));
    }
    cache.listJson(this::load);

    assertThat(productLoads).hasValue(1);
    assertThat(loads).hasValue(1);
  }

  @Test
  void anUnknownProductIsNotCached() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);
    AtomicInteger attempts = new AtomicInteger();

    for (int i = 0; i < 2; i++) {
      assertThatThrownBy(
              () ->
                  cache.productJson(
                      "NOPE",
                      () -> {
                        attempts.incrementAndGet();
                        throw new ProductNotFoundException("NOPE");
                      }))
          .isInstanceOf(ProductNotFoundException.class);
    }

    assertThat(attempts).hasValue(2);
  }

  @Test
  void evictingPublishesANewTokenAndDropsThisInstancesCopy() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);
    cache.listJson(this::load);

    cache.evictAfterCommit("WIDGET-1");
    cache.listJson(this::load);

    verify(values).set(eq(CatalogCache.KEY_VERSION), anyString());
    // Reloaded although the mocked token still reads v1: the local copy went on eviction.
    assertThat(loads).hasValue(2);
  }

  @Test
  void whenRedisIsDownTheCatalogIsReadFromTheDatabaseEveryTime() throws Exception {
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
    CatalogCache cache = cache(true);

    assertThat(cache.listJson(this::load)).isEqualTo(listJson());
    assertThat(cache.listJson(this::load)).isEqualTo(listJson());

    assertThat(loads).hasValue(2);
    assertThat(lookups("error")).isEqualTo(2);
  }

  @Test
  void aChangeThatCouldNotBePublishedIsPublishedOnceRedisIsBack() {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);
    cache.listJson(this::load);

    doThrow(new RedisConnectionFailureException("down"))
        .doNothing()
        .when(values)
        .set(eq(CatalogCache.KEY_VERSION), anyString());
    cache.evictAfterCommit("WIDGET-1");
    // Redis answers again: the read first publishes the token that was missed, so every other
    // instance learns of the change too.
    cache.listJson(this::load);

    verify(values, times(2)).set(eq(CatalogCache.KEY_VERSION), anyString());
    assertThat(loads).hasValue(2);
  }

  @Test
  void onlyOneThreadReloadsWhileTheOthersWaitForItsResult() throws Exception {
    when(values.get(CatalogCache.KEY_VERSION)).thenReturn("v1");
    CatalogCache cache = cache(true);
    CountDownLatch loading = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      for (int i = 0; i < 8; i++) {
        pool.submit(
            () ->
                cache.listJson(
                    () -> {
                      loads.incrementAndGet();
                      loading.countDown();
                      try {
                        release.await(5, TimeUnit.SECONDS);
                      } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                      }
                      return List.of(WIDGET);
                    }));
      }
      assertThat(loading.await(5, TimeUnit.SECONDS)).isTrue();
      // Give the other seven time to reach the cache while the first is still loading.
      Thread.sleep(200);
      release.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    assertThat(loads).hasValue(1);
  }

  @Test
  void switchedOffItNeverTouchesRedis() throws Exception {
    CatalogCache cache = cache(false);

    assertThat(cache.listJson(this::load)).isEqualTo(listJson());
    assertThat(cache.listJson(this::load)).isEqualTo(listJson());
    cache.evictAfterCommit("WIDGET-1");

    assertThat(loads).hasValue(2);
    verifyNoInteractions(redis);
  }
}
