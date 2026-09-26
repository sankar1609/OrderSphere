import { useState } from "react";
import Login from "./components/Login";
import Register from "./components/Register";
import OrdersList from "./components/OrdersList";

export default function App() {
  const [token, setToken] = useState(null);
  const [view, setView] = useState("login"); // "login" | "register"

  if (token) {
    return <OrdersList token={token} onLogout={() => setToken(null)} />;
  }

  if (view === "register") {
    return <Register onRegistered={setToken} onSwitchToLogin={() => setView("login")} />;
  }

  return <Login onLoggedIn={setToken} onSwitchToRegister={() => setView("register")} />;
}
