import { useEffect, useState } from "react";
import { listMyOrders } from "../api";
import { cellStyle } from "../styles";

export default function OrdersList({ token, onUnauthorized }) {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);

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
  }, [token]);

  return (
    <div>
      <h2>My Orders</h2>

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
              <th style={cellStyle}>Created</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => (
              <tr key={order.id}>
                <td style={cellStyle}>{order.id}</td>
                <td style={cellStyle}>{order.status}</td>
                <td style={cellStyle}>
                  {order.items.map((item) => `${item.sku} x${item.quantity}`).join(", ")}
                </td>
                <td style={cellStyle}>{new Date(order.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
