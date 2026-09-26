package com.ordersphere.notification.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.ordersphere.events.DeliveryConfirmedEvent;
import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.events.OrderConfirmedEvent;
import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.events.ShipmentCreatedEvent;
import com.ordersphere.events.ShipmentInTransitEvent;
import com.ordersphere.events.ShipmentPickedEvent;
import com.ordersphere.notification.domain.NotificationChannel;
import com.ordersphere.notification.domain.TemplateKey;
import com.ordersphere.notification.dto.CreateNotificationRequest;
import com.ordersphere.notification.service.NotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DomainEventListenerTest {

  @Mock private NotificationService notificationService;

  @Test
  void onOrderConfirmedCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onOrderConfirmed(new OrderConfirmedEvent(10L, "alice"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.ORDER_CONFIRMED,
                    Map.of("orderId", "10"))));
  }

  @Test
  void onOrderCancelledCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onOrderCancelled(
        new OrderCancelledEvent(10L, OrderCancelledEvent.Reason.CUSTOMER_REQUESTED, "alice"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.ORDER_CANCELLED,
                    Map.of("orderId", "10"))));
  }

  @Test
  void onPaymentCompletedCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onPaymentCompleted(
        new PaymentCompletedEvent(1L, 10L, "alice", new BigDecimal("39.98"), "USD"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.PAYMENT_COMPLETED,
                    Map.of("orderId", "10", "amount", "39.98", "currency", "USD"))));
  }

  @Test
  void onPaymentFailedCreatesEmailNotificationWithReason() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onPaymentFailed(new PaymentFailedEvent(1L, 10L, "declined", "alice"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.PAYMENT_FAILED,
                    Map.of("orderId", "10", "reason", "declined"))));
  }

  @Test
  void onShipmentCreatedCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onShipmentCreated(
        new ShipmentCreatedEvent(7L, 10L, "alice", "123 Main St, Springfield"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.SHIPMENT_CREATED,
                    Map.of("orderId", "10", "destination", "123 Main St, Springfield"))));
  }

  @Test
  void onShipmentPickedCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onShipmentPicked(new ShipmentPickedEvent(7L, 10L, "alice"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.SHIPMENT_PICKED,
                    Map.of("orderId", "10"))));
  }

  @Test
  void onShipmentInTransitCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onShipmentInTransit(new ShipmentInTransitEvent(7L, 10L, "alice"));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.SHIPMENT_IN_TRANSIT,
                    Map.of("orderId", "10"))));
  }

  @Test
  void onDeliveryConfirmedCreatesEmailNotificationForCustomer() {
    DomainEventListener listener = new DomainEventListener(notificationService);

    listener.onDeliveryConfirmed(new DeliveryConfirmedEvent(7L, 10L, "alice", Instant.now()));

    verify(notificationService)
        .createNotification(
            eq(
                new CreateNotificationRequest(
                    "alice",
                    NotificationChannel.EMAIL,
                    TemplateKey.DELIVERY_CONFIRMED,
                    Map.of("orderId", "10"))));
  }
}
