import { useState } from "react";
import Users from "./admin/Users";
import Compensations from "./admin/Compensations";
import Unshipped from "./admin/Unshipped";
import Reconciliation from "./admin/Reconciliation";

const TABS = [
  { id: "users", label: "Users & roles" },
  { id: "compensations", label: "Refunds & stock releases" },
  { id: "unshipped", label: "Unshipped orders" },
  { id: "reconciliation", label: "Payment reconciliation" },
];

/** ADMIN-only tools. Each tab talks to an admin endpoint the backend restricts to ADMIN. */
export default function Admin({ token, currentUsername, onUnauthorized }) {
  const [tab, setTab] = useState("users");

  return (
    <div>
      <h2>Admin</h2>
      <div style={{ marginBottom: 16 }}>
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            disabled={tab === t.id}
            style={{ marginRight: 8 }}
          >
            {t.label}
          </button>
        ))}
      </div>
      {tab === "users" && (
        <Users token={token} currentUsername={currentUsername} onUnauthorized={onUnauthorized} />
      )}
      {tab === "compensations" && <Compensations token={token} onUnauthorized={onUnauthorized} />}
      {tab === "unshipped" && <Unshipped token={token} onUnauthorized={onUnauthorized} />}
      {tab === "reconciliation" && (
        <Reconciliation token={token} onUnauthorized={onUnauthorized} />
      )}
    </div>
  );
}
