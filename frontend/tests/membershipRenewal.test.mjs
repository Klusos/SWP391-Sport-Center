import test from "node:test";
import assert from "node:assert/strict";
import { setAutoRenewApi } from "../src/api.js";
import { activeMembershipChain, isAutoRenewalPayment } from "../src/membershipRenewal.js";

test("only backend-created membership renewals are labelled as automatic", () => {
  assert.equal(isAutoRenewalPayment({ purpose: "MEMBERSHIP", note: "AUTO_RENEWAL membership #7" }), true);
  assert.equal(isAutoRenewalPayment({ purpose: "MEMBERSHIP", note: "Cancelled by MEMBER" }), false);
  assert.equal(isAutoRenewalPayment({ purpose: "MEMBERSHIP", note: null }), false);
  assert.equal(isAutoRenewalPayment({ purpose: "UPGRADE", note: "AUTO_RENEWAL membership #7" }), false);
});

test("the membership chain keeps current and upcoming ACTIVE plans in start order", () => {
  const chain = activeMembershipChain([
    { id: 3, status: "ACTIVE", startDate: "2026-11-15", endDate: "2026-12-15" },
    { id: 1, status: "EXPIRED", startDate: "2026-09-15", endDate: "2026-10-15" },
    { id: 2, status: "ACTIVE", startDate: "2026-10-15", endDate: "2026-11-15" },
    { id: 4, status: "UPGRADED", startDate: "2026-10-15", endDate: "2026-11-15" },
  ], "2026-10-27");
  assert.deepEqual(chain.map((membership) => membership.id), [2, 3]);
});

test("the toggle calls the enable and cancel-renewal endpoints", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => { calls.push([options.method, url]); return new Response("{}", { status: 200 }); };
  try {
    await setAutoRenewApi("t", 9, true);
    await setAutoRenewApi("t", 9, false);
  } finally {
    globalThis.fetch = original;
  }
  assert.deepEqual(calls, [
    ["PATCH", "http://localhost:8080/api/memberships/9/enable-auto-renew"],
    ["PATCH", "http://localhost:8080/api/memberships/9/cancel-renewal"],
  ]);
});
