import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { cancelOrder, getOrder, getTracking, listShipmentsForOrder, requestReturn } from "../api";
import { cellStyle } from "../styles";
import { usePageTitle } from "../usePageTitle";
import { formatMoney } from "../format";

// Shipping advances a shipment one stage per ~5s sweep, so refresh at about that pace while one is
// still on its way.
const REFRESH_MS = 5000;
const FINAL_SHIPMENT_STATUSES = ["DELIVERED", "CANCELLED"];

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

/** Shipments for the order, each with its tracking history. */
async function loadShipments(token, orderId) {
  const shipments = await listShipmentsForOrder(token, orderId);
  return Promise.all(
    shipments.map(async (shipment) => ({
      ...shipment,
      tracking: await getTracking(token, shipment.id).catch(() => []),
    }))
  );
}

function stillMoving(order, shipments) {
  if (order?.status !== "CONFIRMED") {
    return false;
  }
  // Paid but no shipment yet: it's being created (or retried), so keep looking.
  return (
    shipments.length === 0 ||
    shipments.some((shipment) => !FINAL_SHIPMENT_STATUSES.includes(shipment.status))
  );
}

/** /orders/:orderId. An id that isn't a number can't exist, so it isn't sent to the backend. */
export default function OrderDetail({ token, onUnauthorized }) {
  const { orderId: orderIdParam } = useParams();
  const orderId = /^\d+$/.test(orderIdParam) ? orderIdParam : null;
  usePageTitle(orderId ? `Order #${orderId}` : "Order not found");
  const [order, setOrder] = useState(null);
  const [shipments, setShipments] = useState([]);
  const [shipmentsError, setShipmentsError] = useState(null);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [returnReason, setReturnReason] = useState("");
  const [returning, setReturning] = useState(false);

  useEffect(() => {
    if (!orderId) {
      return undefined;
    }
    let stopped = false;
    let timer;

    const load = async () => {
      try {
        const loadedOrder = await getOrder(token, orderId);
        let loadedShipments = [];
        try {
          loadedShipments = await loadShipments(token, orderId);
          if (!stopped) {
            setShipmentsError(null);
          }
        } catch (err) {
          if (err.status === 401) {
            throw err;
          }
          if (!stopped) {
            setShipmentsError("Shipment details are unavailable right now.");
          }
        }
        if (stopped) {
          return;
        }
        setOrder(loadedOrder);
        setShipments(loadedShipments);
        if (stillMoving(loadedOrder, loadedShipments)) {
          timer = setTimeout(load, REFRESH_MS);
        }
      } catch (err) {
        if (stopped) {
          return;
        }
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setError(err.message);
        }
      }
    };

    load();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [token, orderId, reloadKey]);

  function handleActionError(err) {
    if (err.status === 401) {
      onUnauthorized();
    } else {
      setActionError(err.message);
    }
  }

  async function handleCancel() {
    setActionError(null);
    setCancelling(true);
    try {
      setOrder(await cancelOrder(token, orderId));
      setConfirmingCancel(false);
      setReloadKey((key) => key + 1);
    } catch (err) {
      handleActionError(err);
    } finally {
      setCancelling(false);
    }
  }

  async function handleReturn(event, shipmentId) {
    event.preventDefault();
    setActionError(null);
    setReturning(true);
    try {
      await requestReturn(token, shipmentId, returnReason.trim());
      setReturnReason("");
      setReloadKey((key) => key + 1);
    } catch (err) {
      handleActionError(err);
    } finally {
      setReturning(false);
    }
  }

  const backLink = <Link to="/orders">← Back to My Orders</Link>;

  if (!orderId) {
    return (
      <div>
        {backLink}
        <p style={{ color: "crimson" }}>Order not found.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div>
        {backLink}
        <p style={{ color: "crimson" }}>{error}</p>
      </div>
    );
  }
  if (!order) {
    return (
      <div>
        {backLink}
        <p>Loading...</p>
      </div>
    );
  }

  const outbound = shipments.find((shipment) => shipment.type === "OUTBOUND");
  const returnShipment = shipments.find((shipment) => shipment.type === "RETURN");
  const delivered = outbound?.status === "DELIVERED";
  const canCancel =
    (order.status === "AWAITING_PAYMENT" || order.status === "CONFIRMED") && !delivered;
  const canReturn = delivered && !returnShipment;

  return (
    <div>
      {backLink}
      <h2>Order #{order.id}</h2>

      <p>
        <strong>Status:</strong> {order.status}
        {order.status === "AWAITING_PAYMENT" && order.checkoutUrl && (
          <>
            {" "}
            <a href={order.checkoutUrl}>Pay now</a>
          </>
        )}
      </p>
      {order.status === "CANCELLED" && order.cancellationReason && (
        <p style={{ color: "#555" }}>{order.cancellationReason}</p>
      )}
      <p>
        <strong>Ship to:</strong> {order.shippingDestination ?? "—"}
        <br />
        <strong>Placed:</strong> {formatTime(order.createdAt)} · <strong>Updated:</strong>{" "}
        {formatTime(order.updatedAt)}
      </p>

      <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 16 }}>
        <thead>
          <tr>
            <th style={cellStyle}>SKU</th>
            <th style={cellStyle}>Quantity</th>
            <th style={cellStyle}>Unit price</th>
            <th style={cellStyle}>Subtotal</th>
          </tr>
        </thead>
        <tbody>
          {order.items.map((item) => (
            <tr key={item.sku}>
              <td style={cellStyle}>{item.sku}</td>
              <td style={cellStyle}>{item.quantity}</td>
              <td style={cellStyle}>{formatMoney(item.unitPrice, order.currency)}</td>
              <td style={cellStyle}>
                {item.unitPrice == null
                  ? "—"
                  : formatMoney(item.unitPrice * item.quantity, order.currency)}
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td style={cellStyle} colSpan={3}>
              <strong>Total</strong>
            </td>
            <td style={cellStyle}>
              <strong>{formatMoney(order.totalAmount, order.currency)}</strong>
            </td>
          </tr>
        </tfoot>
      </table>

      {canCancel && (
        <div style={{ marginBottom: 16 }}>
          {!confirmingCancel ? (
            <button type="button" onClick={() => setConfirmingCancel(true)}>
              Cancel order
            </button>
          ) : (
            <div>
              <p>
                {order.status === "CONFIRMED"
                  ? "Cancel this order? Your payment will be refunded."
                  : "Cancel this order? Nothing has been charged."}
              </p>
              <button type="button" onClick={handleCancel} disabled={cancelling}>
                {cancelling ? "Cancelling..." : "Confirm cancellation"}
              </button>{" "}
              <button type="button" onClick={() => setConfirmingCancel(false)} disabled={cancelling}>
                Keep order
              </button>
            </div>
          )}
        </div>
      )}
      {actionError && <p style={{ color: "crimson" }}>{actionError}</p>}

      <h3>Shipping</h3>
      {shipmentsError && <p style={{ color: "crimson" }}>{shipmentsError}</p>}
      {!shipmentsError && shipments.length === 0 && (
        <p>
          {order.status === "CONFIRMED"
            ? "Preparing your shipment..."
            : "Nothing has shipped for this order."}
        </p>
      )}
      {shipments.map((shipment) => (
        <div key={shipment.id} style={{ border: "1px solid #ddd", padding: 12, marginBottom: 12 }}>
          <p style={{ marginTop: 0 }}>
            <strong>{shipment.type === "RETURN" ? "Return" : "Delivery"}</strong> #{shipment.id} —{" "}
            <strong>{shipment.status}</strong>
            <br />
            {shipment.carrier ?? "—"} · tracking {shipment.trackingNumber ?? "—"}
            {shipment.deliveredAt && <> · delivered {formatTime(shipment.deliveredAt)}</>}
          </p>
          {shipment.tracking.length > 0 && (
            <ol style={{ margin: 0, paddingLeft: 20 }}>
              {shipment.tracking.map((event, index) => (
                <li key={index}>
                  {event.status}
                  {event.location && ` — ${event.location}`} ({formatTime(event.occurredAt)})
                </li>
              ))}
            </ol>
          )}
          {shipment.type === "OUTBOUND" && canReturn && (
            <form onSubmit={(event) => handleReturn(event, shipment.id)} style={{ marginTop: 12 }}>
              <label>
                Request a return — reason
                <input
                  type="text"
                  value={returnReason}
                  onChange={(e) => setReturnReason(e.target.value)}
                  style={{ display: "block", width: "100%" }}
                  required
                />
              </label>
              <button type="submit" disabled={returning} style={{ marginTop: 8 }}>
                {returning ? "Requesting..." : "Request return"}
              </button>
            </form>
          )}
        </div>
      ))}
    </div>
  );
}
