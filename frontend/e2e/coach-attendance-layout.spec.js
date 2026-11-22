import { Buffer } from "node:buffer";
import { expect, test } from "@playwright/test";
import { createRolePage, hasRoleState, roleSetupCommand } from "./support/roleAuth.js";
const viewports = [
  { width: 1366, height: 768 },
  { width: 1440, height: 900 },
  { width: 1920, height: 1080 },
];

async function classOptionBySessionCount(classSelector, predicate) {
  const options = await classSelector.locator("option").evaluateAll((nodes) => nodes.map((node) => {
    const match = node.textContent.match(/(\d+)\s*buổi\s*$/i);
    return { value: node.value, sessionCount: match ? Number(match[1]) : 0 };
  }));
  return options.find((option) => option.value && predicate(option.sessionCount));
}

async function checkNoHorizontalOverflow(page) {
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth + 1);
}

async function checkSessionCardsFit(page) {
  const panel = await page.locator(".coach-attendance-session-list-view").boundingBox();
  const cards = await page.locator(".coach-attendance-session").evaluateAll((nodes) => nodes.map((node) => {
    const rect = node.getBoundingClientRect();
    return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
  }));
  expect(panel).not.toBeNull();
  for (const [index, card] of cards.entries()) {
    expect(card.left).toBeGreaterThanOrEqual(panel.x - 1.5);
    expect(card.right).toBeLessThanOrEqual(panel.x + panel.width + 1.5);
    if (index > 0) expect(card.top).toBeGreaterThanOrEqual(cards[index - 1].bottom - 1.5);
  }
}

async function sessionSignature(sessionButton) {
  return {
    title: await sessionButton.locator(".coach-attendance-session-title").innerText(),
    time: await sessionButton.locator(".coach-attendance-session-time").innerText(),
    status: await sessionButton.locator(".coach-attendance-badge").innerText(),
    availability: await sessionButton.locator(".coach-attendance-availability").innerText(),
  };
}

