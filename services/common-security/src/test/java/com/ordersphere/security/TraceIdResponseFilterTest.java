package com.ordersphere.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdResponseFilterTest {

  private static MockHttpServletResponse run(Tracer tracer) throws Exception {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (tracer != null) {
      beans.addBean("tracer", tracer);
    }
    MockHttpServletResponse response = new MockHttpServletResponse();
    new TraceIdResponseFilter(beans.getBeanProvider(Tracer.class))
        .doFilter(new MockHttpServletRequest("GET", "/orders"), response, new MockFilterChain());
    return response;
  }

  @Test
  void putsTheCurrentTraceIdOnTheResponse() throws Exception {
    Tracer tracer = mock(Tracer.class);
    Span span = mock(Span.class);
    TraceContext context = mock(TraceContext.class);
    when(tracer.currentSpan()).thenReturn(span);
    when(span.context()).thenReturn(context);
    when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");

    assertThat(run(tracer).getHeader("X-Trace-Id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
  }

  @Test
  void addsNothingWithoutASpanOrATracer() throws Exception {
    assertThat(run(mock(Tracer.class)).getHeader("X-Trace-Id")).isNull();
    assertThat(run(null).getHeader("X-Trace-Id")).isNull();
  }
}
