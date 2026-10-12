package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsScrapeTokenFilterTest {

  private static final String TOKEN = "scrape-secret";

  private static int status(String configuredToken, String path, String authorization)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    if (authorization != null) {
      request.addHeader("Authorization", authorization);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    new MetricsScrapeTokenFilter(configuredToken).doFilter(request, response, chain);
    // The chain records the request only when the filter let it through.
    return chain.getRequest() != null ? 200 : response.getStatus();
  }

  @Test
  void letsAScrapeWithTheTokenThrough() throws Exception {
    assertThat(status(TOKEN, "/actuator/prometheus", "Bearer " + TOKEN)).isEqualTo(200);
  }

  @Test
  void refusesAScrapeWithoutOrWithTheWrongToken() throws Exception {
    assertThat(status(TOKEN, "/actuator/prometheus", null)).isEqualTo(401);
    assertThat(status(TOKEN, "/actuator/prometheus", "Bearer wrong")).isEqualTo(401);
    assertThat(status(TOKEN, "/actuator/prometheus", TOKEN)).isEqualTo(401);
  }

  @Test
  void answersNobodyWhenNoTokenIsConfigured() throws Exception {
    assertThat(status("", "/actuator/prometheus", "Bearer ")).isEqualTo(401);
    assertThat(status(null, "/actuator/prometheus", "Bearer anything")).isEqualTo(401);
  }

  @Test
  void leavesEveryOtherPathAlone() throws Exception {
    assertThat(status(TOKEN, "/actuator/health", null)).isEqualTo(200);
    assertThat(status(TOKEN, "/orders", null)).isEqualTo(200);
  }
}
