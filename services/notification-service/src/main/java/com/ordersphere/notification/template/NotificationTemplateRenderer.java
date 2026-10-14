package com.ordersphere.notification.template;

import com.ordersphere.notification.domain.TemplateKey;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateRenderer {

  private static final Map<TemplateKey, String> TEMPLATES =
      Map.ofEntries(
          Map.entry(TemplateKey.ORDER_CONFIRMED, "Your order #{orderId} has been confirmed."),
          Map.entry(TemplateKey.ORDER_CANCELLED, "Your order #{orderId} has been cancelled."),
          Map.entry(
              TemplateKey.PAYMENT_COMPLETED,
              "Payment of {amount} {currency} for order #{orderId} was successful."),
          Map.entry(TemplateKey.PAYMENT_FAILED, "Payment for order #{orderId} failed: {reason}"),
          Map.entry(
              TemplateKey.SHIPMENT_CREATED,
              "Your shipment for order #{orderId} is on its way to {destination}."),
          Map.entry(
              TemplateKey.SHIPMENT_PICKED,
              "Your shipment for order #{orderId} has been picked up and is on its way."),
          Map.entry(
              TemplateKey.SHIPMENT_IN_TRANSIT, "Your shipment for order #{orderId} is in transit."),
          Map.entry(TemplateKey.DELIVERY_CONFIRMED, "Your order #{orderId} has been delivered."),
          Map.entry(
              TemplateKey.STOCK_LOW,
              "Low stock: {sku} ({name}) has {available} left - reorder threshold is {threshold}."),
          // Alerts from Prometheus, delivered by Alertmanager (AlertWebhookController).
          Map.entry(TemplateKey.SYSTEM_ALERT, "[{severity}] {alertname}: {summary}. {description}"),
          Map.entry(TemplateKey.SYSTEM_ALERT_RESOLVED, "Resolved: {alertname} - {summary}"));

  public String render(TemplateKey templateKey, Map<String, String> variables) {
    String template = TEMPLATES.get(templateKey);
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      template = template.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return template;
  }
}
