# OrderSphere - Agent Architecture & Service Guide

## 📋 Project Overview

**OrderSphere** is a cloud-native, microservices-based order and inventory management platform designed for enterprise environments where trust, auditability, and clear service boundaries are critical. The system supports reliable order processing, inventory control, and transparent auditability across multiple trusted organizations.

**Version:** 1.0.0-SNAPSHOT  
**Tech Stack:** Java 17 | Spring Boot 3.2.1 | Spring Cloud | PostgreSQL | Kubernetes | Docker

---

## 🏗️ System Architecture

### Architectural Principles

- **Microservices Pattern**: Each service is independently deployable with its own database
- **Event-Driven Communication**: Services communicate primarily via asynchronous events
- **Saga Pattern**: Distributed transactions use saga-based compensation for consistency
- **Blockchain for Audit**: Immutable audit trails using Hyperledger Fabric (trust layer, not transaction layer)
- **Cloud-Native**: Containerized services, Kubernetes-ready deployment
- **Service Discovery**: Eureka-based service registry for dynamic service location

---

## 🤖 Core Agents (Microservices)

### 1. **API Gateway** (`ordersphere-gateway`)
- **Port:** 8080
- **Purpose:** Single entry point for all external client requests
- **Responsibilities:**
  - Request routing to appropriate microservices
  - Cross-cutting concerns (logging, metrics)
  - Rate limiting and basic request validation
  - Load balancing across services
- **Technology:** Spring Cloud Gateway
- **Interacts With:** All downstream services

---

### 2. **Auth Service** (`auth-service`)
- **Port:** 8081
- **Purpose:** Centralized authentication and authorization
- **Responsibilities:**
  - User registration and login
  - JWT token generation and validation
  - Role-based access control (RBAC)
  - Token refresh and revocation
  - Password hashing with bcrypt
- **Database:** PostgreSQL (auth_db)
- **Key Features:**
  - JWT token expiration: 1 hour
  - Support for multiple user roles (CUSTOMER, VENDOR, ADMIN, AUDITOR)
  - Session management with Eureka registration
- **Events Produced:** UserRegisteredEvent, UserAuthenticatedEvent
- **Events Consumed:** None

---

### 3. **Orders Service** (`ordersphere-orders`)
- **Port:** 8082
- **Purpose:** Order lifecycle management and saga orchestration
- **Responsibilities:**
  - Order placement and creation
  - Order status tracking (PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED)
  - Saga orchestration for distributed order processing
  - Order history and audit trail
  - Customer order queries
- **Database:** PostgreSQL (orders_db)
- **Key Features:**
  - Implements Order Saga pattern
  - Coordinates across Inventory, Payment, and Shipping services
  - Compensation logic for order cancellation
- **Events Produced:** OrderCreatedEvent, OrderConfirmedEvent, OrderShippedEvent, OrderCancelledEvent
- **Events Consumed:** PaymentCompletedEvent, InventoryReservedEvent, ShipmentCreatedEvent

---

### 4. **Inventory Service** (`inventory-service`)
- **Port:** 8083
- **Purpose:** Stock reservation, release, and inventory tracking
- **Responsibilities:**
  - Product catalog management
  - Stock level tracking
  - Inventory reservation for orders
  - Inventory release on order cancellation
  - Stock updates from suppliers
  - Backorder management
- **Database:** PostgreSQL (inventory_db)
- **Key Features:**
  - Real-time stock availability checks
  - Distributed transaction support (saga compensation)
  - Inventory hold management with expiration
- **Events Produced:** InventoryReservedEvent, InventoryReleasedEvent, StockLowEvent, BackorderCreatedEvent
- **Events Consumed:** OrderCreatedEvent, OrderCancelledEvent

---

### 5. **Payment Service** (`payment-service`)
- **Port:** 8084
- **Purpose:** Asynchronous payment processing
- **Responsibilities:**
  - Payment authorization and processing
  - Transaction status tracking
  - Payment method management
  - Refund processing
  - Payment reconciliation
  - PCI compliance handling (abstracted)
- **Database:** PostgreSQL (payment_db)
- **Key Features:**
  - Async payment processing (eventual consistency)
  - Multiple payment gateway integration (stub)
  - Transaction idempotency
  - Failure recovery and retry logic
