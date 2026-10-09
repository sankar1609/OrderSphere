import { useState } from "react";
import { Link, useLocation } from "react-router";
import { login } from "../api";
import { usePageTitle } from "../usePageTitle";

export default function Login({ onLoggedIn, message }) {
  usePageTitle("Log in");
  // Keep "where the visitor was heading" (set by RequireAuth) when switching to Register.
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      onLoggedIn(await login(username, password));
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 320, margin: "80px auto", fontFamily: "sans-serif" }}>
      <h1>OrderSphere</h1>
      {message && <p style={{ color: "#a15c00" }}>{message}</p>}
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 12 }}>
          <label>
            Username
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={{ display: "block", width: "100%" }}
              required
            />
          </label>
        </div>
        <div style={{ marginBottom: 12 }}>
          <label>
            Password
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={{ display: "block", width: "100%" }}
              required
            />
          </label>
        </div>
        <button type="submit" disabled={submitting}>
          {submitting ? "Logging in..." : "Log in"}
        </button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>
        No account?{" "}
        <Link to="/register" state={location.state}>
          Register
        </Link>
      </p>
    </div>
  );
}
