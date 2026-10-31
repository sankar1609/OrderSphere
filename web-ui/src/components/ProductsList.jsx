import { useEffect, useState } from "react";
import { listProducts } from "../api";
import { cellStyle } from "../styles";
import { formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";
import { usePageParams } from "../usePageParams";
import Pager from "./Pager";
import SearchBox from "./SearchBox";

const PAGE_SIZE = 50;

export default function ProductsList({ token, onUnauthorized }) {
  usePageTitle("Products");
  const { page, query, setPage, setQuery } = usePageParams();
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    // A newer page or search replaces this request: its answer, if it comes later, is dropped.
    let current = true;
    listProducts(token, { page, size: PAGE_SIZE, query })
      .then((data) => current && setResult(data))
      .catch((err) => {
        if (!current) {
          return;
        }
        if (err.status === 401) {
          onUnauthorized();
        } else {
          setError(err.message);
        }
      });
    return () => {
      current = false;
    };
  }, [token, page, query]);

  const products = result?.items ?? null;

  return (
    <div>
      <h2>Products</h2>
      <SearchBox query={query} onSearch={setQuery} label="Search catalog" placeholder="SKU or name" />

      {error && <p style={{ color: "crimson" }}>{error}</p>}
      {!error && products === null && <p>Loading...</p>}
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
      {result && result.total > 0 && (
        <Pager result={result} onPage={setPage} noun={["product", "products"]} />
      )}
    </div>
  );
}
