import { useState } from "react";
import Login from "./components/Login";
import OrdersList from "./components/OrdersList";

export default function App() {
  const [token, setToken] = useState(null);

  if (!token) {
    return <Login onLoggedIn={setToken} />;
  }

  return <OrdersList token={token} onLogout={() => setToken(null)} />;
}
