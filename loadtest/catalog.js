// k6 load test: browsing the product catalog through the API gateway, while stock keeps changing.
// Run it with loadtest/run.sh (cache off, then cache on), or on its own:
//   docker run --rm --network ordersphere_default -v "$PWD/loadtest:/scripts" grafana/k6:1.8.2 run /scripts/catalog.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const GATEWAY = __ENV.GATEWAY_URL || "http://ordersphere-gateway:8080";
// Where the catalog is read from: through the gateway (what a browser does), or set INVENTORY_URL
// to http://inventory-service:8083 to measure the service on its own.
const INVENTORY = __ENV.INVENTORY_URL || `${GATEWAY}/inventory-service`;
const VUS = Number(__ENV.VUS || 50);
const HOLD = __ENV.HOLD || "60s";
const LABEL = __ENV.LABEL || "run";
const RAMP_UP = 15;
const RAMP_DOWN = 5;
// Seconds the browse scenario runs; requests/second is counted over this, not over setup too.
const BROWSE_SECONDS = RAMP_UP + parseInt(HOLD, 10) + RAMP_DOWN;
// Longest pause between a virtual user's requests, in seconds. 0 = no pause: each user sends its
// next request as soon as the last one is answered, which finds the service's capacity.
const THINK = Number(__ENV.THINK === undefined ? 0.2 : __ENV.THINK);

export const options = {
  scenarios: {
    // Customers browsing: mostly the whole catalog (what the Products and Place Order pages
    // load), sometimes one product.
    browse: {
      executor: "ramping-vus",
      exec: "browse",
      startVUs: 0,
      stages: [
        { duration: `${RAMP_UP}s`, target: VUS },
        { duration: HOLD, target: VUS },
        { duration: `${RAMP_DOWN}s`, target: 0 },
      ],
      gracefulRampDown: "5s",
    },
    // A vendor restocking every 2 seconds, so the cache is measured with invalidation happening -
    // and each restock checks that the catalog shows the new number straight away.
    restock: {
      executor: "constant-arrival-rate",
      exec: "restock",
      rate: 1,
      timeUnit: "2s",
      duration: `${RAMP_UP + parseInt(HOLD, 10)}s`,
      preAllocatedVUs: 2,
    },
  },
  thresholds: {
    "http_req_failed{scenario:browse}": ["rate<0.01"],
    "checks{check:fresh after restock}": ["rate>0.99"],
    // No limits of their own: listed so the summary breaks the timings out per endpoint.
    "http_req_duration{endpoint:list}": ["p(95)>=0"],
    "http_req_duration{endpoint:get}": ["p(95)>=0"],
    "http_reqs{endpoint:list}": ["count>=0"],
    "http_reqs{endpoint:get}": ["count>=0"],
    "browse_failures{status:0}": ["count>=0"],
    "browse_failures{status:401}": ["count>=0"],
    "browse_failures{status:429}": ["count>=0"],
    "browse_failures{status:500}": ["count>=0"],
    "browse_failures{status:503}": ["count>=0"],
    "browse_failures{status:504}": ["count>=0"],
  },
  summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
};

const JSON_HEADERS = { "Content-Type": "application/json" };

// What went wrong, when something does: failed browse requests by HTTP status (0 = no response).
const failures = new Counter("browse_failures");

function record(response) {
  if (response.status !== 200) {
    failures.add(1, { status: String(response.status) });
  }
}

