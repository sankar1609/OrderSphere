# OrderSphere

Cloud-native, microservices-based order and inventory management platform. See [CLAUDE.md](./CLAUDE.md) for full architecture details.

## Prerequisites

- Docker & Docker Compose (Docker Desktop must be running)
- Node.js 20+ (for the Web UI and the Postman/newman checks)
- Java 17+ and Maven 3.8+ — only needed to run the tests or a service outside Docker. The Docker images compile the code themselves.

## Running the Full Application

### 1. (Optional) Set secrets

Tokens are signed only by auth-service, with an RSA key it generates on first start and keeps in its database (supply your own with `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`). Other services verify tokens with auth-service's public keys, so there is no shared JWT secret.

The orders service authenticates to auth-service with a client secret; Docker Compose falls back to a dev-only default:

```bash
export ORDERS_CLIENT_SECRET=your-secret-here
```

### 2. Start the backend

From the repository root:

```bash
docker compose up -d --build
```

The first build takes roughly 10–15 minutes while Maven downloads dependencies inside Docker; later builds are much faster. This starts:

| Service | Port | Notes |
|---|---|---|
| postgres | 5432 | Single Postgres instance; per-service DBs created via `docker/postgres-init` (user/password `ordersphere`) |
| redis | — | Rate-limit counters for the API gateway (not published to the host) |
| rabbitmq | 5672, 15672 | Event broker; management UI on 15672 (user/password `ordersphere`) |
| service-registry | 8761 | Eureka; every service registers here |
| ordersphere-gateway | 8080 | API gateway — single entry point, routes `/{service-id}/...` |
| auth-service | 8081 | |
| ordersphere-orders | 8082 | |
| inventory-service | 8083 | |
| payment-service | 8084 | |
| shipping-service | 8085 | |
| notification-service | 8086 | |
| dummy-payment-gateway | 8087 | Stand-in payment provider (hosted payment page); not in Eureka, not behind the API gateway |

### 3. Wait until it's ready

Containers start immediately and keep retrying until their dependencies are up. After about a minute, `docker compose ps` should show the gateway and the six services as **healthy** (each has a health check on `/actuator/health/readiness`), and the Eureka dashboard at http://localhost:8761 should list **7 applications UP** (the six services plus the API gateway).

### 4. Start the Web UI

```bash
cd web-ui
npm install      # first time only
npm run dev
```

The Web UI runs on http://localhost:5173 and talks to the API gateway set in `web-ui/.env` (`VITE_GATEWAY_URL=http://localhost:8080`).

### 5. Use it

| What | URL |
|---|---|
| Web UI | http://localhost:5173 |
| API gateway | http://localhost:8080 |
| API docs (Swagger UI, all services) | http://localhost:8080/swagger-ui.html |
| Payment page (dummy gateway) | http://localhost:8087 |
| Eureka dashboard | http://localhost:8761 |
| Jaeger (distributed traces) | http://localhost:16686 |
| RabbitMQ management | http://localhost:15672 |

- **Accounts:** register customers in the Web UI. An admin account is created on first start: `admin` / `admin123` (override with `ADMIN_BOOTSTRAP_USERNAME` / `ADMIN_BOOTSTRAP_PASSWORD`).
- **Products:** only an admin or vendor can create products, and the Web UI has no admin screens yet — create them through the API, e.g. the "Create Product (Admin/Vendor)" request in `postman/api-reference.postman_collection.json`.
- **Paying:** placing an order takes you to the payment page. Test cards: `4242 4242 4242 4242` succeeds, `4000 0000 0000 0002` is declined (any name, a future MM/YY expiry, any CVC). No real money moves.

### 6. (Optional) Check the whole flow

With the stack running:

```bash
npx newman run postman/full-order-flow.postman_collection.json
npx newman run postman/feature-coverage.postman_collection.json
```

### Day-to-day commands

```bash
docker compose up -d --build ordersphere-orders   # rebuild and restart one service after a change
docker compose logs -f payment-service            # follow a service's logs
docker compose down                               # stop everything, keep the data
docker compose down -v                            # stop and wipe the data (deletes the postgres-data volume)
```

The gateway rate-limits each client IP (50 requests/s, and 5/s for login, register, refresh and token); over the limit it answers 429. Tune with `GATEWAY_RATE_LIMIT_PER_SECOND`, `GATEWAY_RATE_LIMIT_BURST`, `GATEWAY_AUTH_RATE_LIMIT_PER_SECOND` and `GATEWAY_AUTH_RATE_LIMIT_BURST`.

## Running on Kubernetes

Kustomize manifests for the whole stack live in [`k8s/`](k8s/README.md). On Docker Desktop with
Kubernetes enabled (stop docker-compose first - both use the same ports):

```bash
docker compose stop
k8s/deploy.sh                       # builds the images, applies k8s/overlays/docker-desktop, waits until ready
kubectl delete namespace ordersphere   # tear down
```

The gateway, checkout page, Eureka, Jaeger and RabbitMQ UI keep their localhost URLs, so the Web
UI and the Postman collections work unchanged.

## Running a Single Service Locally

For dev/debugging outside Docker:

```bash
cd services/<service-name>
mvn spring-boot:run
```

The service still needs `postgres`, `rabbitmq` and `service-registry` reachable (either the Docker Compose containers or local instances), with matching `DB_HOST` / `RABBITMQ_HOST` / `EUREKA_URI` environment variables.

To run one module's tests (this also rebuilds the shared `common-*` modules it depends on):

```bash
mvn verify -pl services/<service-name> -am
```
