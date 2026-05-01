package com.ordersphere.listener;


import com.ordersphere.events.NotificationSentEvent;
import com.ordersphere.events.ShippingCreatedEvent;
import com.ordersphere.service.NotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEventListener(
            NotificationService notificationService,
            KafkaTemplate<String, Object> kafkaTemplate) {

        this.notificationService = notificationService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "shipping-created",
            groupId = "notification-group"
    )
    public void handleShippingEvent(
            @Payload ShippingCreatedEvent event) {

        try {
            log.info("Received shipping event for order: {}", event.getOrderId());

            if (event.isSuccess()) {

                String message =
                        notificationService.sendNotification(
                                event.getOrderId()
                        );

                NotificationSentEvent notificationEvent =
                        new NotificationSentEvent(
                                event.getOrderId(),
                                message
                        );

                kafkaTemplate.send(
                        "notification-sent",
                        notificationEvent
                );

                log.info("Notification sent for order: {}", event.getOrderId());
            } else {
                log.warn("Shipping event failed for order: {}", event.getOrderId());
            }
        } catch (Exception e) {
            log.error("Error handling shipping event for order: {}", event.getOrderId(), e);
            throw new RuntimeException("Failed to process shipping event", e);
        }
    }
}