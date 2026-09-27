import { useEffect, useState } from "react";
import {
  listProducts,
  listPaymentMethods,
  createPaymentMethod,
  createOrder,
} from "../api";
import { cellStyle, linkButtonStyle } from "../styles";
import { formatMoney } from "../format";

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

  // Display-only estimate from the catalog; the backend recalculates the charged total itself.
  function unitPriceFor(sku) {
    return products?.find((product) => product.sku === sku)?.unitPrice ?? 0;
  }

  const estimatedTotal = items.reduce(
    (sum, item) => sum + unitPriceFor(item.sku) * item.quantity,
    0
  );

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
        currency,
        shippingDestination,
      });
      setSuccess(
        `Order #${order.id} placed (status: ${order.status}, total: ${formatMoney(
          order.totalAmount,
          order.currency
        )}).`
      );
      setItems([]);
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
                  {`${product.sku} — ${product.name} — ${formatMoney(product.unitPrice)} (${
                    product.availableQuantity
                  } available)`}
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
              <th style={cellStyle}>Unit price</th>
              <th style={cellStyle}>Subtotal</th>
              <th style={cellStyle}></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => (
              <tr key={`${item.sku}-${index}`}>
                <td style={cellStyle}>{item.sku}</td>
                <td style={cellStyle}>{item.quantity}</td>
                <td style={cellStyle}>{formatMoney(unitPriceFor(item.sku))}</td>
                <td style={cellStyle}>{formatMoney(unitPriceFor(item.sku) * item.quantity)}</td>
                <td style={cellStyle}>
                  <button type="button" onClick={() => removeItem(index)} style={linkButtonStyle}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td style={cellStyle} colSpan={3}>
                <strong>Estimated total</strong>
              </td>
              <td style={cellStyle}>
                <strong>{formatMoney(estimatedTotal, currency)}</strong>
              </td>
              <td style={cellStyle}></td>
            </tr>
          </tfoot>
        </table>
      )}

      <form onSubmit={handleSubmit}>
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
