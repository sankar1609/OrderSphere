import { expect, test } from "@playwright/test";
import {
  CARD_DECLINED,
  CARD_OK,
  fillCard,
  newProduct,
  newUser,
  orderIdFromStatus,
  placeOrderThroughUi,
  signIn,
} from "./helpers.js";

test.describe("ordering and paying", () => {
  test("order, pay on the hosted checkout, follow the delivery, request a return", async ({
    page,
    request,
  }) => {
    const { sku } = await newProduct(request);
    await signIn(page, await newUser(request, "CUSTOMER"));

    await placeOrderThroughUi(page, sku);
    await expect(page).toHaveURL(/localhost:8087\/checkout\//);
    await fillCard(page, CARD_OK);

    // Back in the app on My Orders, without the provider's query left in the address bar.
    await expect(page).toHaveURL(/localhost:5173\/orders$/);
    const orderId = await orderIdFromStatus(page, "is paid and confirmed");

    await page.getByRole("link", { name: `#${orderId}` }).click();
    await expect(page).toHaveURL(new RegExp(`/orders/${orderId}$`));
    await expect(page.getByRole("heading", { name: `Order #${orderId}` })).toBeVisible();
    await expect(page.getByText("Status: CONFIRMED")).toBeVisible();
    await expect(page).toHaveTitle(`Order #${orderId} - OrderSphere`);

    // The page refreshes itself while the shipment moves (one stage per ~5s sweep).
    const delivery = page.locator("div", { hasText: /^Delivery #/ }).last();
    await expect(delivery.getByText("DELIVERED", { exact: true }).first()).toBeVisible({
      timeout: 90_000,
    });

    // The order has its own address: a refresh comes back to it.
    await page.reload();
    await expect(page.getByRole("heading", { name: `Order #${orderId}` })).toBeVisible();

    await page.getByLabel("Request a return — reason").fill("Changed my mind");
    await page.getByRole("button", { name: "Request return" }).click();
    await expect(page.getByText(/^Return #/)).toBeVisible();

    await page.getByRole("link", { name: "← Back to My Orders" }).click();
    await expect(page).toHaveURL(/\/orders$/);
  });

  test("a declined card can be retried on the same checkout page", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    await signIn(page, await newUser(request, "CUSTOMER"));

    await placeOrderThroughUi(page, sku);
    await fillCard(page, CARD_DECLINED);
    await expect(page.getByText("Your card was declined")).toBeVisible();
    await expect(page).toHaveURL(/\/checkout\//);

    await fillCard(page, CARD_OK);
    await expect(page).toHaveURL(/localhost:5173\/orders$/);
    await orderIdFromStatus(page, "is paid and confirmed");
  });

  test("cancelling on the checkout page cancels the order", async ({ page, request }) => {
    const { sku } = await newProduct(request);
    await signIn(page, await newUser(request, "CUSTOMER"));

    await placeOrderThroughUi(page, sku);
    await page.getByRole("button", { name: "Cancel and return to store" }).click();

    await expect(page).toHaveURL(/localhost:5173\/orders$/);
    const orderId = await orderIdFromStatus(page, "was cancelled - no payment was taken");
    const row = page.getByRole("row", { name: new RegExp(`#${orderId}`) });
    await expect(row).toContainText("CANCELLED");
  });
});
