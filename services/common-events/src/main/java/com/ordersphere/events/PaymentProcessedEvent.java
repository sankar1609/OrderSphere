package com.ordersphere.events;

public class PaymentProcessedEvent {

    private Long orderId;
    private boolean success;

    public PaymentProcessedEvent() {}

    public PaymentProcessedEvent(
            Long orderId,
            boolean success) {

        this.orderId = orderId;
        this.success = success;
    }

    public Long getOrderId() {
        return orderId;
    }

    public boolean isSuccess() {
        return success;
    }
}
