import { useState } from "react";
import { changeUserRole, listUsers } from "../../api";
import { cellStyle, linkButtonStyle } from "../../styles";
import { usePageParams } from "../../usePageParams";
import Pager from "../Pager";
import SearchBox from "../SearchBox";
import { ADMIN_PAGE_SIZE, useAdminList } from "./useAdminList";

const ROLES = ["CUSTOMER", "VENDOR", "ADMIN", "AUDITOR"];

export default function Users({ token, currentUsername, onUnauthorized }) {
  const pageParams = usePageParams();
  const { query, setQuery, setPage } = pageParams;
  const { result, setResult, error, reload } = useAdminList(
    (page) => listUsers(token, { page, size: ADMIN_PAGE_SIZE, query }),
    [token, query],
    pageParams,
    onUnauthorized
  );
  const [pendingRoles, setPendingRoles] = useState({}); // userId -> chosen role
  const [message, setMessage] = useState(null);

  async function save(user) {
    setMessage(null);
    try {
      const updated = await changeUserRole(token, user.id, pendingRoles[user.id]);
      setResult((current) => ({
        ...current,
        items: current.items.map((u) => (u.id === updated.id ? updated : u)),
      }));
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

  return (
    <div>
      <SearchBox
        query={query}
        onSearch={setQuery}
        label="Filter by username"
        placeholder="Part of a username"
        maxLength={50}
      />
      <div style={{ marginBottom: 12 }}>
        <button type="button" onClick={reload} style={linkButtonStyle}>
          Refresh
        </button>
      </div>
      {message && (
        <p role="status" style={{ color: message.tone }}>
          {message.text}
        </p>
      )}
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && result === null && <p>Loading...</p>}
      {result && result.total === 0 && <p>No matching users.</p>}
      {result && result.items.length > 0 && (
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
            {result.items.map((user) => {
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
      {result && result.total > 0 && (
        <Pager result={result} onPage={setPage} noun={["user", "users"]} />
      )}
    </div>
  );
}
