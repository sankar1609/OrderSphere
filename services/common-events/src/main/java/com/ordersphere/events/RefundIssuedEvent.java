package com.ordersphere.events;

import java.math.BigDecimal;
import lombok.Getter;

@Getter
public class RefundIssuedEvent extends BaseEvent implements OrderScoped {

  private final Long paymentId;
  private final Long refundId;
  private final Long orderId;
  private final BigDecimal amount;

  public RefundIssuedEvent(Long paymentId, Long refundId, Long orderId, BigDecimal amount) {
    super("RefundIssuedEvent");
    this.paymentId = paymentId;
    this.refundId = refundId;
    this.orderId = orderId;
    this.amount = amount;
  }
}
