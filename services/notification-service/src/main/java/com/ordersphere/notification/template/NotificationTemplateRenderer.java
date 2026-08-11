package com.ordersphere.notification.template;

import com.ordersphere.notification.domain.TemplateKey;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateRenderer {

  private static final Map<TemplateKey, String> TEMPLATES =
      Map.of(
          TemplateKey.ORDER_CONFIRMED, "Your order #{orderId} has been confirmed.",
          TemplateKey.ORDER_CANCELLED, "Your order #{orderId} has been cancelled.",
          TemplateKey.PAYMENT_COMPLETED,
              "Payment of {amount} {currency} for order #{orderId} was successful.",
          TemplateKey.PAYMENT_FAILED, "Payment for order #{orderId} failed: {reason}",
          TemplateKey.SHIPMENT_CREATED,
              "Your shipment for order #{orderId} is on its way to {destination}.",
          TemplateKey.DELIVERY_CONFIRMED, "Your order #{orderId} has been delivered.");

  public String render(TemplateKey templateKey, Map<String, String> variables) {
    String template = TEMPLATES.get(templateKey);
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      template = template.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return template;
  }
}
