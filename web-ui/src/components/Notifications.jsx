import { useEffect, useState } from "react";
import { listNotifications, listPreferences, setPreference } from "../api";
import { cellStyle } from "../styles";
import { usePageTitle } from "../usePageTitle";

const CHANNELS = ["EMAIL", "SMS", "IN_APP", "PUSH"];
const statusColors = { SENT: "green", PENDING: "#0645ad", SKIPPED: "#777", FAILED: "crimson" };

/** The customer's notifications, plus which channels they want them on. */
export default function Notifications({ token, onUnauthorized }) {
  usePageTitle("Notifications");
  const [notifications, setNotifications] = useState(null);
  const [enabled, setEnabled] = useState(null); // channel -> boolean
  const [error, setError] = useState(null);
  const [prefMessage, setPrefMessage] = useState(null);

  function handleError(err, show) {
    if (err.status === 401) {
      onUnauthorized();
    } else {
      show(err.message);
    }
  }

  useEffect(() => {
    listNotifications(token)
      .then((list) =>
        setNotifications([...list].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)))
      )
      .catch((err) => handleError(err, setError));
    listPreferences(token)
      .then((preferences) => {
        // A channel without a saved preference is on.
        const byChannel = Object.fromEntries(CHANNELS.map((channel) => [channel, true]));
        preferences.forEach((p) => {
          byChannel[p.channel] = p.enabled;
        });
        setEnabled(byChannel);
      })
      .catch((err) => handleError(err, setError));
  }, [token]);

  async function toggle(channel) {
    const previous = enabled[channel];
    setEnabled({ ...enabled, [channel]: !previous });
    setPrefMessage(null);
    try {
      await setPreference(token, channel, !previous);
      setPrefMessage({ tone: "green", text: `Saved - ${channel} is ${!previous ? "on" : "off"}.` });
    } catch (err) {
      setEnabled((current) => ({ ...current, [channel]: previous }));
      handleError(err, (text) => setPrefMessage({ tone: "crimson", text }));
    }
  }

  return (
    <div>
      <h2>Notifications</h2>
      {error && <p style={{ color: "crimson" }}>{error}</p>}

      <h3>Channels</h3>
      {enabled === null && !error && <p>Loading...</p>}
      {enabled && (
        <div style={{ marginBottom: 8 }}>
          {CHANNELS.map((channel) => (
            <label key={channel} style={{ marginRight: 16 }}>
              <input
                type="checkbox"
                checked={enabled[channel]}
                onChange={() => toggle(channel)}
              />{" "}
              {channel}
            </label>
          ))}
          <div>
            <small>Messages for a channel that's off are skipped.</small>
          </div>
        </div>
      )}
      {prefMessage && (
        <p role="status" style={{ color: prefMessage.tone }}>
          {prefMessage.text}
        </p>
      )}

      <h3>Inbox</h3>
      {notifications === null && !error && <p>Loading...</p>}
      {notifications && notifications.length === 0 && <p>No notifications yet.</p>}
      {notifications && notifications.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              <th style={cellStyle}>Message</th>
              <th style={cellStyle}>Channel</th>
              <th style={cellStyle}>Status</th>
              <th style={cellStyle}>When</th>
            </tr>
          </thead>
          <tbody>
            {notifications.map((n) => (
              <tr key={n.id}>
                <td style={cellStyle}>{n.message}</td>
                <td style={cellStyle}>{n.channel}</td>
                <td style={{ ...cellStyle, color: statusColors[n.status] }}>{n.status}</td>
                <td style={cellStyle}>{new Date(n.sentAt ?? n.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
