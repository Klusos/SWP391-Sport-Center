import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { getMembershipUpgradeOptionsApi } from "../src/api.js";
import { isMembershipPayment, upgradeBlockedMessage, upgradeErrorMessage, UPGRADE_BLOCKED_MESSAGES } from "../src/membershipUpgrade.js";

test("upgrade payments are grouped with membership payments", () => {
  assert.equal(isMembershipPayment("MEMBERSHIP"), true);
  assert.equal(isMembershipPayment("UPGRADE"), true);
  assert.equal(isMembershipPayment("PT_BOOKING"), false);
});

test("every backend blocked reason has a Vietnamese message", () => {
  for (const code of ["NO_ACTIVE_MEMBERSHIP", "MULTIPLE_ACTIVE_MEMBERSHIPS", "QUEUED_MEMBERSHIP", "PENDING_PAYMENT", "TOO_FEW_DAYS_LEFT"]) {
    assert.ok(UPGRADE_BLOCKED_MESSAGES[code], code);
    assert.equal(upgradeBlockedMessage(code), UPGRADE_BLOCKED_MESSAGES[code]);
  }
  assert.equal(upgradeBlockedMessage(null), "");
});

test("409 upgrade errors are translated from the backend reason", () => {
  assert.equal(upgradeErrorMessage({ status: 409, message: "Membership cannot be upgraded: PENDING_PAYMENT" }),
    UPGRADE_BLOCKED_MESSAGES.PENDING_PAYMENT);
  assert.match(upgradeErrorMessage({ status: 409, message: "Target plan must cost more per day and keep every benefit of the current plan" }), /không phải là gói cao hơn/);
  assert.match(upgradeErrorMessage({ status: 409, message: "This upgrade quote has expired; cancel the payment" }), /hết hạn/);
});

test("upgrade options are read from the member-scoped endpoint", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => { calls.push({ url, options }); return new Response("{}", { status: 200 }); };
  try {
    await getMembershipUpgradeOptionsApi("t", 12);
  } finally {
    globalThis.fetch = original;
  }
  assert.equal(calls[0].url, "http://localhost:8080/api/memberships/member/12/upgrade-options");
  assert.equal(calls[0].options.headers.Authorization, "Bearer t");
});

test("eligible upgrade cards expose one action that opens the selected plan confirmation", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  const card = source.slice(source.indexOf("upgrade.options.map"), source.indexOf("<section className=\"dash-card\">", source.indexOf("upgrade.options.map")));
  assert.equal((card.match(/onClick=\{\(\) => openUpgradeModal\(option\)\}/g) || []).length, 1);
  assert.match(card, /: "Nâng cấp"/);
  assert.doesNotMatch(card, /startUpgrade\(option, "(?:COUNTER|ONLINE)"\)/);
  assert.match(source, /setUpgradeModal\(\{[\s\S]*?option,[\s\S]*?currentPlanName: upgrade\.currentPlanName,[\s\S]*?remainingDays: upgrade\.remainingDays,[\s\S]*?currentEndDate: upgrade\.currentEndDate/);
});

test("upgrade dialog shows backend quote details and selects the existing COUNTER or ONLINE path", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /upgradeModal\.currentPlanName/);
  assert.match(source, /upgradeModal\.option\.planName/);
  assert.match(source, /money\(upgradeModal\.option\.amount\)/);
  assert.match(source, /upgradeModal\.remainingDays/);
  assert.match(source, /formatDate\(upgradeModal\.currentEndDate\)/);
  assert.match(source, /upgradeModal\.method === method\.value/);
  assert.match(source, /value: "COUNTER", label: "Thanh toán tại quầy \(COUNTER\)"/);
  assert.match(source, /value: "ONLINE", label: "VNPay Sandbox \(ONLINE\)"/);
  assert.match(source, /startUpgrade\(upgradeModal\.option, upgradeModal\.method\)/);
  assert.match(source, /purpose: "UPGRADE", referenceId: Number\(option\.planId\)/);
  assert.match(source, /if \(method === "ONLINE"\)[\s\S]*?validVnpayUrl\(payment\.paymentUrl\)[\s\S]*?window\.location\.assign\(payment\.paymentUrl\)/);
  assert.match(source, /setNotice\(`Đã tạo yêu cầu nâng cấp/);
});

test("upgrade dialog supports Escape, focus containment, cancel and duplicate-submit protection", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /event\.key === "Escape" && !upgradeBusyRef\.current/);
  assert.match(source, /event\.key !== "Tab"/);
  assert.match(source, /if \(previousFocus\?\.isConnected\) previousFocus\.focus\(\)/);
  assert.match(source, /onClick=\{closeUpgradeModal\}/);
  assert.match(source, /upgradeSubmitLock\.current \|\| busyPlanId !== null/);
  assert.match(source, /upgradeSubmitLock\.current = true/);
});

test("purchase and renewal controls remain on their existing handlers", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /onContinue=\{\(method\) => startPurchase\(paymentModalPlan, method\)\}/);
  assert.match(source, /onClick=\{\(\) => openPurchaseModal\(plan\)\}/);
  assert.match(source, /onClick=\{\(\) => void toggleAutoRenew\(membership\)\}/);
});
