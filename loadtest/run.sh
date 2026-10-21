#!/usr/bin/env bash
# Measures the catalog cache: runs loadtest/catalog.js twice against the running docker-compose
# stack - catalog cache off, then on - and prints the two side by side.
#
#   loadtest/run.sh                    # 50 virtual users pausing up to 0.2s between requests
#   VUS=100 loadtest/run.sh
#   THINK=0 VUS=100 loadtest/run.sh    # no pauses: how much the service can take
#   DIRECT=1 THINK=0 VUS=100 loadtest/run.sh   # read inventory-service directly, not via the gateway
#
# For the run only, the gateway's per-client rate limit is raised (one load generator is one
# client, and the default 50 requests/second would throttle it) and inventory-service is restarted
# with the cache off/on. Both are put back to their defaults on exit, whatever happens.
set -euo pipefail
cd "$(dirname "$0")/.."

# A sleeping laptop freezes the run and ruins the timings (and the 15-minute access tokens expire
# while it sleeps). On macOS: wake it fully, then hold it awake while this script runs. An idle
# assertion alone is not enough - a Mac that is only awake for background maintenance goes back
# to sleep regardless.
if command -v caffeinate >/dev/null 2>&1; then
  caffeinate -u -t 5
  caffeinate -d -i -s -w $$ &
fi

K6_IMAGE="${K6_IMAGE:-grafana/k6:1.8.2}"
NETWORK="${COMPOSE_NETWORK:-ordersphere_default}"
RESULTS="$PWD/loadtest/results"
mkdir -p "$RESULTS"

wait_for_inventory() {
  # Recreated containers re-register with Eureka and the gateway refreshes its routes: wait until
  # the catalog answers through the gateway several times in a row.
  local ok=0
  for _ in $(seq 1 90); do
    if [[ "$(curl -s -m 3 -o /dev/null -w '%{http_code}' http://localhost:8080/inventory-service/actuator/health/readiness)" == "200" ]]; then
      ok=$((ok + 1))
      [[ $ok -ge 10 ]] && return 0
    else
      ok=0
    fi
    sleep 2
  done
  echo "inventory-service did not become reachable through the gateway" >&2
  return 1
}

restore() {
  echo
  echo "Restoring the default gateway rate limit and turning the catalog cache back on..."
  docker compose up -d --no-deps ordersphere-gateway inventory-service >/dev/null 2>&1 || true
  wait_for_inventory || true
}
trap restore EXIT

echo "Raising the gateway rate limit for the test..."
GATEWAY_RATE_LIMIT_PER_SECOND=100000 GATEWAY_RATE_LIMIT_BURST=100000 \
  docker compose up -d --no-deps ordersphere-gateway >/dev/null

for mode in off on; do
  enabled=$([[ "$mode" == "on" ]] && echo true || echo false)
  echo
  echo "== Catalog cache $mode =="
  GATEWAY_RATE_LIMIT_PER_SECOND=100000 GATEWAY_RATE_LIMIT_BURST=100000 CATALOG_CACHE_ENABLED=$enabled \
    docker compose up -d --no-deps inventory-service >/dev/null
  wait_for_inventory
  rm -f "$RESULTS/cache-$mode.json"
  # A run takes ~1.5 minutes. The watchdog removes the container if it is still there after 5 -
  # a stalled run (e.g. the machine went to sleep) must not hang the script.
  ( sleep 300; docker rm -f "ordersphere-k6-$mode" >/dev/null 2>&1 ) &
  watchdog=$!
  # Thresholds failing (k6 exit 99) must not stop the comparison; the table shows them.
  docker run --rm --name "ordersphere-k6-$mode" --network "$NETWORK" \
    -v "$PWD/loadtest:/scripts:ro" -v "$RESULTS:/results" \
    -e LABEL="cache-$mode" -e VUS="${VUS:-50}" -e HOLD="${HOLD:-60s}" -e THINK="${THINK:-0.2}" \
    ${DIRECT:+-e INVENTORY_URL=http://inventory-service:8083} \
    "$K6_IMAGE" run --quiet /scripts/catalog.js || echo "(k6 exited $? for cache-$mode)"
  kill "$watchdog" 2>/dev/null || true
  if [[ ! -f "$RESULTS/cache-$mode.json" ]]; then
    echo "The cache-$mode run produced no result (stalled or failed to start)." >&2
    exit 1
  fi
done

python3 - "$RESULTS" <<'PY'
import json, sys
off, on = (json.load(open(f"{sys.argv[1]}/cache-{m}.json")) for m in ("off", "on"))
def ms(v): return "-" if v is None else f"{v:.1f} ms"
def n(v): return "-" if v is None else f"{v:,.0f}"
def pct(v): return "-" if v is None else f"{100 * v:.2f}%"
rows = [
    ("Catalog list: requests/second", n(off["list"]["perSecond"]), n(on["list"]["perSecond"])),
    ("Catalog list: median", ms(off["list"]["med"]), ms(on["list"]["med"])),
    ("Catalog list: 95th percentile", ms(off["list"]["p95"]), ms(on["list"]["p95"])),
    ("Catalog list: 99th percentile", ms(off["list"]["p99"]), ms(on["list"]["p99"])),
    ("One product: median", ms(off["get"]["med"]), ms(on["get"]["med"])),
    ("One product: 95th percentile", ms(off["get"]["p95"]), ms(on["get"]["p95"])),
    ("All requests", n(off["allRequests"]), n(on["allRequests"])),
    ("Errors while browsing", pct(off["browseErrorRate"] or 0), pct(on["browseErrorRate"] or 0)),
    ("  by HTTP status", str(off.get("failuresByStatus") or "-"), str(on.get("failuresByStatus") or "-")),
    ("Fresh right after a restock", pct(off["freshAfterRestock"]), pct(on["freshAfterRestock"])),
]
think = "no pauses" if not off.get("think") else f"pausing up to {off['think']}s"
path = "inventory-service directly" if off.get("direct") else "through the gateway"
print(f"\n{'':34}{'cache off':>14}{'cache on':>14}   ({off['vus']} virtual users, {think}, {path})")
for label, a, b in rows:
    print(f"{label:34}{a:>14}{b:>14}")
for run in (off, on):
    if run["thresholdsFailed"]:
        print(f"!! {run['label']}: thresholds failed: {', '.join(run['thresholdsFailed'])}")
PY
