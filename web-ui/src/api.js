const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080";

/**
 * Thin fetch wrapper: builds the gateway URL, attaches the bearer token when
 * given, and turns a non-2xx response into a thrown Error carrying the
 * backend's own error message (see GlobalExceptionHandler's {message} shape).
 */
async function request(path, { method = "GET", token, body } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${GATEWAY_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const error = new Error(data?.message ?? `Request failed with status ${response.status}`);
    error.status = response.status;
    throw error;
  }

  return data;
}

export function login(username, password) {
  return request("/auth-service/auth/login", {
    method: "POST",
    body: { username, password },
  });
}

export function register(username, password) {
  return request("/auth-service/auth/register", {
    method: "POST",
    body: { username, password, role: "CUSTOMER" },
  });
}

export function listMyOrders(token) {
  return request("/ordersphere-orders/orders", { token });
}

export function getOrder(token, id) {
  return request(`/ordersphere-orders/orders/${id}`, { token });
}

export function listProducts(token) {
  return request("/inventory-service/inventory/products", { token });
}

export function createOrder(token, body) {
  return request("/ordersphere-orders/orders", { method: "POST", token, body });
}
