import test from "node:test";
import assert from "node:assert/strict";
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
