package com.ordersphere.notification.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.ordersphere.notification.domain.TemplateKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateRendererTest {

  @Test
  void rendersTheLowStockAlert() {
    String message =
        new NotificationTemplateRenderer()
            .render(
                TemplateKey.STOCK_LOW,
                Map.of("sku", "SKU-1", "name", "Widget", "available", "2", "threshold", "5"));

    assertThat(message).isEqualTo("Low stock: SKU-1 (Widget) has 2 left - reorder threshold is 5.");
  }

  @Test
  void rendersAFiringAndAResolvedSystemAlert() {
    Map<String, String> alert =
        Map.of(
            "severity", "critical",
            "alertname", "FailedCompensations",
            "summary", "2 refund(s) or stock release(s) have failed for good",
            "description", "Retry them on the Admin page.");
    NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    assertThat(renderer.render(TemplateKey.SYSTEM_ALERT, alert))
        .isEqualTo(
            "[critical] FailedCompensations: 2 refund(s) or stock release(s) have failed for good."
                + " Retry them on the Admin page.");
    assertThat(renderer.render(TemplateKey.SYSTEM_ALERT_RESOLVED, alert))
        .isEqualTo(
            "Resolved: FailedCompensations - 2 refund(s) or stock release(s) have failed for good");
  }
}
