/**
 * The role claim of an access token (CUSTOMER, VENDOR, ADMIN, ...), or null if it can't be read.
 * Only used to decide what to show - every call is still authorized by the backend.
 */
export function roleOf(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)).role ?? null;
  } catch {
    return null;
  }
}

/** Roles that may create and restock products (see inventory-service's ProductController). */
export function canManageProducts(role) {
  return role === "ADMIN" || role === "VENDOR";
}

/** The access token's subject - the logged-in username - or null if it can't be read. */
export function usernameOf(token) {
  try {
    const payload = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(payload)).sub ?? null;
  } catch {
    return null;
  }
}

export function isAdmin(role) {
  return role === "ADMIN";
}
