import { useEffect, useState } from "react";
import { listProducts } from "../api";
import { cellStyle } from "../styles";
import { formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";

export default function ProductsList({ token, onUnauthorized }) {
  usePageTitle("Products");
  const [products, setProducts] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    listProducts(token)
      .then(setProducts)
      .catch((err) => {
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setError(err.message);
        }
      });
  }, [token]);

  return (
    <div>
      <h2>Products</h2>

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && products === null && <p>Loading...</p>}
      {products && products.length === 0 && <p>No products yet.</p>}

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
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td style={cellStyle}>{product.sku}</td>
                <td style={cellStyle}>{product.name}</td>
                <td style={cellStyle}>{formatMoney(product.unitPrice)}</td>
                <td style={cellStyle}>{product.availableQuantity}</td>
                <td style={cellStyle}>{product.quantityOnHand}</td>
                <td style={cellStyle}>{product.quantityReserved}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
