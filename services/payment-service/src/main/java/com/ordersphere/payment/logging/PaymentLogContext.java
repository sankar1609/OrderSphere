package com.ordersphere.payment.logging;

import org.slf4j.MDC;

/**
 * Puts a payment's id and its order's id into the logging context (MDC keys {@value #PAYMENT_ID}
 * and {@value #ORDER_ID}), so the lines logged while working on a payment - in JSON logs,
 * searchable fields in Loki - can be found by payment or by order, next to the order saga's lines
 * in ordersphere-orders.
 *
 * <p>Use each key at the entry point of a unit of work, not in methods it calls: closing removes
 * the key, so a nested scope for the same key would clear it for the rest of the outer one.
 */
public final class PaymentLogContext {

  public static final String PAYMENT_ID = "paymentId";
  public static final String ORDER_ID = "orderId";

  private PaymentLogContext() {}

  public static MDC.MDCCloseable forPayment(Long paymentId) {
    return MDC.putCloseable(PAYMENT_ID, String.valueOf(paymentId));
  }

  public static MDC.MDCCloseable forOrder(Long orderId) {
    return MDC.putCloseable(ORDER_ID, String.valueOf(orderId));
  }

  /** A logging-context scope whose close() doesn't throw, for try-with-resources. */
  public interface Scope extends AutoCloseable {
    @Override
    void close();
  }

  /** Both keys at once, for a unit of work that starts from the payment itself. */
  public static Scope forPayment(Long paymentId, Long orderId) {
    MDC.MDCCloseable payment = forPayment(paymentId);
    MDC.MDCCloseable order = forOrder(orderId);
    return () -> {
      order.close();
      payment.close();
    };
  }
}
