package com.ordersphere.shipping.messaging;

import static org.mockito.Mockito.verify;

import com.ordersphere.events.OrderCancelledEvent;
import com.ordersphere.shipping.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

  @Mock private ShipmentService shipmentService;

  @Test
  void onOrderCancelledHaltsAnyShipmentForThatOrder() {
    OrderEventListener listener = new OrderEventListener(shipmentService);

    listener.onOrderCancelled(
        new OrderCancelledEvent(10L, OrderCancelledEvent.Reason.CUSTOMER_REQUESTED, "alice"));

    verify(shipmentService).cancelForOrder(10L);
  }
}
