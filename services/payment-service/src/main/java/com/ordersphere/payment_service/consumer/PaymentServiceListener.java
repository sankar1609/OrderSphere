package com.ordersphere.payment_service.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.events.InventoryReservedEvent;
import com.ordersphere.events.PaymentProcessedEvent;
import com.ordersphere.payment_service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceListener {
    private PaymentService paymentService;
    private KafkaTemplate kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentServiceListener(PaymentService paymentService, KafkaTemplate kafkaTemplate, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-processed", groupId = "payment-group")
    public void handleInventoryResult(
            String message) {
        InventoryReservedEvent event = null;
        try {
            event = objectMapper.readValue(message, InventoryReservedEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (event.isSuccess()) {

            boolean paymentSuccess =
                    paymentService.processPayment(
                            event.getOrderId()
                    );

            PaymentProcessedEvent paymentEvent =
                    new PaymentProcessedEvent(
                            event.getOrderId(),
                            paymentSuccess
                    );

            kafkaTemplate.send(
                    "payment-processed",
                    paymentEvent
            );
        }

    }
}
