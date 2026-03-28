package com.ordersphere.event;

import com.ordersphere.events.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishInventoryReserved(InventoryReservedEvent event) {
        kafkaTemplate.send("inventory-processed", event);
        kafkaTemplate.send("inventory-reserved", event);
    }
}
