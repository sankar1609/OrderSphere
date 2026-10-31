import { useEffect, useState } from "react";
import { createProduct, listProducts, restockProduct } from "../api";
import { cellStyle } from "../styles";
import { formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";
import { usePageParams } from "../usePageParams";
import Pager from "./Pager";
import SearchBox from "./SearchBox";

const PAGE_SIZE = 50;
const emptyForm = { sku: "", name: "", unitPrice: "", quantityOnHand: "0", reorderThreshold: "0" };

/** ADMIN/VENDOR screen: add products to the catalog and restock existing ones. */
export default function ManageProducts({ token, currentUsername, isAdmin, onUnauthorized }) {
  usePageTitle("Manage Products");
  const { page, query, setPage, setQuery } = usePageParams();
  const [result, setResult] = useState(null);
  const [loadError, setLoadError] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [formMessage, setFormMessage] = useState(null);
  const [creating, setCreating] = useState(false);
  const [restockQuantities, setRestockQuantities] = useState({});
  const [restockMessage, setRestockMessage] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  function handleError(err, show) {
    if (err.status === 401) {
      onUnauthorized();
    } else {
      show(err.message);
    }
  }

  useEffect(() => {
    // A newer page or search replaces this request: its answer, if it comes later, is dropped.
    let current = true;
    listProducts(token, { page, size: PAGE_SIZE, query })
      .then((data) => current && setResult(data))
      .catch((err) => current && handleError(err, setLoadError));
    return () => {
      current = false;
    };
  }, [token, refreshKey, page, query]);

  const products = result?.items ?? null;

  function updateField(field) {
    return (event) => setForm({ ...form, [field]: event.target.value });
  }

  async function handleCreate(event) {
    event.preventDefault();
    setFormMessage(null);
    setCreating(true);
    try {
      const created = await createProduct(token, {
        sku: form.sku.trim(),
        name: form.name.trim(),
        unitPrice: Number(form.unitPrice),
        quantityOnHand: Number(form.quantityOnHand),
        reorderThreshold: Number(form.reorderThreshold),
      });
      setForm(emptyForm);
      setFormMessage({ tone: "success", text: `Created ${created.sku} — ${created.name}.` });
      setRefreshKey((key) => key + 1);
    } catch (err) {
      handleError(err, (text) => setFormMessage({ tone: "error", text }));
    } finally {
      setCreating(false);
    }
  }

  async function handleRestock(sku) {
    setRestockMessage(null);
    const quantity = Number(restockQuantities[sku] ?? 0);
    if (!Number.isInteger(quantity) || quantity < 1) {
      setRestockMessage({ tone: "error", text: "Restock quantity must be a whole number of at least 1." });
      return;
    }
    try {
      const updated = await restockProduct(token, sku, quantity);
      setResult((current) => ({
        ...current,
        items: current.items.map((p) => (p.sku === updated.sku ? updated : p)),
      }));
      setRestockQuantities({ ...restockQuantities, [sku]: "" });
      setRestockMessage({
        tone: "success",
        text: `Added ${quantity} to ${sku} — ${updated.availableQuantity} now available.`,
      });
    } catch (err) {
      handleError(err, (text) => setRestockMessage({ tone: "error", text }));
    }
  }

  const toneColors = { success: "green", error: "crimson" };

  return (
    <div>
      <h2>Manage Products</h2>

      <h3>Add a product</h3>
      <form onSubmit={handleCreate} style={{ marginBottom: 24 }}>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginBottom: 12 }}>
          <label>
            SKU
            <input
              type="text"
              value={form.sku}
              onChange={updateField("sku")}
              style={{ display: "block", width: "100%" }}
              maxLength={64}
              pattern="[A-Za-z0-9][A-Za-z0-9._\-]*"
              title="Letters, digits, '.', '_' or '-' (no spaces or '/')"
              required
            />
          </label>
          <label>
            Name
            <input
              type="text"
              value={form.name}
              onChange={updateField("name")}
              style={{ display: "block", width: "100%" }}
              maxLength={255}
              required
            />
          </label>
          <label>
            Unit price
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={form.unitPrice}
              onChange={updateField("unitPrice")}
              style={{ display: "block", width: "100%" }}
              required
            />
          </label>
          <label>
            Starting stock
            <input
              type="number"
              min="0"
              max="1000000"
              step="1"
              value={form.quantityOnHand}
              onChange={updateField("quantityOnHand")}
              style={{ display: "block", width: "100%" }}
              required
            />
          </label>
          <label>
            Reorder threshold
            <input
              type="number"
              min="0"
              step="1"
              value={form.reorderThreshold}
              onChange={updateField("reorderThreshold")}
              style={{ display: "block", width: "100%" }}
              required
            />
            <small>
              Stock at or below this is flagged as low, and you get a Notifications alert when it
              drops there.
            </small>
          </label>
        </div>
        <button type="submit" disabled={creating}>
          {creating ? "Creating..." : "Create product"}
        </button>
        {formMessage && (
          <p role="status" style={{ color: toneColors[formMessage.tone] }}>
            {formMessage.text}
          </p>
        )}
      </form>

      <h3>Catalog</h3>
      <SearchBox query={query} onSearch={setQuery} label="Search catalog" placeholder="SKU or name" />
      {restockMessage && (
        <p role="status" style={{ color: toneColors[restockMessage.tone] }}>
          {restockMessage.text}
        </p>
      )}
      {loadError && <p style={{ color: "crimson" }}>{loadError}</p>}
      {!loadError && products === null && <p>Loading...</p>}
      {result && result.total === 0 && (
        <p>{query ? `No products match "${query}".` : "No products yet."}</p>
      )}

      {products && products.length > 0 && (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr>
              <th style={cellStyle}>SKU</th>
              <th style={cellStyle}>Name</th>
              <th style={cellStyle}>Price</th>
              <th style={cellStyle}>Available</th>
              <th style={cellStyle}>On Hand</th>
              <th style={cellStyle}>Reserved</th>
              <th style={cellStyle}>Reorder at</th>
              <th style={cellStyle}>Owner</th>
              <th style={cellStyle}>Restock</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => {
              const low = product.availableQuantity <= product.reorderThreshold;
              return (
                <tr key={product.id} style={low ? { background: "#fff4e5" } : undefined}>
                  <td style={cellStyle}>{product.sku}</td>
                  <td style={cellStyle}>{product.name}</td>
                  <td style={cellStyle}>{formatMoney(product.unitPrice)}</td>
                  <td style={cellStyle}>
                    {product.availableQuantity}
                    {low && <span style={{ color: "#b35c00" }}> (low)</span>}
                  </td>
                  <td style={cellStyle}>{product.quantityOnHand}</td>
                  <td style={cellStyle}>{product.quantityReserved}</td>
                  <td style={cellStyle}>{product.reorderThreshold}</td>
                  <td style={cellStyle}>{product.createdBy ?? "—"}</td>
                  <td style={cellStyle}>
                    {isAdmin || product.createdBy === currentUsername ? (
                      <>
                        <input
                          type="number"
                          min="1"
                          max="1000000"
                          step="1"
                          aria-label={`Restock quantity for ${product.sku}`}
                          value={restockQuantities[product.sku] ?? ""}
                          onChange={(e) =>
                            setRestockQuantities({ ...restockQuantities, [product.sku]: e.target.value })
                          }
                          style={{ width: 60 }}
                        />{" "}
                        <button type="button" onClick={() => handleRestock(product.sku)}>
                          Restock
                        </button>
                      </>
                    ) : (
                      <span style={{ color: "#777", fontSize: 12 }}>not yours</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
      {result && result.total > 0 && (
        <Pager result={result} onPage={setPage} noun={["product", "products"]} />
      )}
    </div>
  );
}
