# OrderSphere Web UI

Log in (or register), and see your own orders. Talks to the API Gateway directly — no server of its own, no routing library, no state persistence beyond the current tab.

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
- `src/App.jsx` — holds the token and the current view (`login`/`register`) in memory; refreshing the page logs you out.

## Not in this slice (next iterations)

`localStorage` token persistence, product browsing, order placement, admin views, routing.
