package com.ordersphere.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.annotation.DirtiesContext;

/** Without LOG_FORMAT the console keeps Spring Boot's readable pattern. */
// Spring Boot configures logging once per JVM while a context is open: close it so the other
// format's test gets a freshly configured logging system.
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    classes = LoggingTestApplication.class,
    properties = "spring.application.name=logging-test")
class TextLogFormatTest {

  private static final Logger log = LoggerFactory.getLogger(TextLogFormatTest.class);

  @Test
  void defaultsToReadableText(CapturedOutput output) {
    log.info("plain text please");

    String line =
        output
            .getOut()
            .lines()
            .filter(l -> l.contains("plain text please"))
            .findFirst()
            .orElseThrow();
    assertThat(line).doesNotStartWith("{").contains(" INFO ").contains("[logging-test]");
  }
}
