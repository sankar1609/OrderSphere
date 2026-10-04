import { useEffect, useState } from "react";
import {
  listReconciliationFindings,
  listReconciliationRuns,
  resolveFinding,
  resyncFinding,
  runReconciliation,
} from "../../api";
import { cellStyle, linkButtonStyle } from "../../styles";
import { formatMoney } from "../../format";

// Types the backend can fix by applying the provider's state; the others need a person.
const RESYNCABLE = ["CHARGED_NOT_RECORDED", "REFUND_NOT_RECORDED", "REFUND_NOT_EXECUTED"];

const TYPE_LABELS = {
  CHARGED_NOT_RECORDED: "Charged, not recorded",
  RECORDED_NOT_CHARGED: "Recorded, never charged",
  AMOUNT_MISMATCH: "Amount mismatch",
  REFUND_NOT_RECORDED: "Refunded, not recorded",
  REFUND_NOT_EXECUTED: "Recorded refund never executed",
  UNKNOWN_SESSION: "Charge for unknown session",
};

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

/**
 * Our payments checked against the payment provider's settlement report. Each mismatch is a
 * finding: re-sync applies the provider's state (the saga then confirms or refunds the order);
 * the rest are closed with a note once someone has looked into them.
 */
export default function Reconciliation({ token, onUnauthorized }) {
  const [status, setStatus] = useState("OPEN");
  const [findings, setFindings] = useState(null);
  const [lastRun, setLastRun] = useState(null);
  const [hours, setHours] = useState(24);
  const [running, setRunning] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [notes, setNotes] = useState({});
  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  function handleError(err, show) {
    if (err.status === 401) {
      onUnauthorized();
    } else {
      show(err.message);
    }
  }

  useEffect(() => {
    setFindings(null);
    listReconciliationFindings(token, status)
      .then((list) => {
        setFindings(list);
        setError(null);
      })
      .catch((err) => handleError(err, setError));
    listReconciliationRuns(token)
      .then((runs) => setLastRun(runs[0] ?? null))
      .catch(() => {});
  }, [token, status, reloadKey]);

  async function run() {
    setMessage(null);
    setRunning(true);
    try {
      const result = await runReconciliation(token, Number(hours));
      setMessage(
        result.status === "SUCCEEDED"
          ? {
              tone: "green",
              text: `Checked ${result.transactions} provider transactions and ${result.paymentsChecked} payments: ${result.findingsOpened} new finding(s), ${result.findingsCleared} cleared.`,
            }
          : { tone: "crimson", text: `Run failed: ${result.error}` }
      );
      setReloadKey((k) => k + 1);
    } catch (err) {
      handleError(err, (text) => setMessage({ tone: "crimson", text }));
    } finally {
      setRunning(false);
    }
  }

  async function act(finding, action) {
    setMessage(null);
    setBusyId(finding.id);
    try {
      if (action === "resync") {
        await resyncFinding(token, finding.id);
        setMessage({ tone: "green", text: `Finding #${finding.id} re-synced from the provider.` });
      } else {
        await resolveFinding(token, finding.id, (notes[finding.id] ?? "").trim());
        setMessage({ tone: "green", text: `Finding #${finding.id} resolved.` });
      }
      setReloadKey((k) => k + 1);
    } catch (err) {
      handleError(err, (text) => setMessage({ tone: "crimson", text }));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <label>
          Check the last{" "}
          <input
            type="number"
            min="1"
            max="744"
            value={hours}
            onChange={(e) => setHours(e.target.value)}
            style={{ width: 60 }}
          />{" "}
          hours
        </label>{" "}
        <button type="button" onClick={run} disabled={running}>
          {running ? "Running..." : "Run reconciliation now"}
        </button>
        {lastRun && (
          <div style={{ fontSize: 12, color: "#555", marginTop: 4 }}>
            Last run #{lastRun.id} {lastRun.status} at {formatTime(lastRun.finishedAt)} -{" "}
            {lastRun.transactions} provider transactions, {lastRun.paymentsChecked} payments checked
            (runs daily at 02:00 too)
          </div>
        )}
      </div>

      <div style={{ marginBottom: 12 }}>
        <label>
          Findings{" "}
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
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
      {!error && findings === null && <p>Loading...</p>}
      {findings && findings.length === 0 && (
        <p>{status === "OPEN" ? "No open findings - our records match the provider's." : "None."}</p>
      )}
      {findings && findings.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 14 }}>
          <thead>
            <tr>
              <th style={cellStyle}>#</th>
              <th style={cellStyle}>Mismatch</th>
              <th style={cellStyle}>Order / payment</th>
              <th style={cellStyle}>Ours</th>
              <th style={cellStyle}>Provider</th>
              <th style={cellStyle}>{status === "OPEN" ? "Action" : "Resolution"}</th>
            </tr>
          </thead>
          <tbody>
            {findings.map((f) => (
              <tr key={f.id}>
                <td style={cellStyle}>{f.id}</td>
                <td style={cellStyle}>
                  {TYPE_LABELS[f.type] ?? f.type}
                  <div style={{ fontSize: 12, color: "#555" }}>{f.detail}</div>
                </td>
                <td style={cellStyle}>
                  {f.orderId ?? "—"} / {f.paymentId ?? "—"}
                  <div style={{ fontSize: 11, color: "#777" }}>{f.checkoutSessionId}</div>
                </td>
                <td style={cellStyle}>
                  {f.ourStatus ?? "—"}
                  {f.ourAmount != null && <div>{formatMoney(f.ourAmount, f.ourCurrency)}</div>}
                </td>
                <td style={cellStyle}>
                  {f.providerStatus ?? "—"}
                  {f.providerAmount != null && (
                    <div>{formatMoney(f.providerAmount, f.providerCurrency)}</div>
                  )}
                </td>
                <td style={cellStyle}>
                  {f.status === "OPEN" ? (
                    <>
                      {RESYNCABLE.includes(f.type) && (
                        <button
                          type="button"
                          onClick={() => act(f, "resync")}
                          disabled={busyId === f.id}
                          style={{ marginBottom: 6 }}
                        >
                          Re-sync from provider
                        </button>
                      )}
                      <div>
                        <input
                          type="text"
                          placeholder="Note"
                          aria-label={`Resolution note for finding ${f.id}`}
                          value={notes[f.id] ?? ""}
                          onChange={(e) => setNotes({ ...notes, [f.id]: e.target.value })}
                          style={{ width: 140 }}
                        />{" "}
                        <button
                          type="button"
                          onClick={() => act(f, "resolve")}
                          disabled={busyId === f.id || !(notes[f.id] ?? "").trim()}
                        >
                          Resolve
                        </button>
                      </div>
                    </>
                  ) : (
                    <span>
                      {f.resolution} by {f.resolvedBy}
                      <div style={{ fontSize: 12, color: "#555" }}>
                        {f.note} ({formatTime(f.resolvedAt)})
                      </div>
                    </span>
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
