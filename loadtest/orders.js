// k6 load test: customers reading their order list (the Web UI's My Orders page) when each of them
// has a long order history. Run it with loadtest/orders.sh, or on its own:
//   docker run --rm --network ordersphere_default -v "$PWD/loadtest:/scripts" grafana/k6:1.8.2 run /scripts/orders.js
//
// Works against both API shapes: a plain array (every order, before pagination) and a page object
// ({items, page, size, total}), so the same script measures before and after.
import http from "k6/http";
import { check, sleep } from "k6";

const GATEWAY = __ENV.GATEWAY_URL || "http://ordersphere-gateway:8080";
const ORDERS = `${GATEWAY}/ordersphere-orders/orders`;
const VUS = Number(__ENV.VUS || 50);
const HOLD = __ENV.HOLD || "60s";
const LABEL = __ENV.LABEL || "orders";
// Orders each customer has; the list pages read the newest of them.
const HISTORY = Number(__ENV.HISTORY || 500);
// Customers with that history. Fewer than VUS: several users read the same customer's list.
const CUSTOMERS = Number(__ENV.CUSTOMERS || 5);
const THINK = Number(__ENV.THINK === undefined ? 0.2 : __ENV.THINK);
const RAMP_UP = 15;
const RAMP_DOWN = 5;
const READ_SECONDS = RAMP_UP + parseInt(HOLD, 10) + RAMP_DOWN;

export const options = {
  // Placing HISTORY x CUSTOMERS orders runs the whole saga each time; give setup room.
  setupTimeout: "30m",
  scenarios: {
    read: {
      executor: "ramping-vus",
      exec: "read",
      startVUs: 0,
      stages: [
        { duration: `${RAMP_UP}s`, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: `${RAMP_DOWN}s`, target: 0 },
      ],
      gracefulRampDown: "5s",
    },
  },
  thresholds: {
    "http_req_failed{scenario:read}": ["rate<0.01"],
    // No limits of their own: listed so the summary breaks them out.
    "http_req_duration{endpoint:list}": ["p(95)>=0"],
    "http_reqs{endpoint:list}": ["count>=0"],
    "data_received{scenario:read}": ["count>=0"],
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

const JSON_HEADERS = { "Content-Type": "application/json" };

function bearer(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

function login(username, password) {
  const response = http.post(
    `${GATEWAY}/auth-service/auth/login`,
    JSON.stringify({ username, password }),
    { headers: JSON_HEADERS }
  );
  if (response.status !== 200) {
    throw new Error(`login ${username}: ${response.status} ${response.body}`);
  }
  return response.json("token");
}

function registerAndLogin(username, role) {
  const registered = http.post(
    `${GATEWAY}/auth-service/auth/register`,
    JSON.stringify({ username, password: "password123", role }),
    { headers: JSON_HEADERS }
  );
  if (registered.status !== 201) {
    throw new Error(`register ${username}: ${registered.status} ${registered.body}`);
  }
  return login(username, "password123");
}

// The orders on one response, whichever shape it has.
function ordersOf(response) {
  const body = response.json();
  return Array.isArray(body) ? body : body.items;
}

export function setup() {
  const run = Date.now().toString(36);
  const vendorToken = registerAndLogin(`load_ovend_${run}`, "VENDOR");
  const sku = `LOADORD-${run}`.toUpperCase();
  const created = http.post(
    `${GATEWAY}/inventory-service/inventory/products`,
    JSON.stringify({
      sku,
      name: "Order history widget",
      quantityOnHand: 1000000,
      reorderThreshold: 0,
      unitPrice: 1.0,
    }),
    bearer(vendorToken)
  );
  if (created.status !== 201) {
    throw new Error(`create product: ${created.status} ${created.body}`);
  }

  // Each customer places HISTORY orders. They stay unpaid (the checkout expires and the saga
  // cancels them), which doesn't matter here: every one is a row with an item on My Orders.
  const tokens = [];
  for (let c = 0; c < CUSTOMERS; c++) {
    const token = registerAndLogin(`load_ocust_${run}_${c}`, "CUSTOMER");
    for (let i = 0; i < HISTORY; i++) {
      const placed = http.post(
        ORDERS,
        JSON.stringify({
          items: [{ sku, quantity: 1 }],
          currency: "USD",
          shippingDestination: "1 Load Test Way",
        }),
        { ...bearer(token), tags: { endpoint: "place" } }
      );
      if (placed.status !== 201) {
        throw new Error(`place order ${i} for customer ${c}: ${placed.status} ${placed.body}`);
      }
    }
    tokens.push(token);
  }

  // Placing them takes minutes and access tokens last 15: log in again for the measured part.
  const fresh = tokens.map((_, c) => login(`load_ocust_${run}_${c}`, "password123"));
  // Warm-up, not measured: JIT compilation, connection pools and the gateway's routes.
  for (let i = 0; i < 100; i++) {
    http.get(ORDERS, { ...bearer(fresh[i % fresh.length]), tags: { endpoint: "warmup" } });
  }
  const first = http.get(ORDERS, bearer(fresh[0]));
  return { tokens: fresh, ordersPerResponse: ordersOf(first).length, bytes: first.body.length };
}

export function read(data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const response = http.get(ORDERS, { ...bearer(token), tags: { endpoint: "list" } });
  check(response, { "list 200": (r) => r.status === 200 });
  if (THINK > 0) {
    sleep(Math.random() * THINK);
  }
}

function value(data, name, field) {
  return ((data.metrics[name] || {}).values || {})[field];
}

export function handleSummary(data) {
  const setupData = data.setup_data || {};
  const list = (field) => value(data, "http_req_duration{endpoint:list}", field);
  const summary = {
    label: LABEL,
    vus: VUS,
    think: THINK,
    history: HISTORY,
    customers: CUSTOMERS,
    ordersPerResponse: setupData.ordersPerResponse,
    responseBytes: setupData.bytes,
    requests: value(data, "http_reqs{endpoint:list}", "count"),
    perSecond: (value(data, "http_reqs{endpoint:list}", "count") || 0) / READ_SECONDS,
    med: list("med"),
    p95: list("p(95)"),
    p99: list("p(99)"),
    max: list("max"),
    errorRate: value(data, "http_req_failed{scenario:read}", "rate"),
    receivedMBPerSecond: (value(data, "data_received{scenario:read}", "count") || 0) / READ_SECONDS / 1e6,
    thresholdsFailed: Object.entries(data.metrics)
      .filter(([, m]) => m.thresholds && Object.values(m.thresholds).some((t) => !t.ok))
      .map(([name]) => name),
  };
  const line =
    `${LABEL}: ${summary.ordersPerResponse} orders (${summary.responseBytes} bytes) per response; ` +
    `${summary.requests} requests, ${Number(summary.perSecond).toFixed(0)}/s, ` +
    `median ${Number(summary.med).toFixed(1)} ms, p95 ${Number(summary.p95).toFixed(1)} ms, ` +
    `p99 ${Number(summary.p99).toFixed(1)} ms; errors ${(100 * Number(summary.errorRate || 0)).toFixed(2)}%\n`;
  return {
    stdout: line,
    [`/results/${LABEL}.json`]: JSON.stringify(summary, null, 2),
  };
}
