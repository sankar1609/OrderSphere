# OrderSphere Web UI

Log in (or register), and browse your own orders and the product catalog. Talks to the API Gateway directly — no server of its own, no routing library, no state persistence beyond the current tab.

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
- `src/App.jsx` — holds the token, the current auth view (`login`/`register`), and the current authenticated page (`orders`/`products`) in memory; refreshing the page logs you out.

## Not in this slice (next iterations)

`localStorage` token persistence, order placement, admin views, routing.
