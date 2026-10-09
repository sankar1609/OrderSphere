import { expect, test } from "@playwright/test";
import { ADMIN, loginApi, newUser, signIn } from "./helpers.js";

test.describe("admin", () => {
  test.beforeEach(async ({ page, request }) => {
    await signIn(page, { ...ADMIN, ...(await loginApi(request, ADMIN)) });
  });

  test("every tab has its own address and survives a refresh", async ({ page }) => {
    await page.goto("/admin");
    await expect(page).toHaveURL(/\/admin\/users$/);

    const tabs = [
      ["Users & roles", "users", () => page.getByLabel("Filter by username")],
      ["Refunds & stock releases", "compensations", () => page.getByRole("combobox")],
      ["Unshipped orders", "unshipped", () => page.getByRole("button", { name: "Refresh" })],
      [
        "Payment reconciliation",
        "reconciliation",
        () => page.getByRole("button", { name: "Run reconciliation now" }),
      ],
    ];
    const nav = page.getByRole("navigation", { name: "Admin" });
    for (const [label, path, landmark] of tabs) {
      await nav.getByRole("link", { name: label }).click();
      await expect(page).toHaveURL(new RegExp(`/admin/${path}$`));
      await expect(landmark()).toBeVisible();

      await page.reload();
      await expect(page).toHaveURL(new RegExp(`/admin/${path}$`));
      await expect(landmark()).toBeVisible();
    }

    await page.goto("/admin/no-such-tab");
    await expect(page).toHaveURL(/\/admin\/users$/);
  });

  test("changes a user's role", async ({ page, request }) => {
    const user = await newUser(request, "CUSTOMER");

    await page.goto("/admin/users");
    await page.getByLabel("Filter by username").fill(user.username);
    await page.getByLabel(`Role for ${user.username}`).selectOption("VENDOR");
    await page.getByRole("button", { name: "Save" }).click();

    await expect(
      page.getByRole("status").filter({ hasText: `${user.username} is now VENDOR.` })
    ).toBeVisible();
  });

  test("runs a payment reconciliation", async ({ page }) => {
    await page.goto("/admin/reconciliation");
    await page.getByRole("button", { name: "Run reconciliation now" }).click();

    await expect(
      page.getByRole("status").filter({ hasText: /Checked \d+ provider transactions/ })
    ).toBeVisible({ timeout: 30_000 });
  });
});
