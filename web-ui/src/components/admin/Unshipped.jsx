import { useEffect, useState } from "react";
import { listUnshipped, retryUnshipped } from "../../api";
import { cellStyle, linkButtonStyle } from "../../styles";

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

/**
 * Paid orders with no shipment yet. "Retrying" ones are still being retried automatically; "gave
 * up" ones were rejected by shipping or ran out of attempts and need an admin.
 */
export default function Unshipped({ token, onUnauthorized }) {
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [retrying, setRetrying] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    listUnshipped(token)
      .then((list) => {
        setRows(list);
        setError(null);
      })
      .catch((err) => (err.status === 401 ? onUnauthorized() : setError(err.message)));
  }, [token, reloadKey]);

  async function retry(row) {
    setMessage(null);
    setRetrying(row.orderId);
    try {
      const result = await retryUnshipped(token, row.orderId);
      setMessage(
        result.shipmentId
          ? { tone: "green", text: `Order ${row.orderId}: shipment #${result.shipmentId} created.` }
          : {
              tone: "crimson",
              text: `Order ${row.orderId}: still no shipment - ${result.lastError ?? "no detail"}`,
            }
      );
      setReloadKey((k) => k + 1);
    } catch (err) {
      if (err.status === 401) {
        onUnauthorized();
      } else {
        setMessage({ tone: "crimson", text: err.message });
      }
    } finally {
      setRetrying(null);
    }
  }

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <button type="button" onClick={() => setReloadKey((k) => k + 1)} style={linkButtonStyle}>
          Refresh
        </button>
      </div>
      {message && (
        <p role="status" style={{ color: message.tone }}>
          {message.text}
        </p>
      )}
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && rows === null && <p>Loading...</p>}
      {rows && rows.length === 0 && <p>Every paid order has a shipment.</p>}
      {rows && rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
          <thead>
            <tr>
              <th style={cellStyle}>Order</th>
              <th style={cellStyle}>Customer</th>
              <th style={cellStyle}>Payment</th>
              <th style={cellStyle}>Attempts</th>
              <th style={cellStyle}>State</th>
              <th style={cellStyle}>Last error</th>
              <th style={cellStyle}></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.orderId}>
                <td style={cellStyle}>{row.orderId}</td>
                <td style={cellStyle}>{row.customerUsername}</td>
                <td style={cellStyle}>{row.paymentId ?? "—"}</td>
                <td style={cellStyle}>{row.shipmentAttempts}</td>
                <td style={cellStyle}>
                  {row.retrying ? (
                    <span>
                      retrying
                      <div style={{ fontSize: 12, color: "#555" }}>
                        next {formatTime(row.nextAttemptAt)}
                      </div>
                    </span>
                  ) : (
                    <span style={{ color: "crimson" }}>gave up</span>
                  )}
                </td>
                <td style={{ ...cellStyle, fontSize: 12 }}>{row.lastError ?? "—"}</td>
                <td style={cellStyle}>
                  <button
                    type="button"
                    onClick={() => retry(row)}
                    disabled={retrying === row.orderId}
                  >
                    {retrying === row.orderId ? "Retrying..." : "Retry now"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
