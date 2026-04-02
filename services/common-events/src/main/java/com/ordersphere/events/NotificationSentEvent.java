package com.ordersphere.events;

public class NotificationSentEvent {

    private Long orderId;
    private String message;

    public NotificationSentEvent() {}

    public NotificationSentEvent(
            Long orderId,
            String message) {

        this.orderId = orderId;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getMessage() {
        return message;
    }
}
