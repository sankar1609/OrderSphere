import { useEffect, useState } from "react";
import {
  listProducts,
  listPaymentMethods,
  createPaymentMethod,
  createOrder,
} from "../api";
import { cellStyle, linkButtonStyle } from "../styles";

async function resolvePaymentMethodId(token) {
  const existing = await listPaymentMethods(token);
  if (existing.length > 0) {
    return existing[0].id;
  }
  const created = await createPaymentMethod(token, {
    type: "CARD",
    token: `web-ui-${Date.now()}`,
  });
  return created.id;
}

export default function PlaceOrder({ token, onOrderPlaced, onUnauthorized }) {
  const [products, setProducts] = useState(null);
  const [productsError, setProductsError] = useState(null);
  const [selectedSku, setSelectedSku] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [items, setItems] = useState([]);
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState("USD");
  const [shippingDestination, setShippingDestination] = useState("");
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    listProducts(token)
      .then((data) => {
        setProducts(data);
        if (data.length > 0) {
          setSelectedSku(data[0].sku);
        }
      })
      .catch((err) => {
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setProductsError(err.message);
        }
      });
  }, [token]);

  function addItem() {
    if (!selectedSku || quantity < 1) {
      return;
    }
    setItems([...items, { sku: selectedSku, quantity: Number(quantity) }]);
    setQuantity(1);
  }

  function removeItem(index) {
    setItems(items.filter((_, i) => i !== index));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    if (items.length === 0) {
      setError("Add at least one item before placing the order.");
      return;
    }

    setSubmitting(true);
    try {
      const paymentMethodId = await resolvePaymentMethodId(token);
      const order = await createOrder(token, {
        items,
        paymentMethodId,
        amount: Number(amount),
        currency,
        shippingDestination,
      });
      setSuccess(`Order #${order.id} placed (status: ${order.status}).`);
      setItems([]);
      setAmount("");
      setShippingDestination("");
      onOrderPlaced?.(order);
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

  return (
    <div>
      <h2>Place Order</h2>

      {productsError && <p style={{ color: "crimson" }}>{productsError}</p>}
      {!productsError && products === null && <p>Loading products...</p>}

      {products && products.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <label>
            Product
            <select value={selectedSku} onChange={(e) => setSelectedSku(e.target.value)}>
              {products.map((product) => (
                <option key={product.id} value={product.sku}>
                  {product.sku} — {product.name} ({product.availableQuantity} available)
                </option>
              ))}
            </select>
          </label>{" "}
          <label>
            Qty
            <input
              type="number"
              min={1}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              style={{ width: 60 }}
            />
          </label>{" "}
          <button type="button" onClick={addItem}>
            Add item
          </button>
        </div>
      )}

      {items.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse", marginBottom: 16 }}>
          <thead>
            <tr>
              <th style={cellStyle}>SKU</th>
              <th style={cellStyle}>Quantity</th>
              <th style={cellStyle}></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => (
              <tr key={`${item.sku}-${index}`}>
                <td style={cellStyle}>{item.sku}</td>
                <td style={cellStyle}>{item.quantity}</td>
                <td style={cellStyle}>
                  <button type="button" onClick={() => removeItem(index)} style={linkButtonStyle}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: 12 }}>
          <label>
            Amount
            <input
              type="number"
              step="0.01"
              min="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              style={{ display: "block" }}
              required
            />
          </label>
          <small>
            Not calculated from items — pricing isn't modeled in the backend yet, so enter the
            total to charge.
          </small>
        </div>
        <div style={{ marginBottom: 12 }}>
          <label>
            Currency
            <input
              type="text"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              style={{ display: "block" }}
              required
            />
          </label>
        </div>
        <div style={{ marginBottom: 12 }}>
          <label>
            Shipping destination
            <input
              type="text"
              value={shippingDestination}
              onChange={(e) => setShippingDestination(e.target.value)}
              style={{ display: "block", width: "100%" }}
              required
            />
          </label>
        </div>
        <button type="submit" disabled={submitting}>
          {submitting ? "Placing order..." : "Place order"}
        </button>
      </form>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {success && <p style={{ color: "green" }}>{success}</p>}
    </div>
  );
}
