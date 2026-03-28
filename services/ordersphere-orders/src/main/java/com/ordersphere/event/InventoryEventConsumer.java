package com.ordersphere.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class InventoryEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final Logger logger = LoggerFactory.getLogger(InventoryEventConsumer.class);

    public InventoryEventConsumer(OrderService orderService) {
        this.orderService = orderService;
        this.objectMapper = new ObjectMapper();
    }

    @KafkaListener(topics = "inventory-reserved", groupId = "inventory-group")
    public void consumeInventoryEvent(String message) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);
            if (event == null || event.getOrderId() == null) {
                logger.warn("Received empty or invalid InventoryReservedEvent: {}", message);
                return;
            }

            if (event.isSuccess()) {
                orderService.updateOrderStatus(event.getOrderId(), "CONFIRMED");
                logger.info("Inventory reserved for order {}", event.getOrderId());
            } else {
                orderService.updateOrderStatus(event.getOrderId(), "REJECTED");
                logger.info("Inventory reservation rejected for order {}", event.getOrderId());
            }
        } catch (Exception e) {
            logger.error("Failed to process inventory event (payload={}): {}", message, e.getMessage(), e);
            // Don't rethrow to avoid stopping the listener container; handle dead-lettering externally if needed.
        }
    }
}
