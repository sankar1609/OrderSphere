package com.ordersphere.service;

import com.ordersphere.entity.Product;
import com.ordersphere.event.InventoryEventProducer;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final InventoryEventProducer inventoryEventProducer;
    private final KafkaTemplate kafkaTemplate;

    public InventoryService(ProductRepository productRepository, InventoryEventProducer inventoryEventProducer, KafkaTemplate kafkaTemplate) {
        this.productRepository = productRepository;
        this.inventoryEventProducer = inventoryEventProducer;
        this.kafkaTemplate = kafkaTemplate;
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public void reserveStock(Long productId, int quantity, Long orderId) {

        Product product = getProduct(productId);

        if (product.getQuantity() < quantity) {
            inventoryEventProducer.publishInventoryReserved(
                    new InventoryReservedEvent(orderId, false)
            );

        } else {

            product.setQuantity(product.getQuantity() - quantity);

            inventoryEventProducer.publishInventoryReserved(
                    new InventoryReservedEvent(orderId, true)
            );
        }
    }
}
