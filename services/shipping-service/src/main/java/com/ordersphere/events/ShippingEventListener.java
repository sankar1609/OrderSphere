package com.ordersphere.events;

import com.ordersphere.service.ShippingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShippingEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ShippingService shippingService;

    public ShippingEventListener(
            KafkaTemplate<String, Object> kafkaTemplate,
            ShippingService shippingService) {

        this.kafkaTemplate = kafkaTemplate;
        this.shippingService = shippingService;
    }

    @KafkaListener(
            topics = "payment-processed",
            groupId = "shipping-group"
    )
    public void handlePaymentEvent(
            PaymentProcessedEvent event) {

        if(event.isSuccess()) {

            boolean shippingSuccess =
                    shippingService.createShipment(
                            event.getOrderId()
                    );

            ShippingCreatedEvent shippingEvent =
                    new ShippingCreatedEvent(
                            event.getOrderId(),
                            shippingSuccess
                    );

            kafkaTemplate.send(
                    "shipping-created",
                    shippingEvent
            );
        }
    }
}