function bearer(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

function registerAndLogin(username, role) {
  const credentials = { username, password: "password123" };
  const registered = http.post(
    `${GATEWAY}/auth-service/auth/register`,
    JSON.stringify({ ...credentials, role }),
    { headers: JSON_HEADERS }
  );
  if (registered.status !== 201) {
    throw new Error(`register ${username}: ${registered.status} ${registered.body}`);
  }
  const login = http.post(`${GATEWAY}/auth-service/auth/login`, JSON.stringify(credentials), {
    headers: JSON_HEADERS,
  });
  if (login.status !== 200) {
    throw new Error(`login ${username}: ${login.status} ${login.body}`);
  }
  return login.json("token");
}

// Once per run: a customer to browse, and a vendor with one product to keep restocking.
// (Access tokens last 15 minutes - longer than the test.)
export function setup() {
  const run = `${Date.now().toString(36)}`;
  const customerToken = registerAndLogin(`load_cust_${run}`, "CUSTOMER");
  const vendorToken = registerAndLogin(`load_vend_${run}`, "VENDOR");
  const sku = `LOAD-${run}`.toUpperCase();
  // inventory-service has just been restarted: the gateway's route to it can still flicker.
  let created;
  for (let attempt = 0; attempt < 30; attempt++) {
    created = http.post(
      `${INVENTORY}/inventory/products`,
      JSON.stringify({ sku, name: "Load test widget", quantityOnHand: 100, reorderThreshold: 0, unitPrice: 1.0 }),
      bearer(vendorToken)
    );
    if (created.status === 201 || created.status === 409) {
      break;
    }
    sleep(2);
  }
  if (created.status !== 201 && created.status !== 409) {
    throw new Error(`create product: ${created.status} ${created.body}`);
  }
  // Warm-up, not measured: JIT compilation, connection pools and the gateway's routes.
  for (let i = 0; i < 300; i++) {
    http.get(`${INVENTORY}/inventory/products`, {
      ...bearer(customerToken),
      tags: { endpoint: "warmup" },
    });
  }
  return { customerToken, vendorToken, sku };
}

export function browse(data) {
  const auth = bearer(data.customerToken);
  if (Math.random() < 0.8) {
    const response = http.get(`${INVENTORY}/inventory/products`, {
      ...auth,
      tags: { endpoint: "list" },
    });
    check(response, { "list 200": (r) => r.status === 200 && r.body.length > 2 });
    record(response);
  } else {
    const response = http.get(`${INVENTORY}/inventory/products/${data.sku}`, {
      ...auth,
      tags: { endpoint: "get" },
    });
    check(response, { "get 200": (r) => r.status === 200 });
    record(response);
  }
  if (THINK > 0) {
    sleep(Math.random() * THINK);
  }
}

export function restock(data) {
  const restocked = http.post(
    `${INVENTORY}/inventory/products/${data.sku}/restock`,
    JSON.stringify({ quantity: 1 }),
    { ...bearer(data.vendorToken), tags: { endpoint: "restock" } }
  );
  if (!check(restocked, { "restock 200": (r) => r.status === 200 })) {
    return;
  }
  const expected = restocked.json("availableQuantity");
  // The very next read must not be older than the restock that was just acknowledged.
  const read = http.get(`${INVENTORY}/inventory/products/${data.sku}`, {
    ...bearer(data.customerToken),
    tags: { endpoint: "get-after-restock" },
  });
  check(read, {
    "fresh after restock": (r) => r.status === 200 && r.json("availableQuantity") >= expected,
  });
}

function trend(data, name) {
  const values = (data.metrics[name] || {}).values || {};
  return {
    med: values.med,
    p95: values["p(95)"],
    p99: values["p(99)"],
    max: values.max,
  };
}

function value(data, name, field) {
  return ((data.metrics[name] || {}).values || {})[field];
}

// A small JSON per run for run.sh to compare, and one line on the console.
export function handleSummary(data) {
  const summary = {
    label: LABEL,
    vus: VUS,
    think: THINK,
    direct: Boolean(__ENV.INVENTORY_URL),
    list: {
      requests: value(data, "http_reqs{endpoint:list}", "count"),
      perSecond: (value(data, "http_reqs{endpoint:list}", "count") || 0) / BROWSE_SECONDS,
      ...trend(data, "http_req_duration{endpoint:list}"),
    },
    get: {
      requests: value(data, "http_reqs{endpoint:get}", "count"),
      perSecond: (value(data, "http_reqs{endpoint:get}", "count") || 0) / BROWSE_SECONDS,
      ...trend(data, "http_req_duration{endpoint:get}"),
    },
    allRequests: value(data, "http_reqs", "count"),
    allPerSecond: value(data, "http_reqs", "rate"),
    browseErrorRate: value(data, "http_req_failed{scenario:browse}", "rate"),
    failuresByStatus: Object.fromEntries(
      ["0", "401", "429", "500", "503", "504"]
        .map((status) => [status, value(data, `browse_failures{status:${status}}`, "count") || 0])
        .filter(([, count]) => count > 0)
    ),
    freshAfterRestock: value(data, "checks{check:fresh after restock}", "rate"),
    thresholdsFailed: Object.entries(data.metrics)
      .filter(([, m]) => m.thresholds && Object.values(m.thresholds).some((t) => !t.ok))
      .map(([name]) => name),
  };
  const line =
    `${LABEL}: list ${summary.list.requests} requests, ${Number(summary.list.perSecond).toFixed(0)}/s, ` +
    `median ${Number(summary.list.med).toFixed(1)} ms, p95 ${Number(summary.list.p95).toFixed(1)} ms; ` +
    `errors ${(100 * Number(summary.browseErrorRate || 0)).toFixed(2)}% ${JSON.stringify(summary.failuresByStatus)}; ` +
    `fresh after restock ${(100 * Number(summary.freshAfterRestock || 0)).toFixed(1)}%\n`;
  return {
    stdout: line,
    [`/results/${LABEL}.json`]: JSON.stringify(summary, null, 2),
  };
}
