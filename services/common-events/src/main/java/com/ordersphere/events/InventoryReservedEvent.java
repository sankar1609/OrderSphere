package com.ordersphere.events;

import java.util.Map;
import lombok.Getter;

@Getter
public class InventoryReservedEvent extends BaseEvent implements OrderScoped {

  private final Long orderId;
  private final Map<String, Integer> reservedQuantitiesBySku;

  public InventoryReservedEvent(Long orderId, Map<String, Integer> reservedQuantitiesBySku) {
    super("InventoryReservedEvent");
    this.orderId = orderId;
    this.reservedQuantitiesBySku = reservedQuantitiesBySku;
  }
}
