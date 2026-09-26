# OrderSphere Web UI

Log in (or register), browse your own orders and the product catalog, and place new orders. Talks to the API Gateway directly — no server of its own, no routing library. The login token is persisted in `localStorage`, so a page refresh keeps you logged in.

## Prerequisites

The backend stack must already be running via `docker compose up -d` from the repo root, including the gateway's CORS config for `http://localhost:5173` (see `services/ordersphere-gateway/src/main/resources/application.yml`).

## Run it

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173`. Log in with an existing account, or register a new one — registration always creates a CUSTOMER (other roles are admin-provisioned, not self-service) and logs you straight in afterward.

## What's here

- `src/api.js` — fetch wrapper, gateway base URL from `VITE_GATEWAY_URL` in `.env`.
- `src/components/Login.jsx` — calls `POST /auth-service/auth/login`.
- `src/components/Register.jsx` — calls `POST /auth-service/auth/register`, then logs in with the same credentials (registration itself returns no token).
- `src/components/OrdersList.jsx` — calls `GET /ordersphere-orders/orders` with the bearer token, scoped server-side to the logged-in customer.
- `src/components/ProductsList.jsx` — calls `GET /inventory-service/inventory/products` with the bearer token; shows SKU, name, and stock levels.
- `src/components/PlaceOrder.jsx` — builds an item list from the product catalog and calls `POST /ordersphere-orders/orders`. Two backend gaps this screen works around:
  - **Pricing isn't modeled anywhere in the backend** (no price field on any product) — the order create DTO still requires a pre-computed `amount`, so the user types the total manually instead of it being calculated from items.
  - **No payment-method UI exists yet** — the screen auto-creates one (`POST /payment-service/payment-methods`, type `CARD`, a generated placeholder token) the first time a customer places an order if they don't already have one, then reuses their first payment method on subsequent orders.
- `src/App.jsx` — holds the token (synced to `localStorage` on every change, read back on load) and the current auth view (`login`/`register`) and authenticated page (`orders`/`products`/`placeOrder`) in memory. Also owns session-expiry handling: `api.js`'s fetch wrapper tags a non-2xx response's thrown `Error` with `error.status`, and each authenticated screen calls the `onUnauthorized` prop it's given when a call comes back `401` (the JWT expires after 1 hour) instead of just showing a raw error — `App.jsx` clears the token and bounces back to the login screen with an explanatory message.

## Not in this slice (next iterations)

Real pricing/payment-method management, admin views, routing.
