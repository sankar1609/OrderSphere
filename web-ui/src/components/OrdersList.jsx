import { useEffect, useState } from "react";
import { listMyOrders } from "../api";

export default function OrdersList({ token, onLogout }) {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    listMyOrders(token)
      .then(setOrders)
      .catch((err) => setError(err.message));
  }, [token]);

  return (
    <div style={{ maxWidth: 720, margin: "40px auto", fontFamily: "sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>My Orders</h1>
        <button onClick={onLogout}>Log out</button>
      </div>

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

const cellStyle = {
  border: "1px solid #ddd",
  padding: "8px",
  textAlign: "left",
};
