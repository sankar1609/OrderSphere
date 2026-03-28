package com.ordersphere.events;

public class ShippingCreatedEvent {

    private Long orderId;
    private boolean success;

    public ShippingCreatedEvent() {}

    public ShippingCreatedEvent(Long orderId,
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
