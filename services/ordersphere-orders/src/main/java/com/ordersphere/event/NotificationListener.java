package com.ordersphere.event;

import com.ordersphere.events.NotificationSentEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    @KafkaListener(
            topics = "notification-sent",
            groupId = "order-group"
    )
    public void handleNotificationEvent(
            NotificationSentEvent event) {

        System.out.println(
                "Notification delivered for order: "
                        + event.getOrderId()
        );
    }
}