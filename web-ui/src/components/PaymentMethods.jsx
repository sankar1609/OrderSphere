import { useEffect, useState } from "react";
import { listPaymentMethods, createPaymentMethod, deletePaymentMethod } from "../api";
import { cellStyle, linkButtonStyle } from "../styles";

export default function PaymentMethods({ token, onUnauthorized }) {
  const [methods, setMethods] = useState(null);
  const [error, setError] = useState(null);
  const [newToken, setNewToken] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    listPaymentMethods(token)
      .then(setMethods)
      .catch((err) => {
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setError(err.message);
        }
      });
  }, [token]);

  async function handleAdd(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const created = await createPaymentMethod(token, { type: "CARD", token: newToken });
      setMethods([...methods, created]);
      setNewToken("");
    } catch (err) {
      if (err.status === 401) {
        onUnauthorized();
      } else {
        setError(err.message);
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id) {
    setError(null);
    try {
      await deletePaymentMethod(token, id);
      setMethods(methods.filter((method) => method.id !== id));
    } catch (err) {
      if (err.status === 401) {
        onUnauthorized();
      } else {
        setError(err.message);
      }
    }
  }

  return (
    <div>
      <h2>Payment Methods</h2>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && methods === null && <p>Loading...</p>}
      {methods && methods.length === 0 && <p>No payment methods yet.</p>}

      {methods && methods.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 16 }}>
          <thead>
            <tr>
              <th style={cellStyle}>Type</th>
              <th style={cellStyle}>Token</th>
              <th style={cellStyle}>Created</th>
              <th style={cellStyle}></th>
            </tr>
          </thead>
          <tbody>
            {methods.map((method) => (
              <tr key={method.id}>
                <td style={cellStyle}>{method.type}</td>
                <td style={cellStyle}>{method.token}</td>
                <td style={cellStyle}>{new Date(method.createdAt).toLocaleString()}</td>
                <td style={cellStyle}>
                  <button
                    type="button"
                    onClick={() => handleDelete(method.id)}
                    style={linkButtonStyle}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={handleAdd}>
        <label>
          Card token
          <input
            type="text"
            value={newToken}
            onChange={(e) => setNewToken(e.target.value)}
            required
          />
        </label>{" "}
        <button type="submit" disabled={submitting}>
          {submitting ? "Adding..." : "Add"}
        </button>
      </form>
    </div>
  );
}
