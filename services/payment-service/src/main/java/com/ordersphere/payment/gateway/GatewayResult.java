package com.ordersphere.payment.gateway;

public record GatewayResult(GatewayOutcome outcome, String reference) {

  public enum GatewayOutcome {
    SUCCESS,
    DECLINED,
    TRANSIENT_FAILURE
  }
}
