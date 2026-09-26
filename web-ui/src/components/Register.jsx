import { useState } from "react";
import { register, login } from "../api";

export default function Register({ onRegistered, onSwitchToLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(username, password);
      // Registration doesn't return a token - log straight in with the same
      // credentials so signing up is a single flow, not two.
      const { token } = await login(username, password);
      onRegistered(token);
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 320, margin: "80px auto", fontFamily: "sans-serif" }}>
      <h1>OrderSphere</h1>
      <h2 style={{ fontSize: 16, fontWeight: "normal" }}>Create an account</h2>
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
              minLength={8}
              required
            />
          </label>
          <small>At least 8 characters.</small>
        </div>
        <button type="submit" disabled={submitting}>
          {submitting ? "Creating account..." : "Create account"}
        </button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>
        Already have an account?{" "}
        <button type="button" onClick={onSwitchToLogin} style={linkButtonStyle}>
          Log in
        </button>
      </p>
    </div>
  );
}

const linkButtonStyle = {
  background: "none",
  border: "none",
  padding: 0,
  color: "#0645ad",
  textDecoration: "underline",
  cursor: "pointer",
};
