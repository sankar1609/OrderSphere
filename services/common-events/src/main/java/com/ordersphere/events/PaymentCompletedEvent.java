package com.ordersphere.events;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PaymentCompletedEvent extends BaseEvent {

  private final Long paymentId;
  private final Long orderId;
  private final String customerUsername;
  private final BigDecimal amount;
  private final String currency;

  public PaymentCompletedEvent(
      Long paymentId,
      Long orderId,
      String customerUsername,
      BigDecimal amount,
      String currency) {
    super("PaymentCompletedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.customerUsername = customerUsername;
    this.amount = amount;
    this.currency = currency;
  }
}
