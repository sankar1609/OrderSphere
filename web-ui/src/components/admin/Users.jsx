import { useEffect, useState } from "react";
import { changeUserRole, listUsers } from "../../api";
import { cellStyle, linkButtonStyle } from "../../styles";

const ROLES = ["CUSTOMER", "VENDOR", "ADMIN", "AUDITOR"];

export default function Users({ token, currentUsername, onUnauthorized }) {
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);
  const [filter, setFilter] = useState("");
  const [pendingRoles, setPendingRoles] = useState({}); // userId -> chosen role
  const [message, setMessage] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    listUsers(token)
      .then((list) => {
        setUsers(list);
        setError(null);
      })
      .catch((err) => (err.status === 401 ? onUnauthorized() : setError(err.message)));
  }, [token, reloadKey]);

  async function save(user) {
    setMessage(null);
    try {
      const updated = await changeUserRole(token, user.id, pendingRoles[user.id]);
      setUsers((current) => current.map((u) => (u.id === updated.id ? updated : u)));
      setPendingRoles(({ [user.id]: _, ...rest }) => rest);
      setMessage({
        tone: "green",
        text: `${updated.username} is now ${updated.role}. Their sessions were ended; the new role applies when they next log in.`,
      });
    } catch (err) {
      if (err.status === 401) {
        onUnauthorized();
      } else {
        setMessage({ tone: "crimson", text: err.message });
      }
    }
  }

  const visible = users?.filter((u) =>
    u.username.toLowerCase().includes(filter.trim().toLowerCase())
  );

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <label>
          Filter by username{" "}
          <input type="text" value={filter} onChange={(e) => setFilter(e.target.value)} />
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
      {!error && users === null && <p>Loading...</p>}
      {visible && visible.length === 0 && <p>No matching users.</p>}
      {visible && visible.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              <th style={cellStyle}>Id</th>
              <th style={cellStyle}>Username</th>
              <th style={cellStyle}>Joined</th>
              <th style={cellStyle}>Role</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((user) => {
              const isSelf = user.username === currentUsername;
              const chosen = pendingRoles[user.id] ?? user.role;
              return (
                <tr key={user.id}>
                  <td style={cellStyle}>{user.id}</td>
                  <td style={cellStyle}>
                    {user.username}
                    {isSelf && <span style={{ color: "#555" }}> (you)</span>}
                  </td>
                  <td style={cellStyle}>
                    {user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "—"}
                  </td>
                  <td style={cellStyle}>
                    <select
                      value={chosen}
                      disabled={isSelf}
                      title={isSelf ? "You can't change your own role" : undefined}
                      aria-label={`Role for ${user.username}`}
                      onChange={(e) =>
                        setPendingRoles({ ...pendingRoles, [user.id]: e.target.value })
                      }
                    >
                      {ROLES.map((role) => (
                        <option key={role} value={role}>
                          {role}
                        </option>
                      ))}
                    </select>{" "}
                    {!isSelf && chosen !== user.role && (
                      <button type="button" onClick={() => save(user)}>
                        Save
                      </button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}
