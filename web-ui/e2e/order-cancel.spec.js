import { expect, test } from "@playwright/test";
import { newProduct, newUser, payAndConfirmApi, placeOrderApi, signIn } from "./helpers.js";

test.describe("an order's page", () => {
  test("cancelling a paid order says a refund follows, then shows it cancelled", async ({
    page,
    request,
  }) => {
    const { sku } = await newProduct(request);
    const customer = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, customer, sku);
    await payAndConfirmApi(request, customer, order);
    await signIn(page, customer);

    await page.goto(`/orders/${order.id}`);
    await page.getByRole("button", { name: "Cancel order" }).click();
    await expect(page.getByText("Your payment will be refunded.")).toBeVisible();
    await page.getByRole("button", { name: "Confirm cancellation" }).click();

    await expect(page.getByText("Status: CANCELLED")).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel order" })).toHaveCount(0);
  });

  test("an unpaid order can be cancelled with nothing charged", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    const customer = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, customer, sku);
    await signIn(page, customer);

    await page.goto(`/orders/${order.id}`);
    await expect(page.getByRole("link", { name: "Pay now" })).toBeVisible();
    await page.getByRole("button", { name: "Cancel order" }).click();
    await expect(page.getByText("Nothing has been charged.")).toBeVisible();
    await page.getByRole("button", { name: "Confirm cancellation" }).click();

    await expect(page.getByText("Status: CANCELLED")).toBeVisible();
  });

  test("someone else's or a made-up order isn't shown", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    const owner = await newUser(request, "CUSTOMER");
    const order = await placeOrderApi(request, owner, sku);
    await signIn(page, await newUser(request, "CUSTOMER"));

    await page.goto(`/orders/${order.id}`);
    await expect(page.getByText(`No order found with id: ${order.id}`)).toBeVisible();
    await expect(page.getByRole("heading", { name: `Order #${order.id}` })).toHaveCount(0);

    await page.goto("/orders/not-a-number");
    await expect(page.getByText("Order not found.")).toBeVisible();
    await expect(page.getByRole("link", { name: "← Back to My Orders" })).toBeVisible();
  });
});
