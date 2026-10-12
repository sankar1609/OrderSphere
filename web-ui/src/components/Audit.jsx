import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { getAuditEntry, getAuditStatus, searchAudit, verifyAuditChain } from "../api";
import { cellStyle, linkButtonStyle } from "../styles";
import { usePageTitle } from "../usePageTitle";

const JAEGER_URL = import.meta.env.VITE_JAEGER_URL ?? "http://localhost:16686";
const PAGE_SIZE = 50;

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : "—";
}

function prettyJson(text) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

/**
 * The audit log (AUDITOR and ADMIN): every domain event any service published, in the order it was
 * recorded, with who caused it. Read-only - there is nothing here that changes an entry. "Verify
 * chain" re-checks that no entry has been altered, removed or reordered since it was written.
 * Filters live in the address (?orderId=42&eventType=...&actor=...), so a filtered view can be
 * linked to (the order page does).
 */
export default function Audit({ token, onUnauthorized }) {
  usePageTitle("Audit");
  const [searchParams, setSearchParams] = useSearchParams();
  const applied = {
    orderId: searchParams.get("orderId") ?? "",
    eventType: searchParams.get("eventType") ?? "",
    actor: searchParams.get("actor") ?? "",
  };
  const appliedKey = `${applied.orderId}|${applied.eventType}|${applied.actor}`;

  const [draft, setDraft] = useState(applied);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(null);
  const [verification, setVerification] = useState(null);
  const [verifying, setVerifying] = useState(false);
  const [detail, setDetail] = useState(null);

  function handleError(err) {
    if (err.status === 401) {
      onUnauthorized();
    } else {
      setError(err.message);
    }
  }

  useEffect(() => {
    setDraft(applied);
    setPage(0);
  }, [appliedKey]);

  useEffect(() => {
    setResult(null);
    searchAudit(token, applied, page, PAGE_SIZE)
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch(handleError);
  }, [token, appliedKey, page]);

  useEffect(() => {
    getAuditStatus(token).then(setStatus).catch(handleError);
  }, [token, verification]);

  function applyFilters(event) {
    event.preventDefault();
    const next = {};
    for (const [key, value] of Object.entries(draft)) {
      if (value.trim() !== "") {
        next[key] = value.trim();
      }
    }
    setSearchParams(next);
  }

  async function verify() {
    setVerifying(true);
    setError(null);
    try {
      setVerification(await verifyAuditChain(token));
    } catch (err) {
      handleError(err);
    } finally {
      setVerifying(false);
    }
  }

  async function toggleDetail(seq) {
    if (detail?.seq === seq) {
      setDetail(null);
      return;
    }
    try {
      setDetail(await getAuditEntry(token, seq));
    } catch (err) {
      handleError(err);
    }
  }

  const lastCheck = verification ?? status?.lastVerification;
  const pages = result ? Math.max(1, Math.ceil(result.total / PAGE_SIZE)) : 1;

  return (
    <div>
      <h2>Audit</h2>

      <div style={{ border: "1px solid #ddd", padding: 12, marginBottom: 16 }}>
        <button type="button" onClick={verify} disabled={verifying}>
          {verifying ? "Verifying..." : "Verify chain"}
        </button>{" "}
        {status && (
          <span>
            {status.entries} entries
            {status.lastAnchor && <> · last anchored at entry {status.lastAnchor.seq}</>}
          </span>
        )}
        {lastCheck && (
          <p role="status" style={{ color: lastCheck.valid ? "green" : "crimson", marginBottom: 0 }}>
            {lastCheck.valid
              ? `Chain intact: ${lastCheck.entriesChecked} entries verified (${formatTime(
                  lastCheck.verifiedAt
                )}).`
              : `CHAIN BROKEN at entry ${lastCheck.firstBrokenSeq}: ${lastCheck.reason} (${formatTime(
                  lastCheck.verifiedAt
                )}).`}
          </p>
        )}
      </div>

      <form onSubmit={applyFilters} style={{ marginBottom: 12 }}>
        <label>
          Order id{" "}
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            value={draft.orderId}
            onChange={(e) => setDraft({ ...draft, orderId: e.target.value })}
            style={{ width: 70 }}
          />
        </label>{" "}
        <label>
          Event type{" "}
          <input
            type="text"
            value={draft.eventType}
            onChange={(e) => setDraft({ ...draft, eventType: e.target.value })}
            placeholder="OrderConfirmedEvent"
            style={{ width: 170 }}
          />
        </label>{" "}
        <label>
          Actor{" "}
          <input
            type="text"
            value={draft.actor}
            onChange={(e) => setDraft({ ...draft, actor: e.target.value })}
            style={{ width: 110 }}
          />
        </label>{" "}
        <button type="submit">Filter</button>{" "}
        <button type="button" onClick={() => setSearchParams({})} style={linkButtonStyle}>
          Clear
        </button>
      </form>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && result === null && <p>Loading...</p>}
      {result && result.total === 0 && <p>No audit entries match.</p>}

      {result && result.total > 0 && (
        <>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <th style={cellStyle}>#</th>
                <th style={cellStyle}>When</th>
                <th style={cellStyle}>Event</th>
                <th style={cellStyle}>Service</th>
                <th style={cellStyle}>Actor</th>
                <th style={cellStyle}>Order</th>
              </tr>
            </thead>
            <tbody>
              {result.items.map((entry) => (
                <tr key={entry.seq}>
                  <td style={cellStyle}>
                    <button
                      type="button"
                      onClick={() => toggleDetail(entry.seq)}
                      style={linkButtonStyle}
                      aria-expanded={detail?.seq === entry.seq}
                      title="Show the recorded event and its hashes"
                    >
                      {entry.seq}
                    </button>
                  </td>
                  <td style={cellStyle}>{formatTime(entry.occurredAt)}</td>
                  <td style={cellStyle}>{entry.eventType}</td>
                  <td style={cellStyle}>{entry.sourceService ?? "—"}</td>
                  <td style={cellStyle}>{entry.actor ?? "—"}</td>
                  <td style={cellStyle}>{entry.orderId ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p>
            {result.total} {result.total === 1 ? "entry" : "entries"} · page {page + 1} of {pages}{" "}
            <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0}>
              Newer
            </button>{" "}
            <button type="button" onClick={() => setPage(page + 1)} disabled={page + 1 >= pages}>
              Older
            </button>
          </p>
        </>
      )}

      {detail && (
        <div
          role="region"
          aria-label={`Audit entry ${detail.seq}`}
          style={{ border: "1px solid #ddd", padding: 12, marginTop: 12 }}
        >
          <h3 style={{ marginTop: 0 }}>
            Entry {detail.seq}: {detail.eventType}
          </h3>
          <p>
            Recorded {formatTime(detail.recordedAt)} · event id {detail.eventId}
            {detail.subjectUsername && <> · about {detail.subjectUsername}</>}
            {detail.traceId && (
              <>
                {" · "}
                <a href={`${JAEGER_URL}/trace/${detail.traceId}`} target="_blank" rel="noreferrer">
                  trace
                </a>
              </>
            )}
          </p>
          <pre style={{ background: "#f6f6f6", padding: 8, overflowX: "auto" }}>
            {prettyJson(detail.payload)}
          </pre>
          <p style={{ fontFamily: "monospace", fontSize: 12, wordBreak: "break-all" }}>
            hash {detail.hash}
            <br />
            previous {detail.prevHash}
          </p>
        </div>
      )}
    </div>
  );
}
