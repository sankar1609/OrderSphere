package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.ordersphere.security.testing.TestJwtIssuer;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

  private final JwtAuthenticationFilter filter =
      new JwtAuthenticationFilter(
          new JwtVerifier(
              kid -> RsaKeys.parsePublicKey(TestJwtIssuer.PUBLIC_KEY), TestJwtIssuer.ISSUER));

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void populatesSecurityContextForValidTokenWithRole() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", TestJwtIssuer.bearer("user-123", "ADMIN"));
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

  @Test
  void namesTheCallerInTheLogContextOnlyWhileHandlingTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", TestJwtIssuer.bearer("alice", "CUSTOMER"));
    AtomicReference<String> userDuringRequest = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> userDuringRequest.set(MDC.get(JwtAuthenticationFilter.MDC_USER)));

    assertThat(userDuringRequest.get()).isEqualTo("alice");
    assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER)).isNull();
  }

  @Test
  void leavesTheLogContextAloneForAnonymousRequests() throws Exception {
    AtomicReference<String> userDuringRequest = new AtomicReference<>("unset");

    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (req, res) -> userDuringRequest.set(MDC.get(JwtAuthenticationFilter.MDC_USER)));

    assertThat(userDuringRequest.get()).isNull();
  }
}