- **Events Produced:** PaymentInitiatedEvent, PaymentCompletedEvent, PaymentFailedEvent, RefundIssuedEvent
- **Events Consumed:** OrderCreatedEvent, OrderCancelledEvent

---

### 6. **Shipping Service** (`shipping-service`)
- **Port:** 8085
- **Purpose:** Shipment creation, tracking, and logistics management
- **Responsibilities:**
  - Shipment creation from confirmed orders
  - Carrier integration and label generation
  - Real-time tracking updates
  - Delivery confirmation
  - Return shipment handling
  - Logistics partner coordination
- **Database:** PostgreSQL (shipping_db)
- **Key Features:**
  - Carrier APIs integration (stub)
  - Automatic tracking notifications
  - Multiple shipment destination support
- **Events Produced:** ShipmentCreatedEvent, ShipmentPickedEvent, ShipmentInTransitEvent, DeliveryConfirmedEvent
- **Events Consumed:** OrderConfirmedEvent, OrderCancelledEvent

---

### 7. **Notification Service** (`notification-service`)
- **Port:** 8086
- **Purpose:** Event-driven customer and organizational notifications
- **Responsibilities:**
  - Order status notifications (email, SMS, in-app)
  - Payment confirmations
  - Shipment tracking alerts
  - Account notifications
  - Notification preference management
  - Multi-channel delivery (email, SMS, push)
- **Database:** PostgreSQL (notification_db)
- **Key Features:**
  - Event-driven triggered notifications
  - Delivery retry logic
  - Template-based message composition
  - Notification audit trail
- **Events Produced:** NotificationSentEvent, NotificationFailedEvent
- **Events Consumed:** All major events (OrderCreatedEvent, PaymentCompletedEvent, ShipmentCreatedEvent, etc.)

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

### Event-Driven Communication
Services communicate via asynchronous events using a message broker (RabbitMQ/Kafka pattern):

```
Order Placement Flow:
1. Client → API Gateway → Orders Service (create order)
2. Orders Service → Emit OrderCreatedEvent
3. Inventory Service → Consume OrderCreatedEvent → Reserve Stock → Emit InventoryReservedEvent
4. Payment Service → Consume OrderCreatedEvent → Process Payment → Emit PaymentCompletedEvent
5. Shipping Service → Consume PaymentCompletedEvent → Create Shipment → Emit ShipmentCreatedEvent
6. Notification Service → Consume all events → Send notifications
7. Orders Service → Consume InventoryReservedEvent + PaymentCompletedEvent → Update status
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
- **Databases:** One per service (auth_db, orders_db, inventory_db, payment_db, shipping_db, notification_db)
- **Deployment:** Kubernetes StatefulSet with persistent volumes

---

## 🚀 Deployment Architecture

### Kubernetes Deployment
- **Container Registry:** Docker images for each service
- **Orchestration:** Kubernetes (K8s)
- **Configuration:** K8s manifests + environment variables
- **Networking:** K8s Services for internal/external exposure

### Kubernetes Resources
- `auth-service.yaml` - Auth service deployment
- `postgres-deployment.yaml` - PostgreSQL StatefulSet
- `postgres-service.yaml` - PostgreSQL Service
- `postgres-secret.yaml` - Database credentials

### Docker Compose (Development)
- `docker-compose.yml` - Local development environment
- Containerizes all services and PostgreSQL for quick setup

---

## 🔐 Security Features

- **JWT Authentication:** Token-based stateless auth
- **Role-Based Access Control (RBAC):** Multiple user roles with permission levels
- **Password Security:** Bcrypt hashing with salt
- **Token Expiration:** 1-hour JWT expiration with refresh mechanism
- **Service-to-Service Auth:** Inter-service communication security (TBD)
- **Audit Trail:** Blockchain-based immutable audit logs via Hyperledger Fabric

---

## 📝 API Documentation

### Gateway API Endpoints
- **Base URL:** `http://localhost:8080`

