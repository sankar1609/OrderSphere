// Prices come back from the backend as JSON numbers (BigDecimal); render them with 2 decimals.
// Orders placed before pricing existed have no total, so null/undefined renders as a dash.
export function formatMoney(value, currency) {
  if (value === null || value === undefined) {
    return "—";
  }
  const amount = Number(value).toFixed(2);
  return currency ? `${amount} ${currency}` : amount;
}
