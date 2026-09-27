import { useEffect, useState } from "react";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";
import ProductsList from "./components/ProductsList";
import PlaceOrder from "./components/PlaceOrder";
import PaymentMethods from "./components/PaymentMethods";

function readStoredToken() {
  try {
    return localStorage.getItem("token");
  } catch {
    return null;
  }
}

export default function App() {
  const [token, setToken] = useState(readStoredToken);
  const [view, setView] = useState("login"); // "login" | "register"
  const [page, setPage] = useState("orders"); // "orders" | "products" | "placeOrder" | "paymentMethods"
  const [sessionMessage, setSessionMessage] = useState(null);

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
            style={{ marginRight: 8 }}
          >
            Place Order
          </button>
          <button
            type="button"
            onClick={() => setPage("paymentMethods")}
            disabled={page === "paymentMethods"}
          >
            Payment Methods
          </button>
        </nav>
        {page === "orders" && <OrdersList token={token} onUnauthorized={handleUnauthorized} />}
        {page === "products" && <ProductsList token={token} onUnauthorized={handleUnauthorized} />}
        {page === "placeOrder" && (
          <PlaceOrder
            token={token}
            onOrderPlaced={() => setPage("orders")}
            onUnauthorized={handleUnauthorized}
          />
        )}
        {page === "paymentMethods" && (
          <PaymentMethods token={token} onUnauthorized={handleUnauthorized} />
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
