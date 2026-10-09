package com.ordersphere.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Lets events through only when the configured LOG_FORMAT ({@code active}) names this appender's
 * {@code format}. Both console appenders stay referenced from the root logger - logback 1.4 warns
 * about (and dumps its status for) an unreferenced appender - and exactly one of them writes.
 */
public class LogFormatFilter extends Filter<ILoggingEvent> {

  private String active = "text";
  private String format;

  public void setActive(String active) {
    this.active = active == null ? "text" : active.trim().toLowerCase();
  }

  public void setFormat(String format) {
    this.format = format;
  }

  @Override
  public FilterReply decide(ILoggingEvent event) {
    return active.equals(format) ? FilterReply.NEUTRAL : FilterReply.DENY;
  }
}
