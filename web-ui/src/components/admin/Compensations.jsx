import { useEffect, useState } from "react";
import { listCompensations, retryCompensation } from "../../api";
import { cellStyle, linkButtonStyle } from "../../styles";

const STATUSES = ["FAILED", "PENDING", "DONE"];
const TYPE_LABELS = { REFUND_PAYMENT: "Refund", RELEASE_INVENTORY: "Stock release" };

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

/**
 * Refunds and stock releases the order saga owes other services. FAILED ones were rejected or ran
 * out of retries: fix the cause (e.g. bring payment-service back), then Retry.
 */
export default function Compensations({ token, onUnauthorized }) {
  const [status, setStatus] = useState("FAILED");
  const [rows, setRows] = useState(null);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [retrying, setRetrying] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    setRows(null);
    listCompensations(token, status)
      .then((list) => {
        setRows(list);
        setError(null);
      })
      .catch((err) => (err.status === 401 ? onUnauthorized() : setError(err.message)));
  }, [token, status, reloadKey]);

  async function retry(row) {
    setMessage(null);
    setRetrying(row.id);
    try {
      const result = await retryCompensation(token, row.id);
      setMessage(
        result.status === "DONE"
          ? { tone: "green", text: `#${row.id} retried - now DONE.` }
          : {
              tone: "crimson",
              text: `#${row.id} retried - still ${result.status}: ${result.lastError ?? "no detail"}`,
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
        <label>
          Status{" "}
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>{" "}
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
      {rows && rows.length === 0 && <p>Nothing {status.toLowerCase()}.</p>}
      {rows && rows.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
          <thead>
            <tr>
              <th style={cellStyle}>#</th>
              <th style={cellStyle}>Type</th>
              <th style={cellStyle}>Order</th>
              <th style={cellStyle}>Payment</th>
              <th style={cellStyle}>Attempts</th>
              <th style={cellStyle}>Last error</th>
              <th style={cellStyle}>Updated</th>
              <th style={cellStyle}></th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td style={cellStyle}>{row.id}</td>
                <td style={cellStyle}>
                  {TYPE_LABELS[row.type] ?? row.type}
                  {row.reason && <div style={{ fontSize: 12, color: "#555" }}>{row.reason}</div>}
                </td>
                <td style={cellStyle}>{row.orderId}</td>
                <td style={cellStyle}>{row.paymentId ?? "—"}</td>
                <td style={cellStyle}>{row.attempts}</td>
                <td style={{ ...cellStyle, fontSize: 12 }}>{row.lastError ?? "—"}</td>
                <td style={cellStyle}>{formatTime(row.updatedAt)}</td>
                <td style={cellStyle}>
                  {row.status === "FAILED" && (
                    <button type="button" onClick={() => retry(row)} disabled={retrying === row.id}>
                      {retrying === row.id ? "Retrying..." : "Retry"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
