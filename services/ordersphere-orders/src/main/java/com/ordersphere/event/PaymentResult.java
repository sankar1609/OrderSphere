package com.ordersphere.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersphere.events.PaymentProcessedEvent;
import com.ordersphere.service.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Component
public class PaymentResult {
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    public PaymentResult(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "payment-processed",
            groupId = "order-group"
    )
    public void handlePaymentResult(
            String message) {
        PaymentProcessedEvent event = null;
        try {
            event = objectMapper.readValue(message, PaymentProcessedEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        if (event.isSuccess()) {

            orderService.updateOrderStatus(
                    event.getOrderId(),
                    "CONFIRMED"

            );

        } else {

            orderService.updateOrderStatus(
                    event.getOrderId(),
                    "PAYMENT_FAILED"
            );
        }
    }
}
