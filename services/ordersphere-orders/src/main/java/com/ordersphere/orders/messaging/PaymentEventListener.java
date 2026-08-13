package com.ordersphere.orders.messaging;

import com.ordersphere.events.PaymentCompletedEvent;
import com.ordersphere.events.PaymentFailedEvent;
import com.ordersphere.orders.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * A single listener container on RabbitConfig.PAYMENT_EVENTS_QUEUE, dispatching by payload type
 * via @RabbitHandler. Two independent @RabbitListener methods on the same queue would instead
 * create two competing consumers - RabbitMQ round-robins deliveries between them rather than
 * routing by type, so a PaymentFailedEvent could land on a method expecting PaymentCompletedEvent.
 */
@Component
@RabbitListener(queues = RabbitConfig.PAYMENT_EVENTS_QUEUE)
public class PaymentEventListener {

  private final OrderService orderService;

  public PaymentEventListener(OrderService orderService) {
    this.orderService = orderService;
  }

  @RabbitHandler
  public void onPaymentCompleted(PaymentCompletedEvent event) {
    orderService.onPaymentEvent(event.getOrderId(), true);
  }

  @RabbitHandler
  public void onPaymentFailed(PaymentFailedEvent event) {
    orderService.onPaymentEvent(event.getOrderId(), false);
  }
}
