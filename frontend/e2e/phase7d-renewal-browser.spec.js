import { createHmac } from "node:crypto";
import { readFile } from "node:fs/promises";
import process from "node:process";
import { expect, test } from "@playwright/test";
import { createRolePage } from "./support/roleAuth.js";

const fixturesPath = new URL("../.playwright/phase7d-fixtures.json", import.meta.url);
const fixtureManifest = JSON.parse(await readFile(fixturesPath, "utf8"));
const fixtures = Object.fromEntries(fixtureManifest.fixtures.map((item) => [item.label, item]));
const apiBase = process.env.PHASE7D_API_BASE_URL || "http://127.0.0.1:8081";
const testHashSecret = process.env.PHASE7D_TEST_HASH_SECRET;

function vietnamTimestamp() {
  const parts = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23",
  }).formatToParts(new Date()).map(({ type, value }) => [type, value]));
  return `${parts.year}${parts.month}${parts.day}${parts.hour}${parts.minute}${parts.second}`;
}

function simulatedVnpayCallback(checkoutUrl) {
  if (!testHashSecret) throw new Error("PHASE7D_TEST_HASH_SECRET must be set to the local simulator key");
  const checkout = new URL(checkoutUrl);
  const params = {
    vnp_Amount: checkout.searchParams.get("vnp_Amount"),
    vnp_BankCode: "NCB",
    vnp_CardType: "QRCODE",
    vnp_CurrCode: "VND",
    vnp_OrderInfo: "Phase7D-local-simulation",
    vnp_PayDate: vietnamTimestamp(),
    vnp_ResponseCode: "00",
    vnp_TmnCode: "P7DTEST",
    vnp_TransactionNo: `${Date.now()}${Math.floor(Math.random() * 1000).toString().padStart(3, "0")}`,
    vnp_TransactionStatus: "00",
    vnp_TxnRef: checkout.searchParams.get("vnp_TxnRef"),
  };
  const canonical = Object.entries(params).sort(([left], [right]) => left.localeCompare(right))
    .map(([key, value]) => `${key}=${encodeURIComponent(value).replace(/%20/g, "+")}`).join("&");
  params.vnp_SecureHash = createHmac("sha512", testHashSecret).update(canonical, "utf8").digest("hex");
  const query = new URLSearchParams(params).toString();
  return {
    returnUrl: `${apiBase}/api/payments/vnpay/return?${query}`,
    ipnUrl: `${apiBase}/api/payments/vnpay/ipn?${query}`,
  };
}

async function loginFixtureMember(browser, fixture) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/login");
  await page.locator("#loginEmail").fill(fixture.email);
  await page.locator("#loginPassword").fill(fixture.password);
  await page.locator('form button[type="submit"]').click();
  await expect(page).not.toHaveURL(/\/login(?:\?|$)/, { timeout: 10000 });
  return { context, page };
}

