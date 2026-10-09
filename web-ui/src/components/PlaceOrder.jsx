import { useEffect, useState } from "react";
import { listProducts, createOrder } from "../api";
import { cellStyle, linkButtonStyle } from "../styles";
import { formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";

export default function PlaceOrder({ token, onUnauthorized }) {
  usePageTitle("Place Order");
  const [products, setProducts] = useState(null);
  const [productsError, setProductsError] = useState(null);
  const [selectedSku, setSelectedSku] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [items, setItems] = useState([]);
  const [currency, setCurrency] = useState("USD");
  const [shippingDestination, setShippingDestination] = useState("");
  const [error, setError] = useState(null);
  const [itemNotice, setItemNotice] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    listProducts(token)
      .then((data) => {
        setProducts(data);
        // Only a default: the list reloads when the access token is refreshed, and that mustn't
        // switch a product the customer has already picked.
        const firstInStock = data.find((product) => product.availableQuantity > 0);
        if (firstInStock) {
          setSelectedSku((current) => current || firstInStock.sku);
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

  // How many more of a product fit in this order: what's available minus what's already in the
  // cart. Inventory has the final say (it rejects the whole order if stock ran out meanwhile).
  function remainingFor(sku) {
    const available = products?.find((product) => product.sku === sku)?.availableQuantity ?? 0;
    const inCart = items
      .filter((item) => item.sku === sku)
      .reduce((sum, item) => sum + item.quantity, 0);
    return Math.max(available - inCart, 0);
  }

  function addItem() {
    setItemNotice(null);
    if (!selectedSku || quantity < 1) {
      return;
    }
    const remaining = remainingFor(selectedSku);
    if (Number(quantity) > remaining) {
      setItemNotice(
        remaining === 0
          ? `No more ${selectedSku} available.`
          : `Only ${remaining} more ${selectedSku} available.`
      );
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

    if (items.length === 0) {
      setError("Add at least one item before placing the order.");
      return;
    }

    setSubmitting(true);
    try {
      const order = await createOrder(token, { items, currency, shippingDestination });
      if (order.status === "AWAITING_PAYMENT" && order.checkoutUrl) {
        // Hand over to the payment provider's hosted checkout page. It sends the browser back to
        // this app (?payment=success|cancelled&orderId=...) when the customer is done.
        window.location.assign(order.checkoutUrl);
        return;
      }
      setError(
        `Order #${order.id} couldn't be placed: ${
          order.cancellationReason ?? "some items may be unavailable"
        }. Check the catalog and try again.`
      );
      setSubmitting(false);
    } catch (err) {
      if (err.status === 401) {
        onUnauthorized();
      } else {
        setError(err.message);
      }
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
                <option
                  key={product.id}
                  value={product.sku}
                  disabled={product.availableQuantity <= 0}
                >
                  {`${product.sku} — ${product.name} — ${formatMoney(product.unitPrice)} (${
                    product.availableQuantity > 0
                      ? `${product.availableQuantity} available`
                      : "out of stock"
                  })`}
                </option>
              ))}
            </select>
          </label>{" "}
          <label>
            Qty
            <input
              type="number"
              min={1}
              max={Math.max(remainingFor(selectedSku), 1)}
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              style={{ width: 60 }}
            />
          </label>{" "}
          <button type="button" onClick={addItem} disabled={remainingFor(selectedSku) === 0}>
            Add item
          </button>
          {itemNotice && <p style={{ color: "crimson" }}>{itemNotice}</p>}
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
          {submitting ? "Redirecting to payment..." : "Place order and pay"}
        </button>
      </form>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
    </div>
  );
}
