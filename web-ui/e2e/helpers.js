import { expect } from "@playwright/test";

export const GATEWAY = process.env.VITE_GATEWAY_URL ?? "http://localhost:8080";

// The development admin auth-service bootstraps (ADMIN_BOOTSTRAP_USERNAME / _PASSWORD).
export const ADMIN = {
  username: process.env.E2E_ADMIN_USERNAME ?? "admin",
  password: process.env.E2E_ADMIN_PASSWORD ?? "admin123",
};

export const CARD_OK = "4242 4242 4242 4242";
export const CARD_DECLINED = "4000 0000 0000 0002";

const PASSWORD = "password123";

/** A name that can't clash with another run's: every test brings its own users and products. */
export function unique(prefix) {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
}

async function api(request, method, path, { token, data } = {}) {
  const response = await request.fetch(`${GATEWAY}${path}`, {
    method,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    data,
  });
  if (!response.ok()) {
    throw new Error(`${method} ${path} -> ${response.status()} ${await response.text()}`);
  }
  return response.status() === 204 ? null : response.json();
}

export async function loginApi(request, user) {
  return api(request, "POST", "/auth-service/auth/login", {
    data: { username: user.username, password: user.password },
  });
}

/** Registers a fresh CUSTOMER or VENDOR and returns it with its tokens. */
export async function newUser(request, role) {
  const user = { username: unique(role === "VENDOR" ? "e2e_vend" : "e2e_cust"), password: PASSWORD };
  await api(request, "POST", "/auth-service/auth/register", { data: { ...user, role } });
  return { ...user, ...(await loginApi(request, user)) };
}

/** A new vendor with one product in stock; returns { vendor, sku }. */
export async function newProduct(request, { stock = 5, price = 4.0 } = {}) {
  const vendor = await newUser(request, "VENDOR");
  const sku = unique("E2E").toUpperCase();
  await api(request, "POST", "/inventory-service/inventory/products", {
    token: vendor.token,
    data: { sku, name: `E2E widget ${sku}`, quantityOnHand: stock, reorderThreshold: 0, unitPrice: price },
  });
  return { vendor, sku };
}

export async function placeOrderApi(request, customer, sku, quantity = 1) {
  return api(request, "POST", "/ordersphere-orders/orders", {
    token: customer.token,
    data: { items: [{ sku, quantity }], currency: "USD", shippingDestination: "1 E2E Street" },
  });
}

export async function getOrderApi(request, customer, orderId) {
  return api(request, "GET", `/ordersphere-orders/orders/${orderId}`, { token: customer.token });
}

/** Pays an order's checkout session directly at the provider, then waits for the saga to confirm. */
export async function payAndConfirmApi(request, customer, order) {
  await request.post(`${order.checkoutUrl}/pay`, {
    form: { name: "E2E Tester", cardNumber: CARD_OK, expiry: "12/30", cvc: "123" },
    maxRedirects: 0,
  });
  await expect
    .poll(async () => (await getOrderApi(request, customer, order.id)).status, { timeout: 40_000 })
    .toBe("CONFIRMED");
}

/**
 * Starts the page already logged in (tokens in localStorage, as the app keeps them) - for tests
 * that aren't about the login screen. Applies to the first page load only.
 */
export async function signIn(page, user) {
  await page.addInitScript(
    ([token, refreshToken]) => {
      if (!sessionStorage.getItem("e2e-signed-in")) {
        localStorage.setItem("token", token);
        localStorage.setItem("refreshToken", refreshToken);
        sessionStorage.setItem("e2e-signed-in", "1");
      }
    },
    [user.token, user.refreshToken]
  );
}

export async function logInThroughUi(page, user) {
  await page.getByLabel("Username").fill(user.username);
  await page.getByLabel("Password").fill(user.password);
  await page.getByRole("button", { name: "Log in" }).click();
}

/** On Place Order: one unit of the product, then off to the provider's checkout page. */
export async function placeOrderThroughUi(page, sku) {
  await page.goto("/place-order");
  await page.getByLabel("Product").selectOption(sku);
  await page.getByRole("button", { name: "Add item" }).click();
  await page.getByLabel("Shipping destination").fill("1 E2E Street");
  await page.getByRole("button", { name: "Place order and pay" }).click();
  await page.waitForURL(/\/checkout\//);
}

export async function fillCard(page, cardNumber) {
  await page.getByLabel("Name on card").fill("E2E Tester");
  await page.getByLabel("Card number").fill(cardNumber);
  await page.getByLabel("Expiry").fill("12/30");
  await page.getByLabel("CVC").fill("123");
  await page.getByRole("button", { name: /^Pay / }).click();
}

/** The order id from My Orders' payment-return message ("Order #42 is paid and confirmed."). */
export async function orderIdFromStatus(page, pattern) {
  const status = page.getByRole("status").filter({ hasText: pattern });
  await expect(status).toBeVisible({ timeout: 45_000 });
  return (await status.textContent()).match(/#(\d+)/)[1];
}