function sanitizeDiagnostic(value) {
  return String(value || "")
    .replace(/\bBearer\s+[^\s"'`]+/gi, "Bearer [REDACTED]")
    .replace(/\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b/g, "[REDACTED_JWT]")
    .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "[REDACTED_EMAIL]");
}

for (const viewport of viewports) {
  test(`Coach Attendance list/detail workflow preserves context at ${viewport.width}px`, async ({ browser }, testInfo) => {
    test.skip(!(await hasRoleState("COACH")), `No local Coach session. Create one with: ${roleSetupCommand("COACH")}`);

    const { context, page } = await createRolePage(browser, "COACH");
    await page.setViewportSize(viewport);
    const diagnostics = { consoleErrors: [], pageErrors: [], failedApiRequests: [], failedApiResponses: [] };
    page.on("console", (message) => {
      if (message.type() === "error") diagnostics.consoleErrors.push(sanitizeDiagnostic(message.text()));
    });
    page.on("pageerror", (error) => diagnostics.pageErrors.push(sanitizeDiagnostic(error.message)));
    page.on("requestfailed", (request) => {
      const url = new URL(request.url());
      if (url.pathname.includes("/api/")) diagnostics.failedApiRequests.push({ path: url.pathname, error: request.failure()?.errorText || "request failed" });
    });
    page.on("response", (response) => {
      const url = new URL(response.url());
      if (url.pathname.includes("/api/") && response.status() >= 400) {
        diagnostics.failedApiResponses.push({ path: url.pathname, status: response.status() });
      }
    });

    try {
      await page.goto("/coach/attendance", { waitUntil: "domcontentloaded" });
      await expect(page).toHaveURL(/\/coach\/attendance$/);

      const classSelector = page.getByLabel("Chọn lớp");
      await expect(classSelector).toBeVisible();
      await expect(classSelector).toBeEnabled();
      const smallClass = await classOptionBySessionCount(classSelector, (count) => count === 2);
      const longClass = await classOptionBySessionCount(classSelector, (count) => count >= 10);
      test.skip(!smallClass, "The current Coach API data has no class with exactly 2 sessions.");
      test.skip(!longClass, "The current Coach API data has no class with at least 10 sessions.");

      await classSelector.selectOption(smallClass.value);
      const sessions = page.locator(".coach-attendance-session");
      await expect(sessions).toHaveCount(2);
      await expect(page.getByRole("navigation", { name: "Phân trang buổi học" })).toHaveCount(0);
      await checkNoHorizontalOverflow(page);
      await checkSessionCardsFit(page);
      await page.screenshot({ path: testInfo.outputPath(`coach-attendance-list-${viewport.width}.png`) });

      const firstSession = sessions.first();
      await firstSession.focus();
      await expect(firstSession).toBeFocused();
      const firstSessionDetails = await sessionSignature(firstSession);
      await page.keyboard.press("Enter");
      const detail = page.locator(".coach-attendance-detail-view");
      await expect(detail).toBeVisible();
      await expect(page.getByRole("heading", { name: "Danh sách điểm danh" })).toBeFocused();
      await expect(page.getByRole("button", { name: "Quay lại danh sách" })).toBeVisible();
      await expect(classSelector).toHaveCount(0);
      const detailBounds = await detail.boundingBox();
      const pageBounds = await page.locator(".coach-attendance-page").boundingBox();
      expect(detailBounds).not.toBeNull();
      expect(pageBounds).not.toBeNull();
      expect(detailBounds.width).toBeGreaterThan(pageBounds.width - 60);
      await checkNoHorizontalOverflow(page);
      await expect(detail.locator(".coach-attendance-session-meta")).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath(`coach-attendance-detail-${viewport.width}.png`) });
      await page.screenshot({ path: testInfo.outputPath(`coach-attendance-detail-full-${viewport.width}.png`), fullPage: true });
      const rosterRows = detail.locator(".coach-attendance-table tbody tr");
      if (await rosterRows.count()) {
        await expect(detail.locator(".coach-attendance-search")).toBeVisible();
        await expect(detail.getByRole("button", { name: "Xuất CSV" })).toBeVisible();
        await expect(detail.locator(".coach-attendance-table select").first()).toBeVisible();
        await expect(detail.locator(".coach-attendance-table textarea").first()).toBeVisible();
        const historyControl = detail.locator(".coach-attendance-table .coach-attendance-history-button").first();
        if (await historyControl.count()) await expect(historyControl).toBeVisible();
      }

      await page.getByRole("button", { name: "Quay lại danh sách" }).click();
      await expect(classSelector).toHaveValue(smallClass.value);
      await expect(sessions).toHaveCount(2);
      const selectedSmallSession = page.locator(".coach-attendance-session.is-selected");
      expect(await sessionSignature(selectedSmallSession)).toEqual(firstSessionDetails);
      await expect(selectedSmallSession).toBeFocused();

      await classSelector.selectOption(longClass.value);
      const pagination = page.getByRole("navigation", { name: "Phân trang buổi học" });
      await expect(pagination).toBeVisible();
      await expect(pagination).toContainText("Trang 1/");
      await expect(sessions).toHaveCount(8);
      const firstPageText = await sessions.allInnerTexts();

      await pagination.getByRole("button", { name: "Sau" }).click();
      await expect(pagination).toContainText("Trang 2/");
      await expect(sessions).toHaveCount(Math.min(8, longClass.sessionCount - 8));
      const secondPageText = await sessions.allInnerTexts();
      expect(secondPageText).not.toEqual(firstPageText);
      await checkNoHorizontalOverflow(page);
      await checkSessionCardsFit(page);
      await page.screenshot({ path: testInfo.outputPath(`coach-attendance-list-page-2-${viewport.width}.png`) });

      const pageTwoSession = sessions.first();
      const pageTwoSessionDetails = await sessionSignature(pageTwoSession);
      await pageTwoSession.click();
      await expect(detail).toBeVisible();
      await expect(page.getByRole("button", { name: "Quay lại danh sách" })).toBeVisible();
      await page.getByRole("button", { name: "Quay lại danh sách" }).click();

      await expect(classSelector).toHaveValue(longClass.value);
      await expect(pagination).toContainText("Trang 2/");
      await expect(sessions).toHaveCount(Math.min(8, longClass.sessionCount - 8));
      const selectedPageTwoSession = page.locator(".coach-attendance-session.is-selected");
      expect(await sessionSignature(selectedPageTwoSession)).toEqual(pageTwoSessionDetails);
      await expect(selectedPageTwoSession).toBeFocused();
      await checkNoHorizontalOverflow(page);
    } finally {
      console.log(`[Coach Attendance ${viewport.width}] browser diagnostics: ${JSON.stringify(diagnostics)}`);
      await testInfo.attach("browser-diagnostics", {
        body: Buffer.from(JSON.stringify(diagnostics, null, 2)),
        contentType: "application/json",
      });
      await context.close();
    }
  });
}
