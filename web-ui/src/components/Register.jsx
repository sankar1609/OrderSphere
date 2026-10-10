import { useState } from "react";
import { Link, useLocation } from "react-router";
import { register, login } from "../api";
import { usePageTitle } from "../usePageTitle";

export default function Register({ onRegistered }) {
  usePageTitle("Register");
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("CUSTOMER");
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(username, password, role);
      // Registration doesn't return a token - log straight in with the same
      // credentials so signing up is a single flow, not two.
      onRegistered(await login(username, password));
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
              minLength={3}
              maxLength={50}
              pattern="[A-Za-z0-9._\-]+"
              title="3-50 letters, digits, '.', '_' or '-'"
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
              maxLength={72}
              required
            />
          </label>
          <small>8-72 characters.</small>
        </div>
        <fieldset style={{ marginBottom: 12 }}>
          <legend>Account type</legend>
          <label style={{ marginRight: 12 }}>
            <input
              type="radio"
              name="role"
              value="CUSTOMER"
              checked={role === "CUSTOMER"}
              onChange={() => setRole("CUSTOMER")}
            />{" "}
            Customer
          </label>
          <label>
            <input
              type="radio"
              name="role"
              value="VENDOR"
              checked={role === "VENDOR"}
              onChange={() => setRole("VENDOR")}
            />{" "}
            Vendor
          </label>
          <div>
            <small>Vendors can also add products to the catalog and restock them.</small>
          </div>
        </fieldset>
        <button type="submit" disabled={submitting}>
          {submitting ? "Creating account..." : "Create account"}
        </button>
      </form>
      {error && <p style={{ color: "crimson" }}>{error}</p>}
      <p>
        Already have an account?{" "}
        <Link to="/login" state={location.state}>
          Log in
        </Link>
      </p>
    </div>
  );
}
