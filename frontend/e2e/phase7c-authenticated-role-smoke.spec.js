import { expect, test } from "@playwright/test";
import { createRolePage } from "./support/roleAuth.js";

async function checkPages(browser, role, routes) {
  const { context, page } = await createRolePage(browser, role);
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.name));
  try {
    for (const route of routes) {
      await page.goto(route, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
      await expect(page.locator("#root")).toBeVisible();
      await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
    }
    expect(pageErrors).toEqual([]);
  } finally {
    await context.close();
  }
}

test("ADMIN can load dashboard, members, plans/upgrade paths, and courses", async ({ browser }) => {
  await checkPages(browser, "ADMIN", [
    "/staff/dashboard",
    "/staff/members",
    "/staff/membership-plans",
    "/staff/courses",
  ]);
});

test("RECEPTIONIST can load Membership Sales and Renewal controls", async ({ browser }) => {
  const { context, page } = await createRolePage(browser, "RECEPTIONIST");
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.name));
  try {
    await page.goto("/receptionist/membership-sales", { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
    const memberSelect = page.locator("main select").first();
    await expect(memberSelect).toBeVisible();
    await expect(memberSelect.locator("option").nth(1)).toBeAttached();
    await memberSelect.selectOption({ index: 1 });
    await expect(page.locator(".membership-sales-card[aria-label]").last()).toBeVisible();
    expect(pageErrors).toEqual([]);
  } finally {
    await context.close();
  }
});

test("MEMBER can load Membership Renewal and course registration navigation", async ({ browser }) => {
  const { context, page } = await createRolePage(browser, "MEMBER");
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.name));
  try {
    await page.goto("/member/membership", { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
    await expect(page.getByRole("heading", { name: /Membership/i }).first()).toBeVisible();
    await expect(page.locator(".member-renewal-section")).toBeVisible();
    await page.goto("/member/courses", { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
    await expect(page.locator("#root")).toBeVisible();
    expect(pageErrors).toEqual([]);
  } finally {
    await context.close();
  }
});

test("COACH can load dashboard, attendance, training results, and PT sessions", async ({ browser }) => {
  await checkPages(browser, "COACH", [
    "/coach/dashboard",
    "/coach/attendance",
    "/coach/training",
    "/coach/pt-sessions",
  ]);
});
