#!/usr/bin/env bash
# Builds the images and deploys the whole OrderSphere stack to Docker Desktop's Kubernetes.
# Usage: k8s/deploy.sh [--no-build]
set -euo pipefail
cd "$(dirname "$0")/.."

CONTEXT="${KUBE_CONTEXT:-docker-desktop}"
NAMESPACE=ordersphere

if [[ "${1:-}" != "--no-build" ]]; then
  # Same images (ordersphere/<service>:local) as docker-compose; Docker Desktop's Kubernetes
  # reads Docker's image store directly, so nothing needs to be pushed.
  docker compose build
fi

kubectl --context "$CONTEXT" apply -k k8s/overlays/docker-desktop

echo "Waiting for the stack to become ready (Spring Boot services take a minute or two)..."
kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout status statefulset/postgres --timeout=300s
for deployment in $(kubectl --context "$CONTEXT" -n "$NAMESPACE" get deployments -o name); do
  kubectl --context "$CONTEXT" -n "$NAMESPACE" rollout status "$deployment" --timeout=600s
done

# Ready pods aren't reachable yet: each service still has to register with Eureka and the gateway
# has to fetch the registry (up to ~1 minute). Until then the gateway answers 503.
echo "Waiting for the gateway to route to every service..."
for attempt in $(seq 1 60); do
  routed=0
  for service in auth-service ordersphere-orders inventory-service payment-service shipping-service notification-service audit-service; do
    code=$(curl -s -m 3 -o /dev/null -w '%{http_code}' "http://localhost:8080/$service/actuator/health/readiness" || true)
    if [[ "$code" == "200" ]]; then routed=$((routed + 1)); fi
  done
  if [[ "$routed" == "7" ]]; then break; fi
  sleep 5
done
if [[ "$routed" != "7" ]]; then
  echo "Only $routed of 7 services are reachable through the gateway on localhost:8080 - check 'kubectl -n $NAMESPACE get pods' and the gateway's logs." >&2
  exit 1
fi

kubectl --context "$CONTEXT" -n "$NAMESPACE" get pods
cat <<URLS

OrderSphere is up:
  API gateway           http://localhost:8080   (Swagger UI: /webjars/swagger-ui/index.html)
  Grafana (logs, metrics) http://localhost:3000
  Prometheus            http://localhost:9090
  Payment checkout      http://localhost:8087
  Eureka                http://localhost:8761
  Jaeger                http://localhost:16686
  RabbitMQ management   http://localhost:15672
Web UI: cd web-ui && npm run dev  ->  http://localhost:5173
URLS