### Key Service Endpoints
- **Auth Service:** `/auth/*` (login, register, refresh)
- **Orders Service:** `/orders/*` (create, list, track)
- **Inventory Service:** `/inventory/*` (products, stock)
- **Payment Service:** `/payments/*` (process, status)
- **Shipping Service:** `/shipments/*` (create, track)
- **Notifications:** `/notifications/*` (preferences, history)

*(Detailed API specs available in OpenAPI/Swagger documentation)*

---

## 🧪 Testing & Quality Assurance

- **Unit Tests:** Per-service test suites
- **Integration Tests:** Service-to-service event communication tests
- **Contract Tests:** API contract verification
- **Load Testing:** Performance validation under concurrent load
- **End-to-End Tests:** Complete order flow scenarios

---

## 🔄 Saga Orchestration & Failure Handling

### Order Saga Pattern
The system uses the Saga pattern to maintain consistency across distributed services:

**Happy Path:**
1. Order Service creates order → PENDING
2. Inventory Service reserves stock → Order → CONFIRMED
3. Payment Service processes payment → Payment confirmed
4. Shipping Service creates shipment → Order → SHIPPED

**Failure & Compensation:**
- Payment fails → Inventory compensation (release stock)
- Inventory unavailable → Order cancellation, payment reversal
- Shipping fails → Payment refund, inventory release

---

## 📚 Additional Components (Planned)

### Web UI (Upcoming)
- **Technology Stack:** React/Vue.js (TBD)
- **Responsibilities:**
  - Customer dashboard for order tracking
  - Inventory search and browsing
  - Order placement UI
  - Payment processing flows
  - Account management
  - Admin dashboard
- **Integration:** REST API calls to API Gateway
- **Authentication:** JWT token-based with Auth Service
- **Deployment:** CDN + static hosting or Node.js reverse proxy

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
- Docker & Docker Compose
- Kubernetes (optional, for K8s deployment)

### Local Development
```bash
# Build all services
mvn clean package

# Start services with Docker Compose
docker-compose up

# Services will be available at:
# API Gateway: http://localhost:8080
# Service Registry: http://localhost:8761
# PostgreSQL: localhost:5432
```

### Build Individual Service
```bash
cd services/auth-service
mvn clean package
mvn spring-boot:run
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
│   ├── ordersphere-gateway/          # API Gateway
│   ├── service-registry/             # Eureka Service Registry
│   ├── common-events/                # Shared event definitions
│   └── common-security/              # Shared security utilities
├── docs/
│   └── architecture/                 # Architecture documentation
├── k8sSteps/                         # Kubernetes deployment guides
├── pom.xml                           # Parent Maven configuration
├── docker-compose.yml                # Local development environment
├── README.md                         # Project overview
└── AGENT.md                          # This file
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
| **Events** | RabbitMQ/Kafka (abstracted) | Async messaging |
| **Deployment** | Docker + Kubernetes | Containerization & orchestration |
| **Audit Trail** | Hyperledger Fabric | Blockchain-based audit logs |
| **Build Tool** | Maven | Dependency & build management |

---

## 📞 Service Communication Map

```
External Clients
       ↓
  API Gateway (8080)
   ↙ ↓ ↙ ↓ ↙ ↓ ↙ ↓
Auth  Orders  Inventory  Payment  Shipping  Notification
(8081) (8082)  (8083)    (8084)   (8085)    (8086)
   ↓    ↓       ↓        ↓        ↓          ↓
PostgreSQL Database (5432)
   ↓
Service Registry (Eureka) - 8761
```

---

## 🎯 Next Steps & Roadmap

- [ ] Implement React/Vue.js Web UI
- [ ] Add comprehensive API documentation (OpenAPI/Swagger)
- [ ] Implement message broker (RabbitMQ/Kafka)
- [ ] Add Hyperledger Fabric Ledger Service
- [ ] Implement CQRS for analytics/reporting
- [ ] Add distributed tracing (Jaeger/Zipkin)
- [ ] Implement circuit breakers (Resilience4j)
- [ ] Add comprehensive logging (ELK stack)
- [ ] Performance optimization and caching (Redis)
- [ ] Security hardening and OAuth2/OIDC integration

---

## 📄 License

OrderSphere is developed as an enterprise order management platform.

---

**Last Updated:** July 2026  
**Maintainer:** Sankar  
**Repository:** sankar1609/OrderSphere

