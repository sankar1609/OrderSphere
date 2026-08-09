package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private static final String SECRET = "this-is-a-test-secret-key-that-is-long-enough-for-hs256";

  private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 60_000);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void populatesSecurityContextForValidTokenWithRole() throws Exception {
    String token = jwtTokenProvider.generateToken("user-123", Map.of("role", "ADMIN"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("user-123");
    assertThat(authentication.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_ADMIN");
    verify(chain).doFilter(request, response);
  }

  @Test
  void leavesSecurityContextEmptyWhenNoAuthorizationHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }

  @Test
  void leavesSecurityContextEmptyForInvalidToken() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer not-a-real-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = org.mockito.Mockito.mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    verify(chain).doFilter(request, response);
  }
}
