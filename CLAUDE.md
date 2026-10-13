# OrderSphere - Agent Architecture & Service Guide

## 📋 Project Overview

**OrderSphere** is a cloud-native, microservices-based order and inventory management platform designed for enterprise environments where trust, auditability, and clear service boundaries are critical. The system supports reliable order processing, inventory control, and transparent auditability across multiple trusted organizations.

---

## 🏗️ System Architecture

### Architectural Principles

- **Orchestrated Saga + Events**: the order saga is orchestrated over synchronous REST with compensation; domain events on RabbitMQ drive everything that doesn't block the request (notifications, shipment cancellation, payment progress)
- **Audit as a trust layer**: every domain event goes into audit-service's tamper-evident hash chain; anchoring that chain in a shared ledger (Hyperledger Fabric) is planned - a trust layer, not the transaction layer
- **Cloud-Native**: Containerized services, run with Docker Compose or on Kubernetes (`k8s/`, Kustomize)

---

## 🤖 Core Agents (Microservices)

### 1. **API Gateway** (`ordersphere-gateway`)
- **Port:** 8080
- **Purpose:** Single entry point for all external client requests
- **Responsibilities:**
  - Request routing via Eureka discovery locator: `/{service-id}/**` → that service (e.g. `/ordersphere-orders/orders`)
  - Client-side load balancing across registered instances
  - CORS for the Web UI dev server (`http://localhost:5173`; GET/POST/PUT/PATCH/DELETE/OPTIONS)
  - Rate limiting (`RequestRateLimiter`, token buckets in Redis, per client IP): 50 req/s burst 100 on every route; login/register/refresh/token 5 req/s burst 30 (`GATEWAY_*RATE_LIMIT*` env vars). Over the limit → 429 with `X-RateLimit-*` headers; if Redis is down requests are let through. The client IP is the TCP peer; `X-Forwarded-For` is only used behind trusted proxies (`GATEWAY_TRUSTED_PROXY_HOPS`, default 0 - otherwise a client could send a fake address each request and dodge the limit), and then only the entry the outermost trusted proxy appended
- **Not implemented yet:** request validation
- **Technology:** Spring Cloud Gateway
- **Interacts With:** All downstream services

---

### 2. **Auth Service** (`auth-service`)
- **Port:** 8081
- **Purpose:** Centralized authentication and authorization
- **Responsibilities:**
  - User registration and login (`POST /auth/register`, `POST /auth/login`, `GET /auth/me`)
  - The only JWT signer: RS256 with an RSA key generated on first start and stored in `signing_keys` (or supplied via `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`); public keys published at `GET /auth/.well-known/jwks.json`
  - Service identities via OAuth2 client credentials (`POST /auth/token`, HTTP Basic client id/secret) → 5-minute `SERVICE`-role token; clients configured as `auth.clients.<id>.secret` (`orders-service`, secret `ORDERS_CLIENT_SECRET`)
  - Role-based access control (RBAC); ADMIN can list users (`GET /auth/admin/users`) and change anyone's role but their own (`PATCH /auth/admin/users/{id}/role`; 409 for your own, so the last admin can't lock everyone out)
  - Password hashing with bcrypt; passwords 8-72 characters (bcrypt ignores bytes past 72)
  - Usernames 3-50 of `[A-Za-z0-9._-]` (no look-alikes with spaces) and unique regardless of case
- **Database:** PostgreSQL (auth_db)
- **Key Features:**
  - Access tokens expire after 15 minutes (`JWT_EXPIRATION_MILLIS`); sessions continue via refresh tokens (`POST /auth/refresh`, 30 days, `AUTH_REFRESH_TOKEN_TTL`)
  - Refresh tokens are stored hashed and single-use: each refresh rotates them, and reusing a spent one revokes the whole session (token family). Exception: a token rotated within the last 30s (`auth.refresh-token-reuse-grace`) of a still-live session gets a sibling token instead, so two browser tabs refreshing at once don't log the user out
  - `POST /auth/logout` ends one session, `POST /auth/logout-all` ends all of a user's sessions; an admin role change also ends them
  - Support for multiple user roles (CUSTOMER, VENDOR, ADMIN, AUDITOR)
  - Bootstrapped admin account on startup (`ADMIN_BOOTSTRAP_USERNAME`/`ADMIN_BOOTSTRAP_PASSWORD`, defaults `admin`/`admin123`)
- **Events Produced:** UserRegisteredEvent, UserAuthenticatedEvent
- **Events Consumed:** None

---

