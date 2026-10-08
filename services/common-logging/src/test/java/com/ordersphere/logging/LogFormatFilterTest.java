package com.ordersphere.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.spi.FilterReply;
import org.junit.jupiter.api.Test;

class LogFormatFilterTest {

  @Test
  void passesOnlyTheActiveFormat() {
    assertThat(filter("json", "json").decide(new LoggingEvent())).isEqualTo(FilterReply.NEUTRAL);
    assertThat(filter("json", "text").decide(new LoggingEvent())).isEqualTo(FilterReply.DENY);
  }

  @Test
  void isCaseInsensitiveAndDefaultsToText() {
    assertThat(filter(" JSON ", "json").decide(new LoggingEvent())).isEqualTo(FilterReply.NEUTRAL);
    assertThat(filter(null, "text").decide(new LoggingEvent())).isEqualTo(FilterReply.NEUTRAL);
  }

  private LogFormatFilter filter(String active, String format) {
    LogFormatFilter filter = new LogFormatFilter();
    filter.setActive(active);
    filter.setFormat(format);
    return filter;
  }
}
