# OrderSphere Web UI

Log in (or register), browse your own orders and the product catalog, place new orders and pay for them on the payment provider's hosted checkout page. Vendors and admins can also add products and restock them. Talks to the API Gateway directly — no server of its own, no routing library. The login token is persisted in `localStorage`, so a page refresh keeps you logged in.

## Prerequisites

The backend stack must already be running via `docker compose up -d` from the repo root, including the gateway's CORS config for `http://localhost:5173` (see `services/ordersphere-gateway/src/main/resources/application.yml`).

## Run it

```bash
npm install
npm run dev
```

Opens on `http://localhost:5173`. Log in with an existing account, or register a new one as a **Customer** or a **Vendor** (the only self-service roles; ADMIN is admin-provisioned) — you're logged straight in afterward. The bootstrapped admin is `admin` / `admin123`.

## What's here

- `src/format.js` — `formatMoney` for rendering prices and totals.
- `src/api.js` — fetch wrapper, gateway base URL from `VITE_GATEWAY_URL` in `.env`. Access tokens last 15 minutes: when an authenticated call gets a 401 it exchanges the refresh token once (`POST /auth-service/auth/refresh`) and retries. Refresh tokens are single-use, so concurrent 401s share one in-flight refresh; only if that fails does the app fall back to the "session expired" login screen.
- `src/components/Login.jsx` — calls `POST /auth-service/auth/login`.
- `src/auth.js` — `roleOf(token)` reads the access token's `role` claim, used only to decide what to show (e.g. the Manage Products tab); the backend still authorizes every call.
- `src/components/Register.jsx` — calls `POST /auth-service/auth/register` with the chosen account type (CUSTOMER or VENDOR), then logs in with the same credentials (registration itself returns no token).
- `src/components/ManageProducts.jsx` — ADMIN/VENDOR only (the tab is hidden for others). A create-product form (`POST /inventory-service/inventory/products`: SKU, name, unit price, starting stock, reorder threshold; a duplicate SKU shows the backend's 409 message) and the catalog with a Restock control per product (`POST /inventory-service/inventory/products/{sku}/restock`). Products at or below their reorder threshold are highlighted as low stock.
- `src/components/OrdersList.jsx` — calls `GET /ordersphere-orders/orders` with the bearer token, scoped server-side to the logged-in customer; shows each order's server-calculated total (a dash for orders placed before pricing existed), the reason under any CANCELLED order (`cancellationReason`, e.g. "Not enough stock: …"), and a "Pay now" link (the order's `checkoutUrl`) while an order is `AWAITING_PAYMENT`. When the app is opened with a payment-return query (see `App.jsx`), it polls that order (`GET /ordersphere-orders/orders/{id}`) until it leaves `AWAITING_PAYMENT` and shows whether it was confirmed or cancelled.
- `src/components/ProductsList.jsx` — calls `GET /inventory-service/inventory/products` with the bearer token; shows SKU, name, price, and stock levels.
- `src/components/PlaceOrder.jsx` — builds an item list from the product catalog (out-of-stock products can't be picked, and a product can't be added beyond its available quantity minus what's already in the cart; if stock runs out before the order is placed, Inventory rejects the whole order and the page shows why) (showing each line's unit price and subtotal plus an estimated total) and calls `POST /ordersphere-orders/orders`. The estimate is display-only: the request carries no amount, and the backend calculates the total it charges from Inventory's prices. The response carries the order's `checkoutUrl`, and the browser is sent there (`window.location.assign`) to pay on the payment provider's page — in development the dummy payment gateway on `http://localhost:8087` (test card `4242 4242 4242 4242` succeeds, `4000 0000 0000 0002` is declined). Card details never pass through this app.
- `src/App.jsx` — holds the access and refresh tokens (synced to `localStorage` on every change, read back on load; "Log out" also ends the session server-side via `POST /auth-service/auth/logout`) and the current auth view (`login`/`register`) and authenticated page (`orders`/`products`/`placeOrder`) in memory. On load it reads the payment provider's return query (`?payment=success|cancelled&orderId=N`), strips it from the address bar, and opens the orders page for that order. The login token survives the round trip to the payment page because it lives in `localStorage`. Also owns session-expiry handling: `api.js`'s fetch wrapper tags a non-2xx response's thrown `Error` with `error.status`, and each authenticated screen calls the `onUnauthorized` prop it's given when a call comes back `401` (the JWT expires after 1 hour) instead of just showing a raw error — `App.jsx` clears the token and bounces back to the login screen with an explanatory message.

## Not in this slice (next iterations)

Order detail and cancel, shipment tracking, notifications, admin views beyond products (roles, failed compensations, unshipped orders), routing.
