const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL ?? "http://localhost:8080";

/**
 * Session renewal. Access tokens live 15 minutes; when an authenticated call comes back 401, the
 * refresh token is exchanged once for a new pair (POST /auth/refresh) and the call is retried with
 * the new access token. Refresh tokens are single-use and reusing one revokes the whole session,
 * so concurrent 401s share a single in-flight refresh instead of each spending the token.
 */
const auth = { refreshToken: null, onTokens: null, inFlight: null };

/** Called by App whenever its tokens change; onTokens receives each refreshed {token, refreshToken}. */
export function configureAuth({ refreshToken, onTokens }) {
  auth.refreshToken = refreshToken;
  auth.onTokens = onTokens;
}

function refreshSession() {
  if (!auth.refreshToken) {
    return Promise.resolve(null);
  }
  if (!auth.inFlight) {
    auth.inFlight = send("/auth-service/auth/refresh", {
      method: "POST",
      body: { refreshToken: auth.refreshToken },
    })
      .then((tokens) => {
        auth.refreshToken = tokens.refreshToken;
        auth.onTokens?.(tokens);
        return tokens.token;
      })
      .catch(() => null)
      .finally(() => {
        auth.inFlight = null;
      });
  }
  return auth.inFlight;
}

async function request(path, options = {}) {
  try {
    return await send(path, options);
  } catch (error) {
    if (error.status !== 401 || !options.token) {
      throw error;
    }
    const renewed = await refreshSession();
    if (!renewed) {
      throw error;
    }
    return send(path, { ...options, token: renewed });
  }
}

/**
 * Thin fetch wrapper: builds the gateway URL, attaches the bearer token when
 * given, and turns a non-2xx response into a thrown Error carrying the
 * backend's own error message (see GlobalExceptionHandler's {message} shape).
 */
async function send(path, { method = "GET", token, body } = {}) {
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

/** Ends this session server-side (best effort - the client forgets its tokens either way). */
export function logout(refreshToken) {
  return send("/auth-service/auth/logout", { method: "POST", body: { refreshToken } });
}

/** Self-registration allows CUSTOMER or VENDOR; other roles are granted by an admin. */
export function register(username, password, role = "CUSTOMER") {
  return request("/auth-service/auth/register", {
    method: "POST",
    body: { username, password, role },
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

/** ADMIN/VENDOR only. body: {sku, name, unitPrice, quantityOnHand, reorderThreshold}. */
export function createProduct(token, body) {
  return request("/inventory-service/inventory/products", { method: "POST", token, body });
}

/** ADMIN/VENDOR only. Adds quantity (at least 1) to the product's stock on hand. */
export function restockProduct(token, sku, quantity) {
  return request(`/inventory-service/inventory/products/${encodeURIComponent(sku)}/restock`, {
    method: "POST",
    token,
    body: { quantity },
  });
}

export function createOrder(token, body) {
  return request("/ordersphere-orders/orders", { method: "POST", token, body });
}
