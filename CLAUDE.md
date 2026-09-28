# OrderSphere - Agent Architecture & Service Guide

## 📋 Project Overview

**OrderSphere** is a cloud-native, microservices-based order and inventory management platform designed for enterprise environments where trust, auditability, and clear service boundaries are critical. The system supports reliable order processing, inventory control, and transparent auditability across multiple trusted organizations.

**Version:** 1.0.0-SNAPSHOT  
**Tech Stack:** Java 17 | Spring Boot 3.2.1 | Spring Cloud | PostgreSQL | RabbitMQ | Docker | React

---

## 🏗️ System Architecture

### Architectural Principles

- **Microservices Pattern**: Each service is independently deployable with its own database
- **Orchestrated Saga + Events**: the order saga is orchestrated over synchronous REST with compensation; domain events on RabbitMQ drive everything that doesn't block the request (notifications, shipment cancellation, payment progress)
- **Blockchain for Audit (planned)**: Immutable audit trails using Hyperledger Fabric (trust layer, not transaction layer)
- **Cloud-Native**: Containerized services (Docker Compose today; Kubernetes planned)
- **Service Discovery**: Eureka-based service registry for dynamic service location

---

## 🤖 Core Agents (Microservices)

### 1. **API Gateway** (`ordersphere-gateway`)
- **Port:** 8080
- **Purpose:** Single entry point for all external client requests
- **Responsibilities:**
  - Request routing via Eureka discovery locator: `/{service-id}/**` → that service (e.g. `/ordersphere-orders/orders`)
  - Client-side load balancing across registered instances
  - CORS for the Web UI dev server (`http://localhost:5173`; GET/POST/PUT/DELETE/OPTIONS - **PATCH is not allowed yet**)
- **Not implemented yet:** rate limiting, request validation, centralized logging/metrics
- **Technology:** Spring Cloud Gateway
- **Interacts With:** All downstream services

---

### 2. **Auth Service** (`auth-service`)
- **Port:** 8081
- **Purpose:** Centralized authentication and authorization
- **Responsibilities:**
  - User registration and login (`POST /auth/register`, `POST /auth/login`, `GET /auth/me`)
  - JWT token generation (validated in every service by `common-security`)
  - Role-based access control (RBAC); ADMIN can change a user's role (`PATCH /auth/admin/users/{id}/role`)
  - Password hashing with bcrypt
- **Database:** PostgreSQL (auth_db)
- **Key Features:**
  - JWT token expiration: 1 hour
  - Support for multiple user roles (CUSTOMER, VENDOR, ADMIN, AUDITOR)
  - Bootstrapped admin account on startup (`ADMIN_BOOTSTRAP_USERNAME`/`ADMIN_BOOTSTRAP_PASSWORD`, defaults `admin`/`admin123`)
- **Not implemented yet:** token refresh, token revocation/logout
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
- **Database:** PostgreSQL (orders_db)
- **Key Features:**
  - Orchestrated saga: calls Inventory, Payment and Shipping synchronously over load-balanced REST
  - `OrderSagaProgressJob` (~5s sweep) polls payment status, then confirms the order and creates the shipment
  - Compensation: releases inventory on payment failure/cancellation; refunds if already CONFIRMED
  - Resilience4j circuit breakers on all outbound clients; a downstream 4xx is ignored (see `DownstreamClientErrorPredicate`), only 5xx/408/429/connection failures count
- **Events Produced:** OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent
- **Events Consumed:** PaymentCompletedEvent, PaymentFailedEvent

---

### 4. **Inventory Service** (`inventory-service`)
- **Port:** 8083
- **Purpose:** Stock reservation, release, and inventory tracking
- **Responsibilities:**
  - Product catalog management with unit prices (create is ADMIN/VENDOR only)
  - Stock level tracking
  - Inventory reservation, confirmation and release for orders (called by the orders saga over REST)
  - Restocking (`POST /inventory/products/{sku}/restock`, ADMIN/VENDOR)
  - Backorder management (a shortfall is backordered; the order still proceeds and is charged in full)
