package com.ordersphere.shipping.messaging;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.shipping.service.ShipmentService;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RabbitListener(queues = RabbitConfig.ORDER_EVENTS_QUEUE)
public class OrderEventListener {

  private final ShipmentService shipmentService;

  public OrderEventListener(ShipmentService shipmentService) {
    this.shipmentService = shipmentService;
  }

  @RabbitHandler
  public void onOrderCancelled(OrderCancelledEvent event) {
    shipmentService.cancelForOrder(event.getOrderId());
  }
}
