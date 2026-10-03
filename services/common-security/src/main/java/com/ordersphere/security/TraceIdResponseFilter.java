package com.ordersphere.security;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the request's trace id on the response as {@code X-Trace-Id}, so a failure a client sees (in
 * the Web UI, Postman, curl) can be looked up in Jaeger directly. Runs after Spring's server
 * observation filter, which starts the span, and before Spring Security, so 401/403 answers carry
 * it too.
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Trace-Id";

  private final ObjectProvider<Tracer> tracer;

  public TraceIdResponseFilter(ObjectProvider<Tracer> tracer) {
    this.tracer = tracer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Tracer current = tracer.getIfAvailable();
    Span span = current == null ? null : current.currentSpan();
    if (span != null) {
      response.setHeader(HEADER, span.context().traceId());
    }
    chain.doFilter(request, response);
  }
}
