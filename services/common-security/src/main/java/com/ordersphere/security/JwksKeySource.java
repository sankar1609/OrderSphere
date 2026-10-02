package com.ordersphere.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public keys fetched from auth-service's JWKS endpoint. Fetched lazily (so services don't depend
 * on auth-service at startup), cached, and re-fetched when a token names a key id that isn't cached
 * yet - that's how a rotated key is picked up. Re-fetches are throttled so a flood of tokens with
 * made-up key ids can't hammer auth-service.
 */
public class JwksKeySource implements Function<String, RSAPublicKey> {

  private static final Logger log = LoggerFactory.getLogger(JwksKeySource.class);
  private static final Duration MIN_REFETCH_INTERVAL = Duration.ofSeconds(10);

  private final URI jwksUri;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Clock clock;
  private final Map<String, RSAPublicKey> keys = new ConcurrentHashMap<>();
  private volatile Instant lastFetch = Instant.EPOCH;

  public JwksKeySource(String jwksUri) {
    this(
        jwksUri,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
        Clock.systemUTC());
  }

  JwksKeySource(String jwksUri, HttpClient httpClient, Clock clock) {
    this.jwksUri = URI.create(jwksUri);
    this.httpClient = httpClient;
    this.clock = clock;
  }

  @Override
  public RSAPublicKey apply(String kid) {
    RSAPublicKey key = keys.get(kid);
    if (key == null && refetchAllowed()) {
      refresh();
      key = keys.get(kid);
    }
    return key;
  }

  private synchronized boolean refetchAllowed() {
    Instant now = clock.instant();
    if (Duration.between(lastFetch, now).compareTo(MIN_REFETCH_INTERVAL) < 0) {
      return false;
    }
    lastFetch = now;
    return true;
  }

  private void refresh() {
    try {
      HttpResponse<String> response =
          httpClient.send(
              HttpRequest.newBuilder(jwksUri).timeout(Duration.ofSeconds(5)).GET().build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        log.warn("JWKS fetch from {} returned HTTP {}", jwksUri, response.statusCode());
        return;
      }
      for (JsonNode jwk : objectMapper.readTree(response.body()).path("keys")) {
        if ("RSA".equals(jwk.path("kty").asText()) && jwk.hasNonNull("kid")) {
          keys.put(
              jwk.get("kid").asText(),
              RsaKeys.fromJwk(jwk.path("n").asText(), jwk.path("e").asText()));
        }
      }
    } catch (Exception ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("JWKS fetch from {} failed: {}", jwksUri, ex.getMessage());
    }
  }
}
