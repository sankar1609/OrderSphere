package com.ordersphere.orders.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * orders' own identity for calling other services: a SERVICE-role token obtained from auth-service
 * via the OAuth2 client-credentials grant. Cached and renewed shortly before it expires, so a saga
 * step doesn't cost a token round-trip. orders can no longer mint tokens itself.
 */
@Component
public class ServiceTokenProvider {

  /** Renew this long before expiry so a token never lapses mid-call. */
  private static final Duration RENEW_BEFORE_EXPIRY = Duration.ofSeconds(30);

  private final RestClient restClient;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;

  private String cachedToken;
  private Instant renewAt = Instant.EPOCH;

  @Autowired
  public ServiceTokenProvider(
      RestClient.Builder loadBalancedRestClientBuilder,
      @Value("${orders.auth.client-id}") String clientId,
      @Value("${orders.auth.client-secret}") String clientSecret) {
    this(loadBalancedRestClientBuilder, clientId, clientSecret, Clock.systemUTC());
  }

  ServiceTokenProvider(
      RestClient.Builder restClientBuilder, String clientId, String clientSecret, Clock clock) {
    this.restClient = restClientBuilder.baseUrl("http://auth-service").build();
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
  }

  /** {@code "Bearer <token>"}; throws {@link ServiceTokenException} if auth-service refuses. */
  public synchronized String bearerToken() {
    if (cachedToken == null || !clock.instant().isBefore(renewAt)) {
      TokenResponse response = fetch();
      cachedToken = response.accessToken();
      renewAt = clock.instant().plusSeconds(response.expiresIn()).minus(RENEW_BEFORE_EXPIRY);
    }
    return "Bearer " + cachedToken;
  }

  private TokenResponse fetch() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    try {
      TokenResponse response =
          restClient
              .post()
              .uri("/auth/token")
              .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (response == null || response.accessToken() == null) {
        throw new ServiceTokenException("auth-service returned no access token", null);
      }
      return response;
    } catch (ServiceTokenException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      // RestClientException, but also the load balancer's IllegalStateException when auth-service
      // isn't registered in Eureka at that moment - both mean "no token right now" (503), not a
      // bug.
      throw new ServiceTokenException("Could not obtain a service token from auth-service", ex);
    }
  }

  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("expires_in") long expiresIn) {}

  /** auth-service couldn't be reached or rejected orders' client credentials. */
  public static class ServiceTokenException extends RuntimeException {
    public ServiceTokenException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
