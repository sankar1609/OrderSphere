package com.ordersphere.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * What Alertmanager posts to a webhook receiver: one group of alerts. Only the fields the
 * notification needs are read; the rest of Alertmanager's payload is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlertmanagerWebhook(List<Alert> alerts) {

  /** {@code status} is {@code firing} or {@code resolved}. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Alert(String status, Map<String, String> labels, Map<String, String> annotations) {

    public boolean resolved() {
      return "resolved".equalsIgnoreCase(status);
    }
  }
}
