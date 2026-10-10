import { expect, test } from "@playwright/test";
import { newUser, signIn, unique } from "./helpers.js";

test("a vendor adds a product, restocks it, and can't reuse its SKU", async ({ page, request }) => {
  await signIn(page, await newUser(request, "VENDOR"));
  const sku = unique("E2E").toUpperCase();

  await page.goto("/orders");
  await page
    .getByRole("navigation", { name: "Main" })
    .getByRole("link", { name: "Manage Products" })
    .click();
  await expect(page).toHaveURL(/\/manage-products$/);

  async function createProduct() {
    await page.getByLabel("SKU").fill(sku);
    await page.getByLabel("Name").fill("E2E vendor widget");
    await page.getByLabel("Unit price").fill("7.50");
    await page.getByLabel("Starting stock").fill("3");
    await page.getByLabel("Reorder threshold").fill("1");
    await page.getByRole("button", { name: "Create product" }).click();
  }

  await createProduct();
  await expect(page.getByRole("status").filter({ hasText: `Created ${sku}` })).toBeVisible();
  const row = page.getByRole("row", { name: new RegExp(sku) });
  await expect(row).toBeVisible();

  await page.getByLabel(`Restock quantity for ${sku}`).fill("5");
  await row.getByRole("button", { name: "Restock" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: `Added 5 to ${sku} — 8 now available.` })
  ).toBeVisible();

  await createProduct();
  await expect(page.getByRole("status").filter({ hasText: /already exists/i })).toBeVisible();
});
