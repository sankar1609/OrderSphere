import { useEffect, useState } from "react";
import { getOrder, listMyOrders } from "../api";
import { cellStyle } from "../styles";
import { formatMoney } from "../format";

const POLL_INTERVAL_MS = 2000;
const POLL_ATTEMPTS = 15;

/**
 * After the payment provider sends the customer back, the order settles asynchronously (webhook →
 * payment-service → event → orders saga). Poll that one order until it leaves AWAITING_PAYMENT.
 */
function usePaymentReturn(token, paymentReturn, onSettled, onUnauthorized) {
  const [message, setMessage] = useState(null);

  useEffect(() => {
    if (!paymentReturn) {
      return undefined;
    }
    const { orderId, outcome } = paymentReturn;
    setMessage(
      outcome === "success"
        ? { tone: "info", text: `Payment received for order #${orderId}. Confirming your order...` }
        : { tone: "info", text: `Payment for order #${orderId} was cancelled. Updating your order...` }
    );

    let attempts = 0;
    let timer;
    let stopped = false;
    const poll = async () => {
      try {
        const order = await getOrder(token, orderId);
        if (stopped) {
          return;
        }
        if (order.status !== "AWAITING_PAYMENT") {
          setMessage(
            order.status === "CONFIRMED"
              ? { tone: "success", text: `Order #${orderId} is paid and confirmed.` }
              : { tone: "error", text: `Order #${orderId} was cancelled - no payment was taken.` }
          );
          onSettled();
          return;
        }
      } catch (err) {
        if (err.status === 401) {
          onUnauthorized();
          return;
        }
      }
      attempts += 1;
      if (attempts < POLL_ATTEMPTS) {
        timer = setTimeout(poll, POLL_INTERVAL_MS);
      } else {
        setMessage({
          tone: "info",
          text: `Order #${orderId} is still being processed - check back in a moment.`,
        });
      }
    };
    poll();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [token, paymentReturn]);

  return message;
}

const toneColors = { info: "#0645ad", success: "green", error: "crimson" };

export default function OrdersList({ token, onUnauthorized, paymentReturn }) {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const paymentMessage = usePaymentReturn(
    token,
    paymentReturn,
    () => setRefreshKey((key) => key + 1),
    onUnauthorized
  );

  useEffect(() => {
    listMyOrders(token)
      .then(setOrders)
      .catch((err) => {
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setError(err.message);
        }
      });
  }, [token, refreshKey]);

  return (
    <div>
      <h2>My Orders</h2>

      {paymentMessage && (
        <p role="status" style={{ color: toneColors[paymentMessage.tone] }}>
          {paymentMessage.text}
        </p>
      )}
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && orders === null && <p>Loading...</p>}
      {orders && orders.length === 0 && <p>No orders yet.</p>}

      {orders && orders.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              <th style={cellStyle}>Order #</th>
              <th style={cellStyle}>Status</th>
              <th style={cellStyle}>Items</th>
              <th style={cellStyle}>Total</th>
              <th style={cellStyle}>Created</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td style={cellStyle}>{order.id}</td>
                <td style={cellStyle}>
                  {order.status}
                  {order.status === "AWAITING_PAYMENT" && order.checkoutUrl && (
                    <>
                      {" "}
                      <a href={order.checkoutUrl}>Pay now</a>
                    </>
                  )}
                </td>
                <td style={cellStyle}>
                  {order.items.map((item) => `${item.sku} x${item.quantity}`).join(", ")}
                </td>
                <td style={cellStyle}>{formatMoney(order.totalAmount, order.currency)}</td>
                <td style={cellStyle}>{new Date(order.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
