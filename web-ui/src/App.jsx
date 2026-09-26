import { useState } from "react";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";
import ProductsList from "./components/ProductsList";
import PlaceOrder from "./components/PlaceOrder";

export default function App() {
  const [token, setToken] = useState(null);
  const [view, setView] = useState("login"); // "login" | "register"
  const [page, setPage] = useState("orders"); // "orders" | "products" | "placeOrder"

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
          <button type="button" onClick={() => setPage("placeOrder")} disabled={page === "placeOrder"}>
            Place Order
          </button>
        </nav>
        {page === "orders" && <OrdersList token={token} />}
        {page === "products" && <ProductsList token={token} />}
        {page === "placeOrder" && (
          <PlaceOrder token={token} onOrderPlaced={() => setPage("orders")} />
        )}
      </div>
    );
  }

  if (view === "register") {
    return <Register onRegistered={setToken} onSwitchToLogin={() => setView("login")} />;
  }

  return <Login onLoggedIn={setToken} onSwitchToRegister={() => setView("register")} />;
}
