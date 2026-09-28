import { useEffect, useState } from "react";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";
import ProductsList from "./components/ProductsList";
import PlaceOrder from "./components/PlaceOrder";

function readStoredToken() {
  try {
    return localStorage.getItem("token");
  } catch {
    return null;
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
  const [token, setToken] = useState(readStoredToken);
  const [view, setView] = useState("login"); // "login" | "register"
  const [page, setPage] = useState("orders"); // "orders" | "products" | "placeOrder"
  const [paymentReturn] = useState(readPaymentReturn);
  const [sessionMessage, setSessionMessage] = useState(null);

  // Drop the payment-return query from the address bar so a refresh doesn't replay it.
  useEffect(() => {
    if (paymentReturn) {
      window.history.replaceState(null, "", window.location.pathname);
    }
  }, [paymentReturn]);

  function handleUnauthorized() {
    setToken(null);
    setSessionMessage("Your session has expired. Please log in again.");
  }

  function handleLoggedIn(newToken) {
    setSessionMessage(null);
    setToken(newToken);
  }

  useEffect(() => {
    try {
      if (token) {
        localStorage.setItem("token", token);
      } else {
        localStorage.removeItem("token");
      }
    } catch {
      // e.g. private browsing with storage disabled — token just won't survive a refresh
    }
  }, [token]);

  if (token) {
    return (
      <div style={{ maxWidth: 720, margin: "40px auto", fontFamily: "sans-serif" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <h1>OrderSphere</h1>
          <button type="button" onClick={() => setToken(null)}>
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
          >
            Place Order
          </button>
        </nav>
        {page === "orders" && (
          <OrdersList
            token={token}
            onUnauthorized={handleUnauthorized}
            paymentReturn={paymentReturn}
          />
        )}
        {page === "products" && <ProductsList token={token} onUnauthorized={handleUnauthorized} />}
        {page === "placeOrder" && (
          <PlaceOrder token={token} onUnauthorized={handleUnauthorized} />
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
