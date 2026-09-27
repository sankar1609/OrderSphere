# OrderSphere Web UI

Log in (or register), browse your own orders and the product catalog, place new orders, and manage payment methods. Talks to the API Gateway directly — no server of its own, no routing library. The login token is persisted in `localStorage`, so a page refresh keeps you logged in.

## Prerequisites

The backend stack must already be running via `docker compose up -d` from the repo root, including the gateway's CORS config for `http://localhost:5173` (see `services/ordersphere-gateway/src/main/resources/application.yml`).

## Run it

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173`. Log in with an existing account, or register a new one — registration always creates a CUSTOMER (other roles are admin-provisioned, not self-service) and logs you straight in afterward.

## What's here

- `src/format.js` — `formatMoney` for rendering prices and totals.
- `src/api.js` — fetch wrapper, gateway base URL from `VITE_GATEWAY_URL` in `.env`.
- `src/components/Login.jsx` — calls `POST /auth-service/auth/login`.
- `src/components/Register.jsx` — calls `POST /auth-service/auth/register`, then logs in with the same credentials (registration itself returns no token).
- `src/components/OrdersList.jsx` — calls `GET /ordersphere-orders/orders` with the bearer token, scoped server-side to the logged-in customer; shows each order's server-calculated total (a dash for orders placed before pricing existed).
- `src/components/ProductsList.jsx` — calls `GET /inventory-service/inventory/products` with the bearer token; shows SKU, name, price, and stock levels.
- `src/components/PlaceOrder.jsx` — builds an item list from the product catalog (showing each line's unit price and subtotal plus an estimated total) and calls `POST /ordersphere-orders/orders`. The estimate is display-only: the request carries no amount, and the backend calculates the total it charges from Inventory's prices. If the customer has no payment methods yet, one is auto-created (`POST /payment-service/payment-methods`, type `CARD`, a generated placeholder token) the first time they place an order; otherwise their first payment method is reused. See `PaymentMethods.jsx` below to manage these directly.
- `src/components/PaymentMethods.jsx` — lists, adds (`POST /payment-service/payment-methods`), and deletes (`DELETE /payment-service/payment-methods/{id}`) the logged-in customer's payment methods. `PaymentMethodType` only has one value (`CARD`) today, so there's no type selector — just a free-text "card token" field, matching the backend's own lack of real card validation.
- `src/App.jsx` — holds the token (synced to `localStorage` on every change, read back on load) and the current auth view (`login`/`register`) and authenticated page (`orders`/`products`/`placeOrder`/`paymentMethods`) in memory. Also owns session-expiry handling: `api.js`'s fetch wrapper tags a non-2xx response's thrown `Error` with `error.status`, and each authenticated screen calls the `onUnauthorized` prop it's given when a call comes back `401` (the JWT expires after 1 hour) instead of just showing a raw error — `App.jsx` clears the token and bounces back to the login screen with an explanatory message.

## Not in this slice (next iterations)

Order detail and cancel, admin views, routing.