### 3. **Orders Service** (`ordersphere-orders`)
- **Port:** 8082
- **Purpose:** Order lifecycle management and saga orchestration
- **Responsibilities:**
  - Order placement and creation
  - Order status tracking (PENDING, AWAITING_PAYMENT, CONFIRMED, CANCELLED) - delivery progress lives on the shipment, not the order
  - Saga orchestration for distributed order processing
  - Pricing: the order total is always derived from Inventory's catalog unit prices, never from the client
  - Customer order queries and customer-initiated cancellation (idempotent; 409 once delivered)
  - Requests are validated before anything is reserved or charged: currency must be an ISO 4217 code (`@IsoCurrency`), at most 50 lines of up to 10,000 units, destination up to 255 characters
- **Database:** PostgreSQL (orders_db)
- **Key Features:**
  - Orchestrated saga: calls Inventory, Payment and Shipping synchronously over load-balanced REST
  - `OrderSagaProgressJob` (~5s sweep) polls payment status, then confirms the order and creates the shipment
  - Compensation: releases inventory on payment failure/cancellation; refunds if already CONFIRMED
  - Compensations are durable (`order_compensations` outbox, `CompensationService`): recorded in the same transaction as the order change, attempted right after commit, retried by the saga sweep with exponential back-off (5s doubling to 10 min, 20 attempts). Outages, timeouts, 401/403/408/429 are retried; outright rejections (e.g. 409) and exhausted retries are marked FAILED and logged as needing attention
  - Shipment creation is retried the same way: a paid order whose shipment can't be created is still CONFIRMED, and the saga sweep retries (`OrderShipmentService`, `shipment_*` columns on `orders`) with the same back-off until shipping accepts, rejects (4xx) or 20 attempts run out. Shipping returns the existing shipment for a repeated request, so a retry can't ship twice
  - Admin endpoints for paid orders without a shipment (ADMIN only): `GET /orders/admin/unshipped` (`retrying: false` = given up), `POST /orders/admin/unshipped/{orderId}/retry` - tries again now with a fresh retry budget (409 if the order isn't CONFIRMED or already has a shipment)
  - Admin endpoints for the outbox (ADMIN only): `GET /orders/admin/compensations?status=FAILED` (default FAILED; also PENDING/DONE), `GET /orders/admin/compensations/{id}`, `POST /orders/admin/compensations/{id}/retry` - re-queues a FAILED compensation with a fresh retry budget and attempts it immediately (409 if it isn't FAILED)
  - Resilience4j circuit breakers on all outbound clients; a downstream 4xx is ignored (see `DownstreamClientErrorPredicate`), only 5xx/408/429/connection failures count
- **Events Produced:** OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent
- **Events Consumed:** PaymentCompletedEvent, PaymentFailedEvent

---

### 4. **Inventory Service** (`inventory-service`)
- **Port:** 8083
- **Purpose:** Stock reservation, release, and inventory tracking
- **Responsibilities:**
  - Product catalog management with unit prices of at least 0.01 (create is ADMIN/VENDOR only; the creator is recorded as the product's `createdBy` owner)
  - Low-stock alerts: when a reservation takes a product's available stock from above its reorder threshold to at/below it, `StockLowEvent` is published once (not again while it stays low; restock/release/expiry re-arm it)
  - Stock level tracking
  - Inventory reservation, confirmation and release for orders (internal: SERVICE/ADMIN only - called by the orders saga over REST)
  - Restocking (`POST /inventory/products/{sku}/restock`): ADMIN any product, VENDOR only products they created (403 otherwise)
  - Input limits (`InventoryLimits`): SKUs are 1-64 of `[A-Za-z0-9._-]` (they travel in URL paths), names up to 255, at most 10,000 units per order line and 50 lines, at most 1,000,000 added per create/restock and 100,000,000 on hand - so stock arithmetic can't overflow
  - All-or-nothing reservation: if any line asks for more than is available, nothing is reserved and the request is rejected (409, "Not enough stock: SKU (N requested, M available)") - there are no backorders
- **Database:** PostgreSQL (inventory_db)
- **Key Features:**
  - Real-time stock availability checks
  - Saga compensation via reservation release
  - Reservation holds expire after 15 minutes (`ReservationExpiryJob`, 60s sweep)
- **Events Produced:** InventoryReservedEvent, InventoryReleasedEvent, StockLowEvent
- **Events Consumed:** None (driven by REST calls from Orders)

---

### 5. **Payment Service** (`payment-service`)
- **Port:** 8084
- **Purpose:** Collects payments through a hosted-checkout payment provider
- **Responsibilities:**
  - Payment initiation (`POST /payments`, 202, internal: SERVICE/ADMIN only, customer named in the body): opens a checkout session with the provider and returns its `checkoutUrl`; the payment stays PENDING until the customer pays there
  - An existing payment for an orderId is reused only if customer, amount and currency match (409 otherwise)
  - Receiving the provider's outcome via a signed webhook (`POST /payments/webhooks/gateway`, HMAC-SHA256, no JWT)
  - Transaction status tracking (with `failureReason` for FAILED payments)
  - Refund processing (COMPLETED payments only, SERVICE/ADMIN only - customers get refunds by cancelling the order), executed through the provider
- **Database:** PostgreSQL (payment_db)
- **Key Features:**
  - Card details never reach OrderSphere - customers pay on the provider's page
  - `PaymentGatewayClient` abstraction; `DummyPaymentGatewayClient` talks to the dummy gateway (swap for a real provider)
  - `PaymentProcessingJob` (~5s sweep) reconciles PENDING payments with the provider (covers lost webhooks and expired checkouts) and settles refunds
  - **Reconciliation** (`ReconciliationService`, daily at 02:00 via `payment.reconciliation.cron`, or on demand): settled payments and refunds are compared with the provider's settlement report. Mismatches become findings: `CHARGED_NOT_RECORDED`, `RECORDED_NOT_CHARGED`, `AMOUNT_MISMATCH`, `REFUND_NOT_RECORDED`, `REFUND_NOT_EXECUTED`, `UNKNOWN_SESSION`. Nothing changes automatically: an admin **re-syncs** (the provider's state is applied through the normal paths, so the orders saga confirms or refunds the order) or **resolves with a note**; findings that later agree are closed as CLEARED. Admin API: `/payments/admin/reconciliation` (`POST /runs?hours=`, `GET /runs`, `GET /findings?status=`, `POST /findings/{id}/resync`, `POST /findings/{id}/resolve`)
  - Idempotent initiation per orderId; webhook and sweep serialized by a row lock
- **Not implemented yet:** real payment provider / PCI handling, saved cards
- **Events Produced:** PaymentInitiatedEvent, PaymentCompletedEvent, PaymentFailedEvent, RefundIssuedEvent
- **Events Consumed:** None (driven by REST calls from Orders)

---

### 5a. **Dummy Payment Gateway** (`dummy-payment-gateway`)
- **Port:** 8087 (not registered in Eureka, not behind the API gateway - it stands in for an external provider)
- **Purpose:** Development stand-in for a hosted-checkout card provider (Stripe Checkout-style)
- **Provides:**
  - Merchant API (`/api/checkout-sessions`, `/api/refunds`, and the settlement report `GET /api/reports/transactions?from&to` listing charges and refunds), authenticated with `Authorization: Bearer <GATEWAY_API_KEY>`
  - Hosted checkout page (`/checkout/{sessionId}`) with Pay and Cancel; redirects back to the Web UI (`?payment=success|cancelled&orderId=N`)
  - Signed webhooks (`X-Dummy-Gateway-Signature: sha256=<HMAC>`) to payment-service
- **Test cards:** `4242 4242 4242 4242` succeeds; `4000 0000 0000 0002` is declined (the customer can retry on the same page); any name, a future `MM/YY` expiry, any 3-4 digit CVC
- **Sessions:** stored in its own `gateway_db` (Flyway), so sessions, charges and refunds survive a restart; expire after `GATEWAY_SESSION_TTL` (10 min, below inventory's 15-min hold)

---

### 6. **Shipping Service** (`shipping-service`)
- **Port:** 8085
- **Purpose:** Shipment creation, tracking, and logistics management
- **Responsibilities:**
  - Shipment creation for confirmed orders (SERVICE/ADMIN only - called by the orders saga with its service token)
  - Tracking: `ShipmentProgressJob` advances CREATED → PICKED → IN_TRANSIT → DELIVERED one stage per ~5s sweep
  - Delivery confirmation
  - Return shipments for delivered OUTBOUND shipments (`POST /shipments/{id}/return`)
  - Cancels an undelivered shipment when its order is cancelled
- **Database:** PostgreSQL (shipping_db)
- **Key Features:**
  - Carrier integration (stub)
  - Ownership checks: customers only see/return their own shipments (others get 404); ADMIN sees all
- **Events Produced:** ShipmentCreatedEvent, ShipmentPickedEvent, ShipmentInTransitEvent, DeliveryConfirmedEvent
- **Events Consumed:** OrderCancelledEvent

---

### 7. **Notification Service** (`notification-service`)
- **Port:** 8086
- **Purpose:** Event-driven customer and organizational notifications
- **Responsibilities:**
  - Order, payment and shipment notifications, triggered by domain events
  - Notification preference management per channel (EMAIL, SMS, IN_APP, PUSH); a disabled channel marks notifications SKIPPED
  - Notifications sent directly (`POST /notifications`, SERVICE/ADMIN only)
- **Database:** PostgreSQL (notification_db)
- **Key Features:**
  - Template-based message composition (`NotificationTemplateRenderer`)
  - Low-stock alerts (IN_APP) to the product's owner; products with no recorded owner alert `LOW_STOCK_FALLBACK_RECIPIENT` (default `admin`)
  - Async delivery via `NotificationDeliveryJob` (~5s sweep, PENDING → SENT), retried up to 3 times
  - Customers only see their own notifications
- **Events Produced:** NotificationSentEvent, NotificationFailedEvent
- **Events Consumed:** OrderConfirmedEvent, OrderCancelledEvent, PaymentCompletedEvent, PaymentFailedEvent, ShipmentCreatedEvent, ShipmentPickedEvent, ShipmentInTransitEvent, DeliveryConfirmedEvent, StockLowEvent

---

### 7a. **Audit Service** (`audit-service`)
- **Port:** 8088
- **Purpose:** Tamper-evident record of everything that happened - the audit trail the platform's "trust and auditability" goal calls for
- **Responsibilities:**
  - Records **every** domain event (queue `audit-service.events` bound to `#`; the raw message, so new event types are recorded without touching this service) as an entry in an append-only log: event, publishing service (`x-source-service` header), who it was acting for (`x-actor`: the username, or `orders-service` for saga calls, absent for scheduled jobs), order id, trace id, the payload exactly as published
  - **Hash chain:** each entry's `hash` = SHA-256 over the previous entry's hash and its own content (`ChainHasher`, length-prefixed fields), so an altered, removed or reordered entry no longer verifies. Appends serialize on the one-row `audit_chain_head` lock; a redelivered event (same `event_id`) is recorded once
  - **Verification** (`AuditVerifier`; every `AUDIT_VERIFY_INTERVAL`, default 5 min, at startup, and on demand) walks the chain and reports the first broken `seq` and why
  - **Anchoring** (`AuditAnchor`, every `AUDIT_ANCHOR_INTERVAL`): the chain head is published outside the database and remembered in `audit_anchors`, so a tail that was cut off and rebuilt still fails verification. Today the anchor is the application log (`AUDIT ANCHOR seq=.. hash=..`, in Loki); **this interface is where a shared ledger (Hyperledger Fabric) plugs in** once another organization needs to trust the records
  - Read-only API for `AUDITOR` and `ADMIN` (everyone else 403): `GET /audit/entries?orderId=&eventType=&actor=&username=&from=&to=&page=&size=`, `GET /audit/entries/{seq}` (payload + hashes), `GET /audit/orders/{orderId}` (an order's timeline), `POST /audit/verify`, `GET /audit/status`. There is no endpoint that writes, edits or deletes an entry
- **Database:** PostgreSQL (audit_db). `audit_entries` and `audit_anchors` reject UPDATE/DELETE/TRUNCATE by trigger; someone who disables the trigger is what the chain then exposes
- **Metrics / alert:** `ordersphere_audit_entries`, `ordersphere_audit_chain_valid` → alert `AuditChainBroken` (critical)
- **Events Produced:** None
- **Events Consumed:** all of them

---

### 8. **Service Registry** (`service-registry`)
- **Port:** 8761
- **Purpose:** Service discovery and registration center
- **Responsibilities:**
  - Service registration (Eureka Server)
  - Service lookup and health checking
  - Load balancing metadata
  - Instance failover support
- **Technology:** Netflix Eureka
- **Configuration:**
  - All services register with this registry
  - Eureka dashboard available at `http://localhost:8761`

---

### 9. **Common Libraries** (Shared Code)

#### `common-security`
- JWT verification only (`JwtVerifier`): RS256 public keys from auth-service's JWKS (`jwt.jwks-uri`, fetched lazily, cached, re-fetched for unknown key ids; concurrent cache misses wait for a single fetch, so a burst of requests right after startup isn't answered 401) or a fixed `jwt.public-key`
- `JwtAuthenticationFilter` mapping the `role` claim to `ROLE_*` authorities, and putting the caller (`user`: username, or the client id for SERVICE tokens) in the logging context for the request
- Test-jar with `TestJwtIssuer`, auto-configured so every service's tests can mint tokens without auth-service

#### `common-events`
- Event definitions (POJOs)
- Event serialization/deserialization
- Base event classes
- Event publisher utilities; `DomainEventRelay` logs one INFO line per relayed event, tagged with the order id for events about an order (`OrderScoped`), and stamps each message with `x-source-service` and `x-actor` headers (read by audit-service)

#### `common-logging`
- The shared `logback-spring.xml` every application picks up: `LOG_FORMAT=text` (default - Spring Boot's readable console pattern, for local runs and tests) or `json` (one JSON object per line via logstash-logback-encoder, set in docker-compose and k8s)
- JSON lines carry `service`, `level`, `logger`, `message`, a shortened `stack_trace` and every MDC entry: `traceId`/`spanId`, `user`, `orderId`, `paymentId`

---

## 📊 Data & Communication Flow

### Orchestration + Events
The order saga is **orchestrated** by the Orders service over synchronous, load-balanced REST (resolved through Eureka, wrapped in Resilience4j circuit breakers). Domain events are published to RabbitMQ for everything that doesn't need to block the request.

- **Broker:** RabbitMQ, topic exchange `ordersphere.events` (routing keys like `payment.completed`), dead-letter exchange `ordersphere.events.dlx`
- **Queues:** `notification-service.events`, `ordersphere-orders.payment-events`, `shipping-service.order-events`, `audit-service.events` (everything, `#`) - each with a `.dlq`
- **Publishing:** services raise Spring application events; `DomainEventRelay` (common-events) forwards them to the exchange

```
Order Placement Flow:
1. Client → API Gateway → Orders Service (create order, status PENDING)
2. Orders → Inventory (REST): reserve stock; response prices each line → order total computed
3. Orders → Payment (REST): initiate payment → checkout session opened → order AWAITING_PAYMENT with `checkoutUrl`; payment is PENDING
4. Web UI redirects the browser to the payment provider's hosted checkout page; the customer pays (or cancels)
   → provider webhook → payment-service → PaymentCompletedEvent / PaymentFailedEvent
   (payment-service's ~5s sweep also polls the provider, so a lost webhook or an expired checkout is still picked up)
5. Orders consumes the payment event (or its ~5s saga job polls payment status) → confirms the reservation with Inventory → creates shipment via Shipping (REST) → CONFIRMED
   (on FAILED → releases inventory → CANCELLED)
6. Shipping job (~5s per stage) → PICKED → IN_TRANSIT → DELIVERED, emitting an event per stage
7. Notification Service consumes order/payment/shipment events → queues notifications → delivery job sends them
```

### Database Strategy
- **Database per Service**: Each microservice has its own PostgreSQL database
- **Data Consistency**: Eventual consistency via event-driven saga pattern
- **Schemas**: Independently managed, no cross-service foreign keys

---

## 🗄️ Databases

- One PostgreSQL instance, one database per service (including `audit_db`) plus `gateway_db` for the dummy payment provider, created by `docker/postgres-init` on a fresh volume. On an existing volume, create a newly added one once by hand, e.g. `docker exec ordersphere-postgres psql -U ordersphere -d postgres -c "CREATE DATABASE audit_db;"`
- Flyway migrations per service (`src/main/resources/db/migration`); no cross-service foreign keys

---

## 🚀 Deployment Architecture

### Docker Compose (Development)
- `docker-compose.yml` - PostgreSQL, RabbitMQ (management UI on `:15672`, `ordersphere`/`ordersphere`), Redis (gateway rate limits), Jaeger (traces, UI on `:16686`), Prometheus (metrics and alert rules, UI on `:9090`), Loki + Alloy + Grafana (logs and dashboards, UI on `:3000`), Eureka, the gateway, all seven services and the dummy payment gateway (`:8087`)
- Each service has its own `Dockerfile` under `services/<name>/`; rebuild one with `docker compose up -d --build <service>`
- Images are tagged `ordersphere/<service>:local`, the same tags the Kubernetes manifests use
- The Dockerfiles build with a BuildKit cache mount for `/root/.m2` shared by every service image, so Maven dependencies are downloaded once rather than per image or per `pom.xml` change

### Kubernetes (`k8s/`, Kustomize)
- `k8s/base` deploys the whole stack into the `ordersphere` namespace; `k8s/overlays/docker-desktop` runs it on Docker Desktop's Kubernetes with the images `docker compose build` produces; `k8s/deploy.sh` builds, applies and waits. Stop docker-compose first (same ports). Details and Docker Desktop troubleshooting load from `k8s/CLAUDE.md` when working there
- Files under `k8s/` that are copies of `docker/postgres-init` and `observability/` configs must stay identical to the originals (CI checks)

---

## 🔐 Security Features

- **JWT Authentication:** stateless RS256 tokens signed only by auth-service; every other service verifies them with the public keys from auth-service's JWKS (`common-security`), so no service can mint or forge a token. Tokens carry `iss=ordersphere-auth`, `kid`, `role`, `typ` (`access`/`service`)
- **Role-Based Access Control (RBAC):** `@PreAuthorize` role checks per endpoint
- **Resource ownership:** customers only see their own orders, payments, shipments and notifications (404 otherwise); ADMIN (and the SERVICE identity for payments/shipments) sees all
- **Internal endpoints:** stock reservations, payment initiation/refund, shipment creation and direct notifications require the `SERVICE` or `ADMIN` role - customers get 403
- **Payments:** card details are entered only on the payment provider's hosted page; the provider's webhook is authenticated by HMAC signature and its merchant API by secret key
- **Password Security:** Bcrypt hashing with salt
- **Token Expiration & Revocation:** 15-minute access tokens plus rotating, revocable refresh tokens. Logout/role changes stop a session from refreshing; an access token already issued stays valid until it expires (at most 15 minutes)
- **Service-to-Service Auth:** the orders saga calls every downstream service with its own `SERVICE`-role token, never the customer's. `ServiceTokenProvider` obtains it from auth-service via client credentials (`POST /auth/token`) and caches it until shortly before expiry
- **Audit Trail:** audit-service's hash-chained, append-only log of every domain event, readable by `AUDITOR`/`ADMIN` only (the `AUDITOR` role exists for this; assign it via the admin role change). Anchoring it in a ledger shared with other organizations (Hyperledger Fabric) is the planned next step

---

## 📝 API Documentation

### Gateway API Endpoints
- **Base URL:** `http://localhost:8080/{service-id}` (e.g. `http://localhost:8080/ordersphere-orders/orders`)

### OpenAPI / Swagger
- **Swagger UI:** `http://localhost:8080/swagger-ui.html` (served by the gateway) - pick a service in the top-right dropdown. Click **Authorize** and paste an access token from `POST /auth-service/auth/login` (or a SERVICE token from `POST /auth-service/auth/token`); "Try it out" calls go through the gateway.
- **Specs:** every service serves its own OpenAPI 3 spec at `/v3/api-docs` (through the gateway: `/{service-id}/v3/api-docs`), no token needed. Generated from the code by springdoc: validation limits come from the request annotations, and each operation's description states the role its `@PreAuthorize` requires.
- **Shared conventions** live in `common-security`'s `OpenApiAutoConfiguration`: server URL `/{spring.application.name}` (the gateway path), Bearer JWT on every operation except `ordersphere.openapi.public-paths`, a shared `ErrorResponse` schema and 401/403 responses.
- The Postman collections in `postman/` remain the runnable end-to-end examples.

---

## 🔭 Observability (Distributed Tracing)

- **Jaeger UI:** `http://localhost:16686` (the `jaeger` container in docker-compose). Pick a service, e.g. `ordersphere-orders`, and open a trace to see the cross-service waterfall.
- **What's traced** (Micrometer Tracing over OpenTelemetry, exported via OTLP to `OTLP_TRACING_ENDPOINT`):
  - every request through the gateway into the services;
  - the orders saga's REST calls to inventory, payment and shipping (`RestClientConfig` passes the `ObservationRegistry` to its hand-built load-balanced builder);
  - RabbitMQ events from publish to consume (`RabbitObservationPostProcessor` in common-events turns observation on for every RabbitTemplate and listener factory - Boot 3.2 has no property for it);
  - each run of an `@Scheduled` sweep as its own trace (`TracedSchedulingAutoConfiguration` in common-events).
- **Correlation:**
  - every log line carries `[service,traceId,spanId]`;
  - every response has an `X-Trace-Id` header (`TraceIdResponseFilter` in common-security; the gateway's `TraceIdResponseHeaderFilter` covers responses it answers itself);
  - the Web UI appends the trace id to server-error messages.
- **Sampling:** 100% by default (`TRACING_SAMPLING_PROBABILITY`); lower it in production.
- **Not traced:** the dummy payment gateway. It stands in for an external provider, which wouldn't propagate our trace headers, so its webhook starts a new trace in payment-service; the payment's `orderId` links it to the order.

## 🪵 Centralized Logging (Loki + Grafana)

- **Grafana:** `http://localhost:3000` - the **OrderSphere logs** dashboard and *Explore* (LogQL, e.g. `{service=~".+"} | orderId="42"`); log lines link to their Jaeger trace and back
- Every application logs JSON lines (`LOG_FORMAT=json`, `common-logging`) carrying `service`, `level`, `traceId`, `user`, `orderId`, `paymentId`; Grafana Alloy ships them to Loki (72h retention)
- Pipeline internals, label design and the single-node Loki / Grafana memory gotchas load from `observability/CLAUDE.md` when working there

## 📈 Metrics & Alerting (Prometheus + Grafana)

- **Where to look:** Grafana `http://localhost:3000` → **OrderSphere overview** (firing alerts, orders/payments per minute, backlog tiles, request rate / errors / p95 per service) and **OrderSphere services** (one service: endpoints, JVM, DB pool, RabbitMQ, circuit breakers). Prometheus itself: `http://localhost:9090` (`/targets`, `/alerts`).
- **Scraping:** Prometheus scrapes `/actuator/prometheus` on the gateway and the seven services every 15s (`observability/prometheus/prometheus.yml`; same target names under compose and k8s). Not scraped: the service registry and the dummy payment gateway.
- **The endpoint is guarded by a scrape token, not a JWT:** `Authorization: Bearer <METRICS_SCRAPE_TOKEN>` (dev default `dev-metrics-scrape-token`; k8s Secret). The gateway routes `/{service-id}/**` to every service and its own port is public, so an open metrics endpoint would be public. Enforced by `MetricsScrapeTokenFilter` in common-security (services; their `SecurityConfig` permits the path, the filter checks) and by the gateway's own `WebFilter`. No token configured → the endpoint answers nobody. A user or admin JWT gets 401.
- **Business metrics:** `ordersphere_events_total{type}` - `DomainEventRelay` counts every relayed domain event, so the rates of orders placed/confirmed/cancelled, payments, refunds, shipments and notifications need no counters in the services. Backlog gauges read from the database at scrape time (`OrderMetrics`, `PaymentMetrics`, `InventoryMetrics`; NaN rather than a failed scrape if the DB is down): `ordersphere_orders{status}`, `ordersphere_compensations{status}`, `ordersphere_orders_unshipped{state=retrying|gave_up}`, `ordersphere_payments_pending`, `ordersphere_reconciliation_findings_open`, `ordersphere_products_low_stock`. Every metric carries `application` and Prometheus adds `service`.
- **Alert rules** (`observability/prometheus/alerts.yml`): `ServiceDown`, `HighErrorRate`, `SlowResponses`, `CircuitBreakerOpen`, `FailedCompensations`, `UnshippedOrdersGaveUp`, `OpenReconciliationFindings`, `AuditChainBroken`, `PaymentFailuresHigh`. Evaluated by Prometheus and shown in Grafana and on `:9090/alerts` - **nothing is sent anywhere** (no Alertmanager).
- **In tests** metrics export is off unless the test class has `@AutoConfigureObservability` (Spring Boot's default).
- **Not yet:** alert delivery (Alertmanager / notifications), metrics for the registry and the dummy gateway.

## 🧪 Testing & Quality Assurance

- **Unit Tests:** Per-service JUnit 5 + Mockito suites
- **Integration Tests:** `@SpringBootTest` + MockMvc against real PostgreSQL and RabbitMQ via Testcontainers (Docker required)
- **Formatting:** Spotless (google-java-format) runs `check` in the `verify` phase - fix with `mvn spotless:apply`
- **End-to-End Tests:** Postman collections run against the docker-compose stack with newman:
  - `postman/full-order-flow.postman_collection.json` - happy path, payment decline, cancellation with refund, negative checks
  - `postman/feature-coverage.postman_collection.json` - auth/RBAC, inventory, payments, returns, notifications, Eureka, RabbitMQ, circuit breakers, API docs, centralized logs (an order's lines found in Loki by trace id and order id), metrics (scrape-token guard, Prometheus targets, business metrics, alert rules), audit log (RBAC, an order's trail across services with actors, chain verification)
  - `postman/api-reference.postman_collection.json` - one example request per endpoint (no assertions)
- **UI End-to-End Tests:** Playwright in `web-ui/e2e` against the same running stack (`cd web-ui && npm run e2e`); not part of CI
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) runs `mvn verify`, the Web UI build and a Kubernetes manifest check (kustomize render + `kubeconform -strict`) on pushes and PRs to `master`/`develop`
- **Not yet:** contract tests, load tests

---

## 🔄 Saga Orchestration & Failure Handling

### Order Saga Pattern
The system uses the Saga pattern to maintain consistency across distributed services:

**Happy Path:**
1. Order Service creates order → PENDING
2. Inventory reserves stock and prices the lines → checkout session opened → Order AWAITING_PAYMENT (customer is sent to the payment page)
3. Customer pays on the hosted page → inventory reservation confirmed (so it can't expire) → shipment created → Order CONFIRMED
4. Shipment progresses to DELIVERED (the order stays CONFIRMED; delivery is tracked on the shipment)

**Failure & Compensation:**
- Not enough stock for any line, unknown SKU, or no price → order CANCELLED immediately with a `cancellationReason` the customer sees (e.g. "Not enough stock: SKU-1 (2 requested, 0 available)"), nothing reserved or charged
- Payment initiation fails → inventory released → CANCELLED
- Card declined → shown on the payment page; the customer can retry, the order keeps waiting
- Customer cancels on the payment page, or the checkout expires (10 min) → payment FAILED → inventory released → CANCELLED
- Customer cancels the order but then pays on the still-open page → the late payment is refunded automatically
- Reservation confirm fails: Inventory unavailable → order stays AWAITING_PAYMENT and the next saga sweep retries; Inventory rejects it (reservation already expired/released) → payment refunded → CANCELLED
- Customer cancels → inventory released; payment refunded if already CONFIRMED; undelivered shipment cancelled; 409 once delivered
- A refund or stock release that fails (payment/inventory down, token not yet verifiable) is kept in `order_compensations` and retried until it succeeds - it is never silently dropped
- Shipment creation fails (shipping down) → the order is still CONFIRMED (it's paid) and the saga sweep retries creating the shipment with back-off; if shipping rejects it or the retries run out it's listed for an admin at `/orders/admin/unshipped`

---

## 📚 Additional Components

### Web UI (`web-ui/`)
- React 18 + Vite + React Router; customer, vendor, admin and auditor screens, one address per page; Playwright end-to-end tests (`npm run e2e`, local only). Details load from `web-ui/CLAUDE.md` when working there; see also `web-ui/README.md`
- **Not yet:** UI tests in CI, component-level tests

---

## 🛠️ Development Setup

### Local Development
```bash
# Build and run all tests + the Spotless format check (what CI runs)
mvn verify

# Fix formatting before committing
mvn spotless:apply

# Start the full stack
docker compose up -d

# Rebuild one service after a change
docker compose up -d --build ordersphere-orders

# End-to-end tests against the running stack
npx newman run postman/full-order-flow.postman_collection.json
npx newman run postman/feature-coverage.postman_collection.json

# Web UI
cd web-ui && npm install && npm run dev

# Web UI end-to-end tests (stack must be running; first time: npx playwright install chromium)
cd web-ui && npm run e2e
```

### Build Individual Service
```bash
# -am also builds common-events/common-security; without it a stale copy in ~/.m2 can be used
mvn verify -pl services/shipping-service -am
```

---

## 🎯 Next Steps & Roadmap

- [x] Implement message broker (RabbitMQ)
- [x] Implement circuit breakers (Resilience4j)
- [x] CI pipeline (GitHub Actions)
- [x] Web UI - customer screens (orders, tracking, cancellation, returns, notifications), product management and admin screens
- [x] Fix: retry shipment creation for CONFIRMED orders left without a shipment
- [x] Security hardening: internal endpoints limited to SERVICE/ADMIN, RS256 tokens signed only by auth-service (JWKS), service identity via client credentials, refresh tokens with rotation/revocation
- [x] Gateway rate limiting (Redis), unauthenticated health probes, PATCH in gateway CORS
- [ ] Security hardening (later): OAuth2/OIDC, request validation at the gateway
- [x] Consume `StockLowEvent` - low-stock alerts to the product's vendor
- [x] Payment reconciliation (settlement report vs. our records, findings with admin re-sync/resolve)
- [x] API documentation: OpenAPI specs per service, one Swagger UI at the gateway
- [x] Distributed tracing (Micrometer Tracing + OpenTelemetry, Jaeger)
- [x] Kubernetes manifests (Kustomize, verified on Docker Desktop Kubernetes)
- [x] Audit trail: audit-service records every domain event in a hash-chained, append-only log (AUDITOR/ADMIN API and Web UI page, verification, alert)
- [ ] Anchor the audit chain in a shared ledger (Hyperledger Fabric) when a second organization needs to trust it - implement `AuditAnchor`
- [ ] Implement CQRS for analytics/reporting
- [x] Centralized logging (Loki + Alloy + Grafana instead of ELK; JSON logs linked to traces)
- [x] Metrics and alerting (Prometheus scraping every service with a scrape token, business metrics, alert rules, Grafana dashboards)
- [ ] Alert delivery (Alertmanager / notifications)
- [ ] Performance optimization and caching (Redis)
