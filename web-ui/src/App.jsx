import { useEffect, useState } from "react";
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from "react-router";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";
import ProductsList from "./components/ProductsList";
import PlaceOrder from "./components/PlaceOrder";
import ManageProducts from "./components/ManageProducts";
import OrderDetail from "./components/OrderDetail";
import Notifications from "./components/Notifications";
import Admin from "./components/Admin";
import { configureAuth, logout } from "./api";
import { canManageProducts, isAdmin, roleOf, usernameOf } from "./auth";
import { usePageTitle } from "./usePageTitle";

function readStored(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStored(key, value) {
  try {
    if (value) {
      localStorage.setItem(key, value);
    } else {
      localStorage.removeItem(key);
    }
  } catch {
    // e.g. private browsing with storage disabled — the session just won't survive a reload
  }
}

/**
 * "/" - where the payment provider sends the customer back, with
 * ?payment=success|cancelled&orderId=N. Moves on to My Orders with the outcome in router state, so
 * the query leaves the address bar and a refresh doesn't replay it.
 */
function Home() {
  const params = new URLSearchParams(useLocation().search);
  const outcome = params.get("payment");
  const orderId = params.get("orderId");
  const paymentReturn = outcome && orderId ? { outcome, orderId } : null;
  return <Navigate to="/orders" replace state={paymentReturn ? { paymentReturn } : null} />;
}

/** Sends a logged-out visitor to the login page, remembering where they were going. */
function RequireAuth({ token }) {
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

/**
 * Pages for some roles only. Without the role you land on My Orders - the same as the tab simply
 * not being offered; the backend still authorizes every call.
 */
function RequireRole({ allowed }) {
  return allowed ? <Outlet /> : <Navigate to="/orders" replace />;
}

/** The login/register pages: once logged in, go where the visitor was headed (or My Orders). */
function LoggedOutOnly({ token }) {
  const location = useLocation();
  if (token) {
    const from = location.state?.from;
    return <Navigate to={from ? `${from.pathname}${from.search}` : "/orders"} replace />;
  }
  return <Outlet />;
}

const navLinkStyle = ({ isActive }) => ({
  marginRight: 12,
  fontWeight: isActive ? "bold" : "normal",
  textDecoration: isActive ? "none" : "underline",
  color: isActive ? "#000" : "#0645ad",
});

function Layout({ showManageProducts, showAdmin, onLogout }) {
  return (
    <div style={{ maxWidth: 720, margin: "40px auto", fontFamily: "sans-serif" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1>OrderSphere</h1>
        <button type="button" onClick={onLogout}>
          Log out
        </button>
      </div>
      <nav aria-label="Main" style={{ marginBottom: 20 }}>
        <NavLink to="/orders" style={navLinkStyle}>
          My Orders
        </NavLink>
        <NavLink to="/products" style={navLinkStyle}>
          Products
        </NavLink>
        <NavLink to="/place-order" style={navLinkStyle}>
          Place Order
        </NavLink>
        <NavLink to="/notifications" style={navLinkStyle}>
          Notifications
        </NavLink>
        {showManageProducts && (
          <NavLink to="/manage-products" style={navLinkStyle}>
            Manage Products
          </NavLink>
        )}
        {showAdmin && (
          <NavLink to="/admin" style={navLinkStyle}>
            Admin
          </NavLink>
        )}
      </nav>
      <Outlet />
    </div>
  );
}

function NotFound() {
  usePageTitle("Page not found");
  return (
    <div>
      <h2>Page not found</h2>
      <p>
        There's nothing at this address. <Link to="/orders">Go to My Orders</Link>
      </p>
    </div>
  );
}

export default function App() {
  const [token, setToken] = useState(() => readStored("token"));
  const [refreshToken, setRefreshToken] = useState(() => readStored("refreshToken"));
  const [sessionMessage, setSessionMessage] = useState(null);
  const navigate = useNavigate();

  // Reached only once the refresh token can't renew the session either (see api.js). RequireAuth
  // then shows the login page and remembers this page, so logging in again returns to it.
  function handleUnauthorized() {
    setToken(null);
    setRefreshToken(null);
    setSessionMessage("Your session has expired. Please log in again.");
  }

  function handleLoggedIn(tokens) {
    setSessionMessage(null);
    setToken(tokens.token);
    setRefreshToken(tokens.refreshToken);
  }

  function handleLogout() {
    if (refreshToken) {
      logout(refreshToken).catch(() => {});
    }
    // Go to the login page first, so logging out doesn't count as "was heading to this page".
    navigate("/login", { replace: true });
    setToken(null);
    setRefreshToken(null);
  }

  useEffect(() => writeStored("token", token), [token]);
  useEffect(() => writeStored("refreshToken", refreshToken), [refreshToken]);

  useEffect(() => {
    configureAuth({
      refreshToken,
      onTokens: (tokens) => {
        setToken(tokens.token);
        setRefreshToken(tokens.refreshToken);
      },
    });
  }, [refreshToken]);

  const role = token ? roleOf(token) : null;
  const showManageProducts = canManageProducts(role);
  const showAdmin = isAdmin(role);
  const session = { token, onUnauthorized: handleUnauthorized };

  return (
    <Routes>
      <Route element={<LoggedOutOnly token={token} />}>
        <Route
          path="/login"
          element={<Login onLoggedIn={handleLoggedIn} message={sessionMessage} />}
        />
        <Route path="/register" element={<Register onRegistered={handleLoggedIn} />} />
      </Route>

      <Route element={<RequireAuth token={token} />}>
        <Route path="/" element={<Home />} />
        <Route
          element={
            <Layout
              showManageProducts={showManageProducts}
              showAdmin={showAdmin}
              onLogout={handleLogout}
            />
          }
        >
          <Route path="/orders" element={<OrdersList {...session} />} />
          <Route path="/orders/:orderId" element={<OrderDetail {...session} />} />
          <Route path="/products" element={<ProductsList {...session} />} />
          <Route path="/place-order" element={<PlaceOrder {...session} />} />
          <Route path="/notifications" element={<Notifications {...session} />} />
          <Route element={<RequireRole allowed={showManageProducts} />}>
            <Route
              path="/manage-products"
              element={
                <ManageProducts
                  {...session}
                  currentUsername={usernameOf(token)}
                  isAdmin={showAdmin}
                />
              }
            />
          </Route>
          <Route element={<RequireRole allowed={showAdmin} />}>
            <Route
              path="/admin/*"
              element={<Admin {...session} currentUsername={usernameOf(token)} />}
            />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Route>
    </Routes>
  );
}
