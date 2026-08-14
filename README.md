# OrderSphere

Cloud-native, microservices-based order and inventory management platform. See [CLAUDE.md](./CLAUDE.md) for full architecture details.

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose

## Running the Full Application

### 1. Build all services

```bash
mvn clean package -DskipTests
```

### 2. (Optional) Set a JWT secret

If unset, Docker Compose falls back to a dev-only default (`dev-only-secret-key-change-me-before-any-real-deployment`).

```bash
export JWT_SECRET=your-secret-here
```

### 3. Start everything

```bash
docker-compose up --build
```

This brings up, in dependency order:

| Service | Port | Notes |
|---|---|---|
| postgres | 5432 | Single Postgres instance; per-service DBs seeded via `docker/postgres-init` |
| service-registry | 8761 | Eureka; all other services depend on this |
| ordersphere-gateway | 8080 | Single entry point for external clients |
| auth-service | 8081 | |
| ordersphere-orders | 8082 | |
| inventory-service | 8083 | |
| payment-service | 8084 | |
| shipping-service | 8085 | |
| notification-service | 8086 | |

To run in the background:

```bash
docker-compose up --build -d
docker-compose logs -f <service>   # tail a specific service
```

### 4. Verify

- Eureka dashboard: http://localhost:8761 — confirm all services are registered
- Gateway entry point: http://localhost:8080

## Running a Single Service Locally

For dev/debugging outside Docker:

```bash
cd services/<service-name>
mvn spring-boot:run
```

The service still needs `postgres` and `service-registry` reachable (either the Docker Compose containers or local instances), with matching `DB_HOST` / `EUREKA_URI` environment variables.
