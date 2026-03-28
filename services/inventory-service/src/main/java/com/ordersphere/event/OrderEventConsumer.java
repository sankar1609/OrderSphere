package com.ordersphere.event;

import com.ordersphere.events.OrderCreatedEvent;
import com.ordersphere.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventConsumer {
    private final InventoryService inventoryService;

    public OrderEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }
    @KafkaListener(topics = "order-created", groupId = "inventory-group")
    public void consume(OrderCreatedEvent event) {

        System.out.println("Received order event: " + event.getOrderId());
        inventoryService.reserveStock(
                event.getProductId(),
                event.getQuantity(),
                event.getOrderId());
    }
}
