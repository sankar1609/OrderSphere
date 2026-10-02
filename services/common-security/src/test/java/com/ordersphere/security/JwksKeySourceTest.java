package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.security.testing.TestJwtIssuer;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwksKeySourceTest {

  private final List<Map<String, Object>> published = new ArrayList<>();
  private final AtomicInteger fetches = new AtomicInteger();
  private volatile long responseDelayMillis = 0;
  private HttpServer server;
  private String uri;

  @BeforeEach
  void startServer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/jwks",
        exchange -> {
          fetches.incrementAndGet();
          try {
            Thread.sleep(responseDelayMillis);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          byte[] body =
              new ObjectMapper()
                  .writeValueAsString(Map.of("keys", published))
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void concurrentFirstRequestsAllGetTheKeyFromASingleFetch() throws Exception {
    published.add(
        RsaKeys.toJwk(TestJwtIssuer.KEY_ID, RsaKeys.parsePublicKey(TestJwtIssuer.PUBLIC_KEY)));
    responseDelayMillis = 300; // the first fetch is still in flight when the others arrive
    JwksKeySource source = new JwksKeySource(uri);

    List<Thread> threads = new ArrayList<>();
    List<RSAPublicKey> found = java.util.Collections.synchronizedList(new ArrayList<>());
    for (int i = 0; i < 8; i++) {
      threads.add(new Thread(() -> found.add(source.apply(TestJwtIssuer.KEY_ID))));
    }
    threads.forEach(Thread::start);
    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(found).hasSize(8).doesNotContainNull();
    assertThat(fetches.get()).isEqualTo(1);
  }

  @Test
  void fetchesLazilyAndVerifiesTokensWithThePublishedKey() {
    published.add(
        RsaKeys.toJwk(TestJwtIssuer.KEY_ID, RsaKeys.parsePublicKey(TestJwtIssuer.PUBLIC_KEY)));
    JwksKeySource source = new JwksKeySource(uri);
    JwtVerifier verifier = new JwtVerifier(source, TestJwtIssuer.ISSUER);
    assertThat(fetches).hasValue(0);

    assertThat(verifier.verify(TestJwtIssuer.token("alice", "CUSTOMER")).getSubject())
        .isEqualTo("alice");
    verifier.verify(TestJwtIssuer.token("bob", "CUSTOMER"));

    assertThat(fetches).hasValue(1);
  }

  @Test
  void refetchesForARotatedKeyButThrottlesUnknownKids() throws Exception {
    MutableClock clock = new MutableClock();
    JwksKeySource source = new JwksKeySource(uri, HttpClient.newHttpClient(), clock);
    RSAPublicKey rotated = newKey();

    assertThat(source.apply("new-key")).isNull(); // not published yet: first fetch
    published.add(RsaKeys.toJwk("new-key", rotated));
    assertThat(source.apply("new-key")).isNull(); // within the throttle window: no refetch
    clock.advanceSeconds(11);
    assertThat(source.apply("new-key")).isEqualTo(rotated); // rotated key picked up

    assertThat(fetches).hasValue(2);
  }

  private static RSAPublicKey newKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return (RSAPublicKey) generator.generateKeyPair().getPublic();
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advanceSeconds(long seconds) {
      now = now.plusSeconds(seconds);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
