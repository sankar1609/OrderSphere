package com.ordersphere.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;

// Spring Boot configures logging once per JVM while a context is open: close it so the other
// format's test gets a freshly configured logging system.
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    classes = LoggingTestApplication.class,
    properties = {"LOG_FORMAT=json", "spring.application.name=logging-test"})
class JsonLogFormatTest {

  private static final Logger log = LoggerFactory.getLogger(JsonLogFormatTest.class);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void writesOneJsonObjectPerLineWithServiceAndMdc(CapturedOutput output) throws Exception {
    try (var trace = MDC.putCloseable("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
        var user = MDC.putCloseable("user", "alice");
        var order = MDC.putCloseable("orderId", "42")) {
      log.info("order {} confirmed", 42);
    }

    JsonNode line = lineContaining(output, "order 42 confirmed");
    assertThat(line.get("service").asText()).isEqualTo("logging-test");
    assertThat(line.get("level").asText()).isEqualTo("INFO");
    assertThat(line.get("logger").asText()).isEqualTo(JsonLogFormatTest.class.getName());
    assertThat(line.get("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    assertThat(line.get("user").asText()).isEqualTo("alice");
    assertThat(line.get("orderId").asText()).isEqualTo("42");
    assertThat(line.has("@timestamp")).isTrue();
    assertThat(line.has("@version")).isFalse();
    assertThat(line.has("LOG_FORMAT")).isFalse();
  }

  @Test
  void includesAShortenedStackTrace(CapturedOutput output) throws Exception {
    log.error("refund failed", new IllegalStateException("payment-service unavailable"));

    JsonNode line = lineContaining(output, "refund failed");
    assertThat(line.get("level").asText()).isEqualTo("ERROR");
    assertThat(line.get("stack_trace").asText())
        .contains("java.lang.IllegalStateException: payment-service unavailable");
  }

  private JsonNode lineContaining(CapturedOutput output, String text) throws Exception {
    String json =
        Arrays.stream(output.getOut().split("\\R"))
            .filter(l -> l.contains(text))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no log line containing: " + text));
    return mapper.readTree(json);
  }
}