- **Database:** PostgreSQL (inventory_db)
- **Key Features:**
  - Real-time stock availability checks
  - Saga compensation via reservation release
  - Reservation holds expire after 15 minutes (`ReservationExpiryJob`, 60s sweep)
- **Events Produced:** InventoryReservedEvent, InventoryReleasedEvent, StockLowEvent (no consumer yet), BackorderCreatedEvent
- **Events Consumed:** None (driven by REST calls from Orders)

---

### 5. **Payment Service** (`payment-service`)
- **Port:** 8084
- **Purpose:** Collects payments through a hosted-checkout payment provider
- **Responsibilities:**
  - Payment initiation (`POST /payments`, 202): opens a checkout session with the provider and returns its `checkoutUrl`; the payment stays PENDING until the customer pays there
  - Receiving the provider's outcome via a signed webhook (`POST /payments/webhooks/gateway`, HMAC-SHA256, no JWT)
  - Transaction status tracking (with `failureReason` for FAILED payments)
  - Refund processing (COMPLETED payments only), executed through the provider
- **Database:** PostgreSQL (payment_db)
- **Key Features:**
  - Card details never reach OrderSphere - customers pay on the provider's page
  - `PaymentGatewayClient` abstraction; `DummyPaymentGatewayClient` talks to the dummy gateway (swap for a real provider)
  - `PaymentProcessingJob` (~5s sweep) reconciles PENDING payments with the provider (covers lost webhooks and expired checkouts) and settles refunds
  - Idempotent initiation per orderId; webhook and sweep serialized by a row lock
- **Not implemented yet:** real payment provider / PCI handling, saved cards
- **Events Produced:** PaymentInitiatedEvent, PaymentCompletedEvent, PaymentFailedEvent, RefundIssuedEvent
- **Events Consumed:** None (driven by REST calls from Orders)

---

### 5a. **Dummy Payment Gateway** (`dummy-payment-gateway`)
- **Port:** 8087 (not registered in Eureka, not behind the API gateway - it stands in for an external provider)
- **Purpose:** Development stand-in for a hosted-checkout card provider (Stripe Checkout-style)
- **Provides:**
  - Merchant API (`/api/checkout-sessions`, `/api/refunds`), authenticated with `Authorization: Bearer <GATEWAY_API_KEY>`
  - Hosted checkout page (`/checkout/{sessionId}`) with Pay and Cancel; redirects back to the Web UI (`?payment=success|cancelled&orderId=N`)
  - Signed webhooks (`X-Dummy-Gateway-Signature: sha256=<HMAC>`) to payment-service
