package com.ordersphere.notification.messaging;

import com.ordersphere.events.DeliveryConfirmedEvent;
import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.ShipmentCreatedEvent;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationService;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to domain events consumed off the broker by creating the matching notification directly
 * via NotificationService - the same method the (admin-guarded) REST endpoint calls, just without
 * an HTTP principal in the picture. One @RabbitListener class + @RabbitHandler per payload type, so
 * all six event types share a single consumer on notification-service.events rather than racing
 * each other as separate listeners on the same queue.
 */
@Component
@RabbitListener(queues = RabbitConfig.EVENTS_QUEUE)
public class DomainEventListener {

  private final NotificationService notificationService;

  public DomainEventListener(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @RabbitHandler
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.ORDER_CONFIRMED,
        Map.of("orderId", event.getOrderId().toString()));
  }

  @RabbitHandler
  public void onOrderCancelled(OrderCancelledEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.ORDER_CANCELLED,
        Map.of("orderId", event.getOrderId().toString()));
  }

  @RabbitHandler
  public void onPaymentCompleted(PaymentCompletedEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.PAYMENT_COMPLETED,
        Map.of(
            "orderId", event.getOrderId().toString(),
            "amount", event.getAmount().toPlainString(),
            "currency", event.getCurrency()));
  }

  @RabbitHandler
  public void onPaymentFailed(PaymentFailedEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.PAYMENT_FAILED,
        Map.of("orderId", event.getOrderId().toString(), "reason", event.getReason()));
  }

  @RabbitHandler
  public void onShipmentCreated(ShipmentCreatedEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.SHIPMENT_CREATED,
        Map.of("orderId", event.getOrderId().toString(), "destination", event.getDestination()));
  }

  @RabbitHandler
  public void onDeliveryConfirmed(DeliveryConfirmedEvent event) {
    notify(
        event.getCustomerUsername(),
        TemplateKey.DELIVERY_CONFIRMED,
        Map.of("orderId", event.getOrderId().toString()));
  }

  private void notify(
      String recipientUsername, TemplateKey templateKey, Map<String, String> variables) {
    notificationService.createNotification(
        new CreateNotificationRequest(
            recipientUsername, NotificationChannel.EMAIL, templateKey, variables));
  }
}
