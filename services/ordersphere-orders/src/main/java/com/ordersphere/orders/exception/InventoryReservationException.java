package com.ordersphere.orders.exception;

public class InventoryReservationException extends RuntimeException {

  /** Inventory's own explanation (e.g. "Not enough stock: ..."), safe to show the customer. */
  private final String detail;

  public InventoryReservationException(String message) {
    this(message, null, null);
  }

  public InventoryReservationException(String message, Throwable cause) {
    this(message, cause, null);
  }

  public InventoryReservationException(String message, Throwable cause, String detail) {
    super(message, cause);
    this.detail = detail;
  }

  /** Null when Inventory wasn't reached or gave no explanation. */
  public String getDetail() {
    return detail;
  }
}