- **Test cards:** `4242 4242 4242 4242` succeeds; `4000 0000 0000 0002` is declined (the customer can retry on the same page); any name, a future `MM/YY` expiry, any 3-4 digit CVC
- **Sessions:** kept in memory (lost on restart → payment-service fails those payments); expire after `GATEWAY_SESSION_TTL` (10 min, below inventory's 15-min hold)

---

### 6. **Shipping Service** (`shipping-service`)
- **Port:** 8085
- **Purpose:** Shipment creation, tracking, and logistics management
- **Responsibilities:**
  - Shipment creation for confirmed orders (ADMIN only - called by the orders saga with its service token)
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
  - Admin-sent notifications (`POST /notifications`, ADMIN only)
- **Database:** PostgreSQL (notification_db)
- **Key Features:**
  - Template-based message composition (`NotificationTemplateRenderer`)
  - Async delivery via `NotificationDeliveryJob` (~5s sweep, PENDING → SENT), retried up to 3 times
  - Customers only see their own notifications
- **Events Produced:** NotificationSentEvent, NotificationFailedEvent
- **Events Consumed:** OrderConfirmedEvent, OrderCancelledEvent, PaymentCompletedEvent, PaymentFailedEvent, ShipmentCreatedEvent, ShipmentPickedEvent, ShipmentInTransitEvent, DeliveryConfirmedEvent

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
- JWT token utilities
- Authentication filters
- RBAC annotations and interceptors
- Security configuration templates

#### `common-events`
- Event definitions (POJOs)
- Event serialization/deserialization
- Base event classes
- Event publisher utilities

---

## 📊 Data & Communication Flow

### Orchestration + Events
The order saga is **orchestrated** by the Orders service over synchronous, load-balanced REST (resolved through Eureka, wrapped in Resilience4j circuit breakers). Domain events are published to RabbitMQ for everything that doesn't need to block the request.

- **Broker:** RabbitMQ, topic exchange `ordersphere.events` (routing keys like `payment.completed`), dead-letter exchange `ordersphere.events.dlx`
- **Queues:** `notification-service.events`, `ordersphere-orders.payment-events`, `shipping-service.order-events` - each with a `.dlq`
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

## 🗄️ Database Services

### PostgreSQL
- **Host:** `postgres` (containerized service)
- **Port:** 5432
- **Databases:** One per service (auth_db, orders_db, inventory_db, payment_db, shipping_db, notification_db), created by `docker/postgres-init`
- **Migrations:** Flyway, per service (`src/main/resources/db/migration`)

---

## 🚀 Deployment Architecture

### Docker Compose (Development - the only deployment in the repo today)
- `docker-compose.yml` - PostgreSQL, RabbitMQ (management UI on `:15672`, `ordersphere`/`ordersphere`), Eureka, the gateway, all six services and the dummy payment gateway (`:8087`)
- Each service has its own `Dockerfile` under `services/<name>/`; rebuild one with `docker compose up -d --build <service>`

### Kubernetes (Planned)
- No Kubernetes manifests are checked in yet. Target: one Deployment per service, PostgreSQL as a StatefulSet with persistent volumes, credentials in Secrets.
- Note: `/actuator/health` currently requires a JWT, so it can't be used as-is for liveness/readiness probes.

---

## 🔐 Security Features

- **JWT Authentication:** Token-based stateless auth, one shared signing secret validated by `common-security` in every service
- **Role-Based Access Control (RBAC):** `@PreAuthorize` role checks per endpoint
- **Resource ownership:** customers only see their own orders, payments, shipments and notifications (404 otherwise); ADMIN sees all
- **Payments:** card details are entered only on the payment provider's hosted page; the provider's webhook is authenticated by HMAC signature and its merchant API by secret key
- **Password Security:** Bcrypt hashing with salt
- **Token Expiration:** 1-hour JWT expiration (no refresh or revocation yet)
- **Service-to-Service Auth:** the orders saga mints its own ADMIN-role JWT with the shared secret (`ServiceTokenProvider`) and forwards the customer's token where acting on their behalf - a proper service identity is still TBD
- **Audit Trail (Planned):** Blockchain-based immutable audit logs via Hyperledger Fabric

---

## 📝 API Documentation

### Gateway API Endpoints
- **Base URL:** `http://localhost:8080/{service-id}` (e.g. `http://localhost:8080/ordersphere-orders/orders`)

### Key Service Endpoints
- **Auth Service:** `/auth/register`, `/auth/login`, `/auth/me`, `/auth/admin/users/{id}/role`
- **Orders Service:** `/orders` (create, list), `/orders/{id}`, `/orders/{id}/cancel`
- **Inventory Service:** `/inventory/products` (create, list, get, restock), `/inventory/reservations` (reserve, confirm, release - used by the saga)
- **Payment Service:** `/payments` (initiate, status, refund), `/payments/webhooks/gateway` (provider webhook)
- **Dummy Payment Gateway (:8087, direct):** `/checkout/{sessionId}` (hosted page), `/api/checkout-sessions`, `/api/refunds`
- **Shipping Service:** `/shipments` (create - ADMIN), `/shipments/{id}`, `/shipments/{id}/tracking`, `/shipments/order/{orderId}`, `/shipments/{id}/return`
- **Notifications:** `/notifications` (list, get; create - ADMIN), `/notification-preferences` (set, list, delete)

*(No OpenAPI/Swagger docs yet - see the Postman collections in `postman/` for runnable examples)*

---

## 🧪 Testing & Quality Assurance

- **Unit Tests:** Per-service JUnit 5 + Mockito suites
- **Integration Tests:** `@SpringBootTest` + MockMvc against real PostgreSQL and RabbitMQ via Testcontainers (Docker required)
- **Formatting:** Spotless (google-java-format) runs `check` in the `verify` phase - fix with `mvn spotless:apply`
- **End-to-End Tests:** Postman collections run against the docker-compose stack with newman:
  - `postman/full-order-flow.postman_collection.json` - happy path, payment decline, cancellation with refund, negative checks
  - `postman/feature-coverage.postman_collection.json` - auth/RBAC, inventory, payments, returns, notifications, Eureka, RabbitMQ, circuit breakers
  - `postman/api-reference.postman_collection.json` - one example request per endpoint (no assertions)
- **CI:** GitHub Actions (`.github/workflows/ci.yml`) runs `mvn verify` and the Web UI build on pushes and PRs to `master`/`develop`
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
- Inventory can't reserve (e.g. unknown SKU) or returns no price → order CANCELLED immediately, nothing charged
- Payment initiation fails → inventory released → CANCELLED
- Card declined → shown on the payment page; the customer can retry, the order keeps waiting
- Customer cancels on the payment page, or the checkout expires (10 min) → payment FAILED → inventory released → CANCELLED
- Customer cancels the order but then pays on the still-open page → the late payment is refunded automatically
- Reservation confirm fails: Inventory unavailable → order stays AWAITING_PAYMENT and the next saga sweep retries; Inventory rejects it (reservation already expired/released) → payment refunded → CANCELLED
- Customer cancels → inventory released; payment refunded if already CONFIRMED; undelivered shipment cancelled; 409 once delivered
- Shipment creation failure → logged; the order is still marked CONFIRMED with no shipment and is **not** retried (known gap)

---

## 📚 Additional Components

### Web UI (`web-ui/` - in progress, customer screens done)
- **Technology Stack:** React 18 + Vite (no router, no server of its own); see `web-ui/README.md`
- **Done:** login/register (JWT kept in `localStorage`, 401 → back to login), order list with totals, product catalog with prices and stock, order placement with redirect to the hosted payment page and back (with a "Pay now" link for unpaid orders)
- **Not yet:** shipment tracking, notifications and preferences, order cancellation, admin/vendor screens (products, restock, roles)
- **Integration:** REST calls to the API Gateway (`VITE_GATEWAY_URL`, default `http://localhost:8080`); dev server on `http://localhost:5173`
- **Deployment:** not decided (static hosting behind a CDN is the likely fit)

### Mobile App (Future)
- Native iOS/Android applications
- Same backend API consumption
- Offline support and sync

### Analytics & Reporting Service (Future)
- Real-time dashboards
- Read-optimized views (CQRS pattern)
- Business intelligence integration

### Ledger Service (Future)
- Hyperledger Fabric integration for audit trails
- Immutable blockchain-based event logs
- Multi-organizational compliance

---

## 🛠️ Development Setup

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (also needed by the Testcontainers integration tests)
- Node.js 20+ (Web UI, newman)

### Local Development
```bash
# Build and run all tests + the Spotless format check (what CI runs)
mvn verify

# Fix formatting before committing
mvn spotless:apply

# Start the full stack
docker compose up -d

# Services will be available at:
# API Gateway: http://localhost:8080
# Service Registry: http://localhost:8761
# RabbitMQ management: http://localhost:15672 (ordersphere / ordersphere)
# Dummy payment gateway (hosted checkout): http://localhost:8087
# PostgreSQL: localhost:5432

# Rebuild one service after a change
docker compose up -d --build ordersphere-orders

# End-to-end tests against the running stack
npx newman run postman/full-order-flow.postman_collection.json
npx newman run postman/feature-coverage.postman_collection.json

# Web UI
cd web-ui && npm install && npm run dev
```

### Build Individual Service
```bash
# -am also builds common-events/common-security; without it a stale copy in ~/.m2 can be used
mvn verify -pl services/shipping-service -am
```

---

## 📖 Project Structure

```
ordersphere/
├── services/
│   ├── auth-service/                 # Authentication & Authorization
│   ├── ordersphere-orders/           # Order management & saga orchestration
│   ├── inventory-service/            # Stock & inventory management
│   ├── payment-service/              # Payment processing
│   ├── shipping-service/             # Shipment & logistics
│   ├── notification-service/         # Event-driven notifications
│   ├── dummy-payment-gateway/        # Stand-in hosted-checkout payment provider (dev/test)
│   ├── ordersphere-gateway/          # API Gateway
│   ├── service-registry/             # Eureka Service Registry
│   ├── common-events/                # Shared event definitions
│   └── common-security/              # Shared security utilities
├── web-ui/                           # React + Vite customer Web UI
├── postman/                          # Postman collections (flow, feature coverage, API reference)
├── docs/                             # product-functionality.md, technical-architecture.md
├── docker/postgres-init/             # Creates the per-service databases
├── .github/workflows/ci.yml          # CI: mvn verify + Web UI build
├── pom.xml                           # Parent Maven configuration (incl. Spotless)
├── docker-compose.yml                # Local development environment
├── README.md                         # Project overview
└── CLAUDE.md                         # This file
```

---

## 🔗 Key Technologies

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Framework** | Spring Boot 3.2.1 | Microservice framework |
| **Service Discovery** | Netflix Eureka | Service registry |
| **Database** | PostgreSQL | Primary data store |
| **API Gateway** | Spring Cloud Gateway | Request routing |
| **Authentication** | JWT + Spring Security | Auth & authorization |
| **Events** | RabbitMQ | Async messaging |
| **Resilience** | Resilience4j | Circuit breakers on the saga's outbound calls |
| **Migrations** | Flyway | Per-service schema management |
| **Web UI** | React + Vite | Customer front end |
| **Deployment** | Docker Compose (Kubernetes planned) | Containerization & orchestration |
| **Audit Trail** | Hyperledger Fabric (planned) | Blockchain-based audit logs |
| **Build Tool** | Maven | Dependency & build management |
| **CI** | GitHub Actions | Build, test and format check on push/PR |

---

## 📞 Service Communication Map

```
External Clients (browser / Web UI)
       ↓                                        ↘ redirect to pay
  API Gateway (8080)                      Dummy Payment Gateway (8087)
   ↙ ↓ ↙ ↓ ↙ ↓ ↙ ↓                         ↑ merchant API   ↓ signed webhook
Auth  Orders  Inventory  Payment  Shipping  Notification
(8081) (8082)  (8083)    (8084)   (8085)    (8086)
   ↓    ↓       ↓        ↓        ↓          ↓
PostgreSQL Database (5432)
   ↓
Service Registry (Eureka) - 8761
```

---

## 🎯 Next Steps & Roadmap

- [x] Implement message broker (RabbitMQ)
- [x] Implement circuit breakers (Resilience4j)
- [x] CI pipeline (GitHub Actions)
- [ ] Web UI - customer screens done; shipment tracking, notifications, cancellation and admin/vendor screens remaining
- [ ] Fix: retry shipment creation for CONFIRMED orders left without a shipment
- [ ] Security hardening: token refresh/revocation, gateway rate limiting, unauthenticated `/actuator/health`, PATCH in gateway CORS, real service-to-service identity; later OAuth2/OIDC
- [ ] Consume `StockLowEvent` (e.g. notify vendors)
- [ ] Payment reconciliation
- [ ] Add comprehensive API documentation (OpenAPI/Swagger)
- [ ] Add distributed tracing (Jaeger/Zipkin)
- [ ] Kubernetes manifests
- [ ] Add Hyperledger Fabric Ledger Service
- [ ] Implement CQRS for analytics/reporting
- [ ] Add comprehensive logging (ELK stack)
- [ ] Performance optimization and caching (Redis)

---

## 📄 License

OrderSphere is developed as an enterprise order management platform.

---

**Last Updated:** September 2026  
**Maintainer:** Sankar  
**Repository:** sankar1609/OrderSphere

