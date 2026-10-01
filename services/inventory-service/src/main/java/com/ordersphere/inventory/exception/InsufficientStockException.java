package com.ordersphere.inventory.exception;

import java.util.List;
import java.util.stream.Collectors;

/** An order asked for more of one or more products than is available; nothing was reserved. */
public class InsufficientStockException extends RuntimeException {

  public record Shortage(String sku, int requested, int available) {}

  public InsufficientStockException(List<Shortage> shortages) {
    super(
        "Not enough stock: "
            + shortages.stream()
                .map(
                    s ->
                        s.sku()
                            + " ("
                            + s.requested()
                            + " requested, "
                            + s.available()
                            + " available)")
                .collect(Collectors.joining("; ")));
  }
}
