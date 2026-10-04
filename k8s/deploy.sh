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

kubectl --context "$CONTEXT" -n "$NAMESPACE" get pods
cat <<URLS

OrderSphere is up:
  API gateway           http://localhost:8080   (Swagger UI: /webjars/swagger-ui/index.html)
  Payment checkout      http://localhost:8087
  Eureka                http://localhost:8761
  Jaeger                http://localhost:16686
  RabbitMQ management   http://localhost:15672
Web UI: cd web-ui && npm run dev  ->  http://localhost:5173
URLS
