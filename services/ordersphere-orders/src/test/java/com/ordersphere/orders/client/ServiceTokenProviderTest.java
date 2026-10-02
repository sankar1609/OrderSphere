package com.ordersphere.orders.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ServiceTokenProviderTest {

  private static final String TOKEN_JSON =
      "{\"access_token\": \"%s\", \"token_type\": \"Bearer\", \"expires_in\": 300}";

  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final MutableClock clock = new MutableClock();
  private final ServiceTokenProvider provider =
      new ServiceTokenProvider(builder, "orders-service", "s3cret", clock);

  @Test
  void fetchesATokenWithClientCredentialsAndCachesIt() {
    String basic =
        "Basic "
            + Base64.getEncoder()
                .encodeToString("orders-service:s3cret".getBytes(StandardCharsets.UTF_8));
    server
        .expect(ExpectedCount.once(), requestTo("http://auth-service/auth/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", basic))
        .andExpect(content().formDataContains(java.util.Map.of("grant_type", "client_credentials")))
        .andRespond(withSuccess(TOKEN_JSON.formatted("token-1"), MediaType.APPLICATION_JSON));

    assertThat(provider.bearerToken()).isEqualTo("Bearer token-1");
    clock.advanceSeconds(200);
    assertThat(provider.bearerToken()).isEqualTo("Bearer token-1");
    server.verify();
  }

  @Test
  void renewsShortlyBeforeExpiry() {
    server
        .expect(requestTo("http://auth-service/auth/token"))
        .andRespond(withSuccess(TOKEN_JSON.formatted("token-1"), MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://auth-service/auth/token"))
        .andRespond(withSuccess(TOKEN_JSON.formatted("token-2"), MediaType.APPLICATION_JSON));

    assertThat(provider.bearerToken()).isEqualTo("Bearer token-1");
    clock.advanceSeconds(271); // 300s token, renewed 30s early
    assertThat(provider.bearerToken()).isEqualTo("Bearer token-2");
    server.verify();
  }

  @Test
  void rejectedCredentialsSurfaceAsServiceTokenException() {
    server
        .expect(requestTo("http://auth-service/auth/token"))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(provider::bearerToken)
        .isInstanceOf(ServiceTokenProvider.ServiceTokenException.class);
  }

  @Test
  void authServiceMissingFromDiscoverySurfacesAsServiceTokenException() {
    RestClient.Builder failing =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  // What the load-balanced client throws when no auth-service instance is known.
                  throw new IllegalStateException(
                      "Service Instance cannot be null, serviceId: auth-service");
                });
    ServiceTokenProvider unavailable =
        new ServiceTokenProvider(failing, "orders-service", "s3cret", clock);

    assertThatThrownBy(unavailable::bearerToken)
        .isInstanceOf(ServiceTokenProvider.ServiceTokenException.class);
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
