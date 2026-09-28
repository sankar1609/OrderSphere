package com.ordersphere.dummygateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Requires "Authorization: Bearer &lt;api-key&gt;" on the merchant API. Runs before the handler's
 * arguments are bound, so an unauthenticated caller gets 401 rather than validation errors.
 */
@Configuration
public class ApiKeyInterceptor implements HandlerInterceptor, WebMvcConfigurer {

  private final GatewayProperties properties;

  public ApiKeyInterceptor(GatewayProperties properties) {
    this.properties = properties;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this).addPathPatterns("/api/**");
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    String expected = "Bearer " + properties.apiKey();
    String actual = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (actual != null
        && MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
      return true;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
    return false;
  }
}
