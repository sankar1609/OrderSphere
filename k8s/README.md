# OrderSphere on Kubernetes

Kustomize manifests for the whole stack: the eight Spring Boot services (API gateway, Eureka, auth,
orders, inventory, payment, shipping, notification), the dummy payment gateway, PostgreSQL,
RabbitMQ, Redis and Jaeger - all in the `ordersphere` namespace.

```
k8s/
├── base/                     # everything, environment-neutral
│   ├── apps/                 # one Deployment + ClusterIP Service per service
│   ├── infra/                # postgres (StatefulSet + PVC), rabbitmq, redis, jaeger
│   ├── config.env            # shared settings -> ConfigMap ordersphere-config
│   └── secrets.env           # DEV credentials -> Secret ordersphere-secrets
├── overlays/docker-desktop/  # publishes the gateway, checkout page and UIs on localhost
└── deploy.sh                 # build images + apply + wait until ready
```

Services keep their docker-compose names (`postgres`, `rabbitmq`, `auth-service`, ...), so the
same settings work in both. Service discovery still goes through Eureka - each pod registers its
own IP.

## Run on Docker Desktop

Prerequisites: Docker Desktop with Kubernetes enabled (Settings → Kubernetes), and about 6 GB of
memory for Docker. Stop the docker-compose stack first - both publish the same ports:

```bash
docker compose stop
k8s/deploy.sh                 # docker compose build + kubectl apply -k + wait (~3-5 min)
```

The images are the ones docker-compose builds (`ordersphere/<service>:local`); Docker Desktop's
Kubernetes uses Docker's image store, so nothing is pushed to a registry. After changing a
service: `docker compose build <service>` and
`kubectl -n ordersphere rollout restart deployment/<service>`.

| What | URL |
|------|-----|
| API gateway (and Swagger UI) | http://localhost:8080 |
| Hosted checkout (dummy payment gateway) | http://localhost:8087 |
| Eureka | http://localhost:8761 |
| Jaeger | http://localhost:16686 |
| RabbitMQ management (`ordersphere`/`ordersphere`) | http://localhost:15672 |

The Web UI and the Postman collections work unchanged (`cd web-ui && npm run dev`,
`npx newman run postman/full-order-flow.postman_collection.json`).

Useful commands:

```bash
kubectl -n ordersphere get pods
kubectl -n ordersphere logs deploy/ordersphere-orders -f
kubectl -n ordersphere describe pod <pod>          # probe failures, OOM kills, image problems
kubectl -n ordersphere exec -it postgres-0 -- psql -U ordersphere -d orders_db
```

Tear down (also deletes the PostgreSQL volume), then bring docker-compose back:

```bash
kubectl delete namespace ordersphere
docker compose start
```

## Troubleshooting (Docker Desktop)

- **`ErrImageNeverPull` / images vanish after `docker compose build`:** when Docker's disk passes
  ~85% the kubelet garbage-collects unused images (and evicts pods). Check with
  `docker run --rm alpine df -h /`; old build cache is usually the culprit -
  `docker buildx prune --filter until=72h -f` - then rebuild.
- **`postgres-0` Pending, PVC `data-postgres-0` Pending:** Docker Desktop's `storage-provisioner`
  pod (kube-system) isn't running - typically evicted during disk pressure, and nothing recreates
  it. **EXTERNAL-IP `<pending>`, nothing on localhost:8080:** the same for `vpnkit-controller`.
  Check `kubectl -n kube-system get pods`; *Settings → Kubernetes → Reset* or restarting Docker
  Desktop brings both back.
- **A service crash-loops with `For input string: "tcp://..."`:** a pod spec lost
  `enableServiceLinks: false` - Kubernetes then injects `REDIS_PORT=tcp://...`-style variables that
  clash with the services' own `*_PORT` settings.

## Before deploying anywhere real

- `base/secrets.env` holds the development credentials docker-compose also defaults to. Replace
  them - or create the `ordersphere-secrets` Secret another way (sealed secrets, an external
  secret store) - and set real `PAYMENT_GATEWAY_*` keys for a real payment provider.
- Push the images to a registry and set `images:` in an overlay; add an Ingress (with TLS) for the
  API gateway instead of the `LoadBalancer` Services of the docker-desktop overlay.
- `PAYMENT_RETURN_URL` and `GATEWAY_PUBLIC_URL` in `config.env` are browser-facing URLs - point them
  at the deployed Web UI and payment page.
- PostgreSQL, RabbitMQ and Redis run as single in-cluster instances; managed services (or
  operators) are the usual choice in production.
- `base/infra/postgres-init.sql` is a copy of `docker/postgres-init/01-init-databases.sql`
  (kustomize can't read outside `k8s/`); CI fails if they differ.
