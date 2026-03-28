package com.ordersphere.event;

import com.ordersphere.events.ShippingCreatedEvent;
import com.ordersphere.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
public class ShippingEventListener {

    private final OrderService orderService;

    public ShippingEventListener(
            OrderService orderService) {

        this.orderService = orderService;
    }

    @KafkaListener(
            topics = "shipping-created",
            groupId = "order-group"
    )
    public void handleShippingEvent(
            ShippingCreatedEvent event) {

        if(event.isSuccess()) {

            orderService.updateOrderStatus(
                    event.getOrderId(),
                    "COMPLETED"
            );

        } else {

            orderService.updateOrderStatus(
                    event.getOrderId(),
                    "SHIPPING_FAILED"
            );
        }
    }
}
