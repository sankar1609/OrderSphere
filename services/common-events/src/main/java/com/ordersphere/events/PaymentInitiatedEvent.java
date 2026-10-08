package com.ordersphere.events;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class PaymentInitiatedEvent extends BaseEvent implements OrderScoped {

  private final Long paymentId;
  private final Long orderId;
  private final BigDecimal amount;
  private final String currency;

  public PaymentInitiatedEvent(Long paymentId, Long orderId, BigDecimal amount, String currency) {
    super("PaymentInitiatedEvent");
    this.paymentId = paymentId;
    this.orderId = orderId;
    this.amount = amount;
    this.currency = currency;
  }
}
