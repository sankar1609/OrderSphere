package com.ordersphere.notification.channel;

public record DeliveryResult(Outcome outcome) {

  public enum Outcome {
    SUCCESS,
    PERMANENT_FAILURE,
    TRANSIENT_FAILURE
  }
}
