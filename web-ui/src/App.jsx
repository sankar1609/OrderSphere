import { useEffect, useState } from "react";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";
import ProductsList from "./components/ProductsList";
import PlaceOrder from "./components/PlaceOrder";
import ManageProducts from "./components/ManageProducts";
import OrderDetail from "./components/OrderDetail";
import Notifications from "./components/Notifications";
import { configureAuth, logout } from "./api";
import { canManageProducts, roleOf } from "./auth";

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
 * The payment provider redirects back here with ?payment=success|cancelled&orderId=N. Pure read:
 * React may call a state initializer twice (StrictMode), so stripping the query happens in an
 * effect instead.
 */
function readPaymentReturn() {
  const params = new URLSearchParams(window.location.search);
  const outcome = params.get("payment");
  const orderId = params.get("orderId");
  return outcome && orderId ? { outcome, orderId } : null;
}

export default function App() {
  const [token, setToken] = useState(() => readStored("token"));
  const [refreshToken, setRefreshToken] = useState(() => readStored("refreshToken"));
  const [view, setView] = useState("login"); // "login" | "register"
  // "orders" | "orderDetail" | "products" | "placeOrder" | "notifications" | "manageProducts"
  const [page, setPage] = useState("orders");
  const [detailOrderId, setDetailOrderId] = useState(null);
  const [paymentReturn] = useState(readPaymentReturn);
  const [sessionMessage, setSessionMessage] = useState(null);

  // Drop the payment-return query from the address bar so a refresh doesn't replay it.
  useEffect(() => {
    if (paymentReturn) {
      window.history.replaceState(null, "", window.location.pathname);
    }
  }, [paymentReturn]);

  // Reached only once the refresh token can't renew the session either (see api.js).
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
    setPage("orders");
    if (refreshToken) {
      logout(refreshToken).catch(() => {});
    }
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

  if (token) {
    const showManageProducts = canManageProducts(roleOf(token));
    return (
      <div style={{ maxWidth: 720, margin: "40px auto", fontFamily: "sans-serif" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h1>OrderSphere</h1>
          <button type="button" onClick={handleLogout}>
            Log out
          </button>
        </div>
        <nav style={{ marginBottom: 20 }}>
          <button
            type="button"
            onClick={() => setPage("orders")}
            disabled={page === "orders"}
            style={{ marginRight: 8 }}
          >
            My Orders
          </button>
          <button
            type="button"
            onClick={() => setPage("products")}
            disabled={page === "products"}
            style={{ marginRight: 8 }}
          >
            Products
          </button>
          <button
            type="button"
            onClick={() => setPage("placeOrder")}
            disabled={page === "placeOrder"}
            style={{ marginRight: 8 }}
          >
            Place Order
          </button>
          <button
            type="button"
            onClick={() => setPage("notifications")}
            disabled={page === "notifications"}
            style={{ marginRight: 8 }}
          >
            Notifications
          </button>
          {showManageProducts && (
            <button
              type="button"
              onClick={() => setPage("manageProducts")}
              disabled={page === "manageProducts"}
            >
              Manage Products
            </button>
          )}
        </nav>
        {page === "orders" && (
          <OrdersList
            token={token}
            onUnauthorized={handleUnauthorized}
            paymentReturn={paymentReturn}
            onOpen={(orderId) => {
              setDetailOrderId(orderId);
              setPage("orderDetail");
            }}
          />
        )}
        {page === "orderDetail" && (
          <OrderDetail
            token={token}
            orderId={detailOrderId}
            onBack={() => setPage("orders")}
            onUnauthorized={handleUnauthorized}
          />
        )}
        {page === "notifications" && (
          <Notifications token={token} onUnauthorized={handleUnauthorized} />
        )}
        {page === "products" && <ProductsList token={token} onUnauthorized={handleUnauthorized} />}
        {page === "placeOrder" && (
          <PlaceOrder token={token} onUnauthorized={handleUnauthorized} />
        )}
        {page === "manageProducts" && showManageProducts && (
          <ManageProducts token={token} onUnauthorized={handleUnauthorized} />
        )}
      </div>
    );
  }

  if (view === "register") {
    return (
      <Register onRegistered={handleLoggedIn} onSwitchToLogin={() => setView("login")} />
    );
  }

  return (
    <Login
      onLoggedIn={handleLoggedIn}
      onSwitchToRegister={() => setView("register")}
      message={sessionMessage}
    />
  );
}
