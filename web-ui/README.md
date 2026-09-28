# OrderSphere Web UI

Log in (or register), browse your own orders and the product catalog, place new orders and pay for them on the payment provider's hosted checkout page. Talks to the API Gateway directly — no server of its own, no routing library. The login token is persisted in `localStorage`, so a page refresh keeps you logged in.

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
- `src/components/OrdersList.jsx` — calls `GET /ordersphere-orders/orders` with the bearer token, scoped server-side to the logged-in customer; shows each order's server-calculated total (a dash for orders placed before pricing existed) and a "Pay now" link (the order's `checkoutUrl`) while an order is `AWAITING_PAYMENT`. When the app is opened with a payment-return query (see `App.jsx`), it polls that order (`GET /ordersphere-orders/orders/{id}`) until it leaves `AWAITING_PAYMENT` and shows whether it was confirmed or cancelled.
- `src/components/ProductsList.jsx` — calls `GET /inventory-service/inventory/products` with the bearer token; shows SKU, name, price, and stock levels.
- `src/components/PlaceOrder.jsx` — builds an item list from the product catalog (showing each line's unit price and subtotal plus an estimated total) and calls `POST /ordersphere-orders/orders`. The estimate is display-only: the request carries no amount, and the backend calculates the total it charges from Inventory's prices. The response carries the order's `checkoutUrl`, and the browser is sent there (`window.location.assign`) to pay on the payment provider's page — in development the dummy payment gateway on `http://localhost:8087` (test card `4242 4242 4242 4242` succeeds, `4000 0000 0000 0002` is declined). Card details never pass through this app.
- `src/App.jsx` — holds the token (synced to `localStorage` on every change, read back on load) and the current auth view (`login`/`register`) and authenticated page (`orders`/`products`/`placeOrder`) in memory. On load it reads the payment provider's return query (`?payment=success|cancelled&orderId=N`), strips it from the address bar, and opens the orders page for that order. The login token survives the round trip to the payment page because it lives in `localStorage`. Also owns session-expiry handling: `api.js`'s fetch wrapper tags a non-2xx response's thrown `Error` with `error.status`, and each authenticated screen calls the `onUnauthorized` prop it's given when a call comes back `401` (the JWT expires after 1 hour) instead of just showing a raw error — `App.jsx` clears the token and bounces back to the login screen with an explanatory message.

## Not in this slice (next iterations)

Order detail and cancel, admin views, routing.
