#!/usr/bin/env bash
# Measures reading the order list (My Orders) for customers with a long order history: runs
# loadtest/orders.js once against the running docker-compose stack and prints its summary.
#
#   LABEL=orders-before loadtest/orders.sh     # name the result (loadtest/results/<LABEL>.json)
#   HISTORY=500 CUSTOMERS=5 VUS=50 loadtest/orders.sh
#   THINK=0 VUS=100 loadtest/orders.sh         # no pauses: how much the service can take
#
# Setup places HISTORY x CUSTOMERS orders through the whole saga (a few minutes). For the run only,
# the gateway's per-client rate limit is raised; it is put back on exit, whatever happens.
set -euo pipefail
cd "$(dirname "$0")/.."

# Keep the Mac awake for the run (see run.sh and README.md: a sleeping machine ruins the timings).
if command -v caffeinate >/dev/null 2>&1; then
  caffeinate -u -t 5
  caffeinate -d -i -s -w $$ &
fi

K6_IMAGE="${K6_IMAGE:-grafana/k6:1.8.2}"
NETWORK="${COMPOSE_NETWORK:-ordersphere_default}"
RESULTS="$PWD/loadtest/results"
LABEL="${LABEL:-orders}"
mkdir -p "$RESULTS"

wait_for_gateway() {
  local ok=0
  for _ in $(seq 1 90); do
    if [[ "$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://localhost:8080/ordersphere-orders/actuator/health/readiness)" == "200" ]]; then
      ok=$((ok + 1))
      [[ $ok -ge 10 ]] && return 0
    else
      ok=0
    fi
    sleep 2
  done
  echo "ordersphere-orders did not become reachable through the gateway" >&2
  return 1
}

restore() {
  echo
  echo "Restoring the default gateway rate limit..."
  docker compose up -d --no-deps ordersphere-gateway >/dev/null 2>&1 || true
  wait_for_gateway || true
}
trap restore EXIT

echo "Raising the gateway rate limit for the test..."
GATEWAY_RATE_LIMIT_PER_SECOND=100000 GATEWAY_RATE_LIMIT_BURST=100000 \
  docker compose up -d --no-deps ordersphere-gateway >/dev/null
wait_for_gateway

rm -f "$RESULTS/$LABEL.json"
docker run --rm --name "ordersphere-k6-$LABEL" --network "$NETWORK" \
  -v "$PWD/loadtest:/scripts:ro" -v "$RESULTS:/results" \
  -e LABEL="$LABEL" -e VUS="${VUS:-50}" -e HOLD="${HOLD:-60s}" -e THINK="${THINK:-0.2}" \
  -e HISTORY="${HISTORY:-500}" -e CUSTOMERS="${CUSTOMERS:-5}" \
  "$K6_IMAGE" run --quiet /scripts/orders.js || echo "(k6 exited $?)"

if [[ ! -f "$RESULTS/$LABEL.json" ]]; then
  echo "The run produced no result (stalled or failed to start)." >&2
  exit 1
fi
cat "$RESULTS/$LABEL.json"
