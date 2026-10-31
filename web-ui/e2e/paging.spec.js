import { expect, test } from "@playwright/test";
import {
  ADMIN,
  GATEWAY,
  loginApi,
  newProduct,
  newUser,
  placeOrderApi,
  signIn,
  unique,
} from "./helpers.js";

test("My Orders is paged, and the page is kept in the address", async ({ page, request }) => {
  const { sku } = await newProduct(request, { stock: 25 });
  const customer = await newUser(request, "CUSTOMER");
  const ids = [];
  for (let i = 0; i < 21; i++) {
    ids.push((await placeOrderApi(request, customer, sku)).id);
  }
  await signIn(page, customer);

  await page.goto("/orders");
  await expect(page.getByText("21 orders · page 1 of 2")).toBeVisible();
  // Newest first: the last order placed heads the first page, the first one is alone on page 2.
  await expect(page.getByRole("row", { name: new RegExp(`#${ids[20]}\\b`) })).toBeVisible();
  await expect(page.getByRole("row", { name: new RegExp(`#${ids[0]}\\b`) })).toHaveCount(0);

  await page.getByRole("button", { name: "Next" }).click();
  await expect(page).toHaveURL(/\/orders\?page=2$/);
  await expect(page.getByRole("row", { name: new RegExp(`#${ids[0]}\\b`) })).toBeVisible();
  await expect(page.getByRole("button", { name: "Next" })).toBeDisabled();

  await page.reload();
  await expect(page.getByText("21 orders · page 2 of 2")).toBeVisible();
  await page.getByRole("button", { name: "Previous" }).click();
  await expect(page).toHaveURL(/\/orders$/);
});

test("the admin's user list is paged and searches every page", async ({ page, request }) => {
  const prefix = unique("e2e_pager");
  const names = [];
  for (let i = 0; i < 51; i++) {
    names.push(`${prefix}-${String(i).padStart(2, "0")}`);
    await register(request, names[i]);
  }
  await signIn(page, { ...ADMIN, ...(await loginApi(request, ADMIN)) });

  // The last of them is on the search's second page, so only the server can find it.
  await page.goto("/admin/users");
  await page.getByLabel("Filter by username").fill(prefix);
  await page.getByRole("button", { name: "Search" }).click();
  await expect(page.getByText("51 users · page 1 of 2")).toBeVisible();
  await page.getByRole("button", { name: "Next" }).click();
  await expect(page).toHaveURL(new RegExp(`/admin/users\\?q=${prefix}&page=2$`));
  await expect(page.getByLabel(`Role for ${names[50]}`)).toBeVisible();

  await page.reload();
  await expect(page.getByText("51 users · page 2 of 2")).toBeVisible();
});

/** Registers a customer, waiting out the gateway's rate limit (burst 30, then 5 a second). */
async function register(request, username) {
  for (;;) {
    const response = await request.post(`${GATEWAY}/auth-service/auth/register`, {
      data: { username, password: "password123", role: "CUSTOMER" },
    });
    if (response.status() !== 429) {
      expect(response.ok()).toBeTruthy();
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}