test("fixture Member completes ONLINE Renewal through simulated Return and IPN", async ({ browser }) => {
  const fixture = fixtures.online;
  const { context, page } = await loginFixtureMember(browser, fixture);
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.name));
  let callback;
  let checkoutUrl;
  let checkoutIntercepted = false;
  try {
    await page.route("https://sandbox.vnpayment.vn/**", async (route) => {
      checkoutIntercepted = true;
      checkoutUrl = route.request().url();
      await route.fulfill({
        status: 200,
        contentType: "text/html",
        body: '<!doctype html><html><body><h1>Simulated test gateway</h1><a id="return" href="#">'
          + 'Return to SportCenter</a></body></html>',
      });
    });
    await page.goto("/member/membership");
    const card = page.locator(".member-renewal-card").filter({ hasText: `Intent #${fixture.intentId}` });
    await expect(card).toBeVisible();
    await expect(card).toContainText("OPEN");
    await expect(card).toContainText("725.000");
    await card.locator("button").click();
    await expect(page.getByRole("heading", { name: "Simulated test gateway" })).toBeVisible();
    expect(checkoutIntercepted).toBe(true);

    // VNPay paidAt has whole-second precision; let the provider-side event occur after the
    // persisted microsecond Payment.createdAt, rather than synthesizing a pre-attempt time.
    await page.waitForTimeout(1100);
    callback = simulatedVnpayCallback(checkoutUrl);
    await page.locator("#return").evaluate((link, href) => { link.href = href; }, callback.returnUrl);
    await page.locator("#return").click();
    await expect(page).toHaveURL(/paymentStatus=PENDING/);
    await page.goto("/member/membership");
    let cardAfterReturn = page.locator(".member-renewal-card").filter({ hasText: `Intent #${fixture.intentId}` });
    await expect(cardAfterReturn.locator(".member-renewal-attempt")).toContainText("Payment #");
    const pendingState = await page.evaluate(async ({ apiBase, intentId }) => {
      const auth = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
      const response = await fetch(`${apiBase}/api/memberships/renewal-intents/${intentId}`, {
        headers: { Authorization: `Bearer ${auth?.accessToken || ""}` },
      });
      const intent = response.ok ? await response.json() : null;
      return { status: response.status, intentStatus: intent?.status, attemptStatus: intent?.attempts?.[0]?.status };
    }, { apiBase, intentId: fixture.intentId });
    expect(pendingState).toEqual({ status: 200, intentStatus: "OPEN", attemptStatus: "PENDING" });

    const ipn = await page.request.get(callback.ipnUrl);
    expect(ipn.status()).toBe(200);
    expect((await ipn.json()).RspCode).toBe("00");
    const duplicate = await page.request.get(callback.ipnUrl);
    expect(duplicate.status()).toBe(200);
    expect((await duplicate.json()).RspCode).toBe("02");
    const invalid = new URL(callback.ipnUrl);
    invalid.searchParams.set("vnp_SecureHash", "0".repeat(128));
    const rejected = await page.request.get(invalid.toString());
    expect((await rejected.json()).RspCode).toBe("97");

    await page.reload();
    cardAfterReturn = page.locator(".member-renewal-card").filter({ hasText: `Intent #${fixture.intentId}` });
    await expect(cardAfterReturn).toContainText("SETTLED");
    const result = await page.evaluate(async ({ apiBase, intentId }) => {
      const auth = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
      const response = await fetch(`${apiBase}/api/memberships/renewal-intents/${intentId}`, {
        headers: { Authorization: `Bearer ${auth?.accessToken || ""}` },
      });
      return { status: response.status, intent: response.ok ? await response.json() : null };
    }, { apiBase, intentId: fixture.intentId });
    expect(result.status).toBe(200);
    expect(result.intent.status).toBe("SETTLED");
    expect(result.intent.successorMembershipId).toBeTruthy();
    const negativeAccess = await page.evaluate(async ({ apiBase, ownIntentId, otherIntentId }) => {
      const auth = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
      const headers = { Authorization: `Bearer ${auth?.accessToken || ""}` };
      const other = await fetch(`${apiBase}/api/memberships/renewal-intents/${otherIntentId}`, { headers });
      const retry = await fetch(`${apiBase}/api/memberships/renewal-intents/${ownIntentId}/attempts`, {
        method: "POST", headers,
      });
      return { crossMemberStatus: other.status, settledRetryStatus: retry.status };
    }, { apiBase, ownIntentId: fixture.intentId, otherIntentId: fixtures.counter.intentId });
    expect(negativeAccess.crossMemberStatus).toBe(404);
    expect(negativeAccess.settledRetryStatus).toBe(409);
    expect(pageErrors).toEqual([]);
  } finally {
    await context.close();
  }
});

test("fixture Receptionist creates and confirms COUNTER Renewal once", async ({ browser }) => {
  const fixture = fixtures.counter;
  const { context, page } = await createRolePage(browser, "RECEPTIONIST");
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.name));
  try {
    await page.goto("/receptionist/membership-sales");
    const memberSelect = page.locator("main select").first();
    await memberSelect.selectOption(String(fixture.memberId));
    const renewalPanel = page.locator(".membership-sales-card[aria-label]").last();
    const intentRow = renewalPanel.locator(".membership-sales-history-row")
      .filter({ hasText: `Intent #${fixture.intentId}` });
    await expect(intentRow).toBeVisible();
    await expect(intentRow).toContainText("OPEN");
    await intentRow.locator("button").click();
    await expect(intentRow).toContainText("COUNTER");
    await expect(intentRow).toContainText("PENDING");
    page.once("dialog", (dialog) => dialog.accept());
    await intentRow.getByRole("button").click();
    await expect(intentRow).toContainText("SETTLED", { timeout: 10000 });
    await expect(intentRow).toContainText(/Membership.*#/);

    const result = await page.evaluate(async ({ apiBase, memberId, paymentId }) => {
      const auth = JSON.parse(localStorage.getItem("sportcenter.auth") || "null");
      const response = await fetch(`${apiBase}/api/payments/${paymentId}/counter`, {
        method: "PATCH",
        headers: { Authorization: `Bearer ${auth?.accessToken || ""}`, "Content-Type": "application/json" },
        body: JSON.stringify({ success: true, note: "Phase 7D idempotency replay" }),
      });
      const renewalResponse = await fetch(`${apiBase}/api/receptionist/members/${memberId}/renewal-intents`, {
        headers: { Authorization: `Bearer ${auth?.accessToken || ""}` },
      });
      return {
        status: response.status,
        payment: response.ok ? await response.json() : null,
        intents: renewalResponse.ok ? await renewalResponse.json() : null,
      };
    }, { apiBase, memberId: fixture.memberId,
      paymentId: Number((await intentRow.innerText()).match(/Payment #(\d+)/)?.[1]) });
    expect(result.status).toBe(200);
    expect(result.payment.paidAt).toBeTruthy();
    expect(Date.parse(result.payment.paidAt)).toBeGreaterThan(Date.now() - 60000);
    expect(Date.parse(result.payment.paidAt)).toBeLessThan(Date.now() + 5000);
    expect(result.intents.find((item) => item.intentId === fixture.intentId).status).toBe("SETTLED");
    expect(result.intents.find((item) => item.intentId === fixture.intentId).successorMembershipId).toBeTruthy();
    expect(pageErrors).toEqual([]);
  } finally {
    await context.close();
  }
});
