import { expect, test } from "@playwright/test";
import {
  ADMIN,
  loginApi,
  newAuditor,
  newProduct,
  newUser,
  placeOrderApi,
  signIn,
} from "./helpers.js";

test.describe("audit log", () => {
  test("an auditor follows an order's trail, opens an entry and verifies the chain", async ({
    page,
    request,
  }) => {
    const { sku } = await newProduct(request);
    const customer = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, customer, sku);
    await signIn(page, await newAuditor(request));

    await page.goto("/orders");
    await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Audit" }).click();
    await expect(page).toHaveURL(/\/audit$/);
    await expect(page).toHaveTitle("Audit - OrderSphere");

    await page.getByLabel("Order id").fill(String(order.id));
    await page.getByRole("button", { name: "Filter" }).click();
    await expect(page).toHaveURL(new RegExp(`/audit\\?orderId=${order.id}$`));

    // The saga's three steps, each recorded by the service that took it. Events arrive
    // asynchronously, so reload until all three are there.
    const created = page.getByRole("row", { name: /OrderCreatedEvent/ });
    await expect(async () => {
      await page.reload();
      await expect(page.getByRole("row", { name: /PaymentInitiatedEvent/ })).toBeVisible({
        timeout: 2_000,
      });
    }).toPass({ timeout: 20_000 });
    await expect(created).toContainText("ordersphere-orders");
    await expect(created).toContainText(customer.username);
    const reserved = page.getByRole("row", { name: /InventoryReservedEvent/ });
    await expect(reserved).toContainText("inventory-service");
    await expect(reserved).toContainText("orders-service");

    await created.getByRole("button").click();
    const detail = page.getByRole("region", { name: /^Audit entry / });
    await expect(detail).toContainText(`"orderId": ${order.id}`);
    await expect(detail).toContainText(/hash [0-9a-f]{64}/);
    await expect(detail.getByRole("link", { name: "trace" })).toBeVisible();

    await page.getByRole("button", { name: "Verify chain" }).click();
    await expect(page.getByRole("status").filter({ hasText: /Chain intact: \d+ entries verified/ }))
        .toBeVisible();

    // A filtered view is an address: a refresh keeps it.
    await page.reload();
    await expect(page.getByLabel("Order id")).toHaveValue(String(order.id));
    await expect(page.getByRole("row", { name: /OrderCreatedEvent/ })).toBeVisible();
  });

  test("an admin reaches an order's trail from the order page", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    const customer = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, customer, sku);
    await signIn(page, { ...ADMIN, ...(await loginApi(request, ADMIN)) });

    await page.goto(`/orders/${order.id}`);
    await page.getByRole("link", { name: "View audit trail" }).click();

    await expect(page).toHaveURL(new RegExp(`/audit\\?orderId=${order.id}$`));
    await expect(page.getByRole("row", { name: /OrderCreatedEvent/ })).toBeVisible();
  });

  test("customers have no audit page and no audit link", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    const customer = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, customer, sku);
    await signIn(page, customer);

    await page.goto("/audit");
    await expect(page).toHaveURL(/\/orders$/);
    await expect(
      page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Audit" })
    ).toHaveCount(0);

    await page.goto(`/orders/${order.id}`);
    await expect(page.getByRole("heading", { name: `Order #${order.id}` })).toBeVisible();
    await expect(page.getByRole("link", { name: "View audit trail" })).toHaveCount(0);
  });
});
