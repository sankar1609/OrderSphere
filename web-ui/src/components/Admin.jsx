import { Navigate, NavLink, Route, Routes } from "react-router";
import Users from "./admin/Users";
import Compensations from "./admin/Compensations";
import Unshipped from "./admin/Unshipped";
import Reconciliation from "./admin/Reconciliation";
import { usePageTitle } from "../usePageTitle";

const TABS = [
  { path: "users", label: "Users & roles" },
  { path: "compensations", label: "Refunds & stock releases" },
  { path: "unshipped", label: "Unshipped orders" },
  { path: "reconciliation", label: "Payment reconciliation" },
];

const tabStyle = ({ isActive }) => ({
  marginRight: 12,
  fontWeight: isActive ? "bold" : "normal",
  textDecoration: isActive ? "none" : "underline",
  color: isActive ? "#000" : "#0645ad",
});

/**
 * ADMIN-only tools, one URL per tab (/admin/users, /admin/compensations, ...). Each tab talks to
 * an admin endpoint the backend restricts to ADMIN. Links and redirects use absolute paths: under
 * the parent's "/admin/*" route a relative "users" resolves against the whole current URL, so it
 * would become /admin/users/users.
 */
export default function Admin({ token, currentUsername, onUnauthorized }) {
  usePageTitle("Admin");

  return (
    <div>
      <h2>Admin</h2>
      <nav aria-label="Admin" style={{ marginBottom: 16 }}>
        {TABS.map((tab) => (
          <NavLink key={tab.path} to={`/admin/${tab.path}`} style={tabStyle}>
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Routes>
        <Route index element={<Navigate to="/admin/users" replace />} />
        <Route
          path="users"
          element={
            <Users token={token} currentUsername={currentUsername} onUnauthorized={onUnauthorized} />
          }
        />
        <Route
          path="compensations"
          element={<Compensations token={token} onUnauthorized={onUnauthorized} />}
        />
        <Route
          path="unshipped"
          element={<Unshipped token={token} onUnauthorized={onUnauthorized} />}
        />
        <Route
          path="reconciliation"
          element={<Reconciliation token={token} onUnauthorized={onUnauthorized} />}
        />
        <Route path="*" element={<Navigate to="/admin/users" replace />} />
      </Routes>
    </div>
  );
}
