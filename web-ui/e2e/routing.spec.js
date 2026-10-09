import { expect, test } from "@playwright/test";
import { ADMIN, logInThroughUi, newUser, signIn, unique } from "./helpers.js";

test.describe("routing and session", () => {
  test("a deep link while logged out goes to login, then on to the page asked for", async ({
    page,
    request,
  }) => {
    const customer = await newUser(request, "CUSTOMER");

    await page.goto("/products");
    await expect(page).toHaveURL(/\/login$/);

    await logInThroughUi(page, customer);
    await expect(page).toHaveURL(/\/products$/);
    await expect(page.getByRole("heading", { name: "Products" })).toBeVisible();
    await expect(page).toHaveTitle("Products - OrderSphere");
  });

  test("a deep link to an admin tab survives logging in", async ({ page }) => {
    await page.goto("/admin/reconciliation");
    await expect(page).toHaveURL(/\/login$/);

    await logInThroughUi(page, ADMIN);
    await expect(page).toHaveURL(/\/admin\/reconciliation$/);
    await expect(page.getByRole("button", { name: "Run reconciliation now" })).toBeVisible();
  });

  test("a refresh stays on the same page", async ({ page, request }) => {
    await signIn(page, await newUser(request, "CUSTOMER"));

    await page.goto("/notifications");
    await page.reload();

    await expect(page).toHaveURL(/\/notifications$/);
    await expect(page.getByRole("heading", { name: "Notifications" })).toBeVisible();
  });

  test("the browser's back and forward buttons move between pages", async ({ page, request }) => {
    await signIn(page, await newUser(request, "CUSTOMER"));
    await page.goto("/orders");
    const nav = page.getByRole("navigation", { name: "Main" });

    await nav.getByRole("link", { name: "Products" }).click();
    await expect(page).toHaveURL(/\/products$/);
    await nav.getByRole("link", { name: "Place Order" }).click();
    await expect(page).toHaveURL(/\/place-order$/);

    await page.goBack();
    await expect(page).toHaveURL(/\/products$/);
    await expect(page.getByRole("heading", { name: "Products" })).toBeVisible();
    await page.goForward();
    await expect(page).toHaveURL(/\/place-order$/);
    await expect(page.getByRole("heading", { name: "Place Order" })).toBeVisible();
  });

  test("an unknown address shows a not-found page with a way back", async ({ page, request }) => {
    await signIn(page, await newUser(request, "CUSTOMER"));

    await page.goto("/no-such-page");
    await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible();

    await page.getByRole("link", { name: "Go to My Orders" }).click();
    await expect(page).toHaveURL(/\/orders$/);
  });

  test("a customer can't open the admin or product-management pages", async ({ page, request }) => {
    await signIn(page, await newUser(request, "CUSTOMER"));

    await page.goto("/admin/users");
    await expect(page).toHaveURL(/\/orders$/);
    await page.goto("/manage-products");
    await expect(page).toHaveURL(/\/orders$/);

    const nav = page.getByRole("navigation", { name: "Main" });
    await expect(nav.getByRole("link", { name: "My Orders" })).toBeVisible();
    await expect(nav.getByRole("link", { name: "Admin" })).toHaveCount(0);
    await expect(nav.getByRole("link", { name: "Manage Products" })).toHaveCount(0);
  });

  test("logging out returns to login and closes the pages", async ({ page, request }) => {
    const customer = await newUser(request, "CUSTOMER");
    await page.goto("/login");
    await logInThroughUi(page, customer);
    await expect(page).toHaveURL(/\/orders$/);

    await page.getByRole("button", { name: "Log out" }).click();
    await expect(page).toHaveURL(/\/login$/);

    await page.goto("/orders");
    await expect(page).toHaveURL(/\/login$/);
    // Logging out isn't "was heading to /orders": the next login starts at My Orders, and a
    // logged-in visit to /login is sent on.
    await logInThroughUi(page, customer);
    await expect(page).toHaveURL(/\/orders$/);
    await page.goto("/login");
    await expect(page).toHaveURL(/\/orders$/);
  });

  test("registering logs you in and opens My Orders", async ({ page }) => {
    await page.goto("/login");
    await page.getByRole("link", { name: "Register" }).click();
    await expect(page).toHaveURL(/\/register$/);
    // The address changes just before the page does: wait for the form itself, so the username
    // isn't typed into the login form that's about to be replaced.
    await expect(page.getByRole("heading", { name: "Create an account" })).toBeVisible();

    await page.getByLabel("Username").fill(unique("e2e_reg"));
    await page.getByLabel("Password").fill("password123");
    await page.getByRole("button", { name: "Create account" }).click();

    await expect(page).toHaveURL(/\/orders$/);
    await expect(page.getByText("No orders yet.")).toBeVisible();
    await expect(page).toHaveTitle("My Orders - OrderSphere");
  });
});
