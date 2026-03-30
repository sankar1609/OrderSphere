package com.ordersphere.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.events.ShippingCreatedEvent;
import com.ordersphere.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
public class ShippingEventListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    public ShippingEventListener(
            OrderService orderService, ObjectMapper objectMapper) {

        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "shipping-created",
            groupId = "order-group"
    )
    public void handleShippingEvent(
            String message) {
        ShippingCreatedEvent event = null;
        try {
            event = objectMapper.readValue(message, ShippingCreatedEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

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
