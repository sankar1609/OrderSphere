package com.ordersphere.events;

public class InventoryReservedEvent {

    private Long orderId;
    private boolean success;

    public InventoryReservedEvent() {}

    public InventoryReservedEvent(Long orderId, boolean success) {
        this.orderId = orderId;
        this.success = success;
    }

    public Long getOrderId() { return orderId; }
    public boolean isSuccess() { return success; }
}

