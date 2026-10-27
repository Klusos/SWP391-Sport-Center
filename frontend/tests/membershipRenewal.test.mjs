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

test("members may cancel only the last membership and only before it starts; admins may also cancel a running one", async () => {
  const { cancellableMembershipId } = await import("../src/membershipRenewal.js");
  const running = { id: 2, startDate: "2026-10-15", endDate: "2026-11-15" };
  const upcoming = { id: 3, startDate: "2026-11-15", endDate: "2026-12-15" };
  assert.equal(cancellableMembershipId([running, upcoming], "2026-10-27"), 3);
  assert.equal(cancellableMembershipId([running], "2026-10-27"), null);
  assert.equal(cancellableMembershipId([running], "2026-10-27", { staffCanCancelRunning: true }), 2);
  assert.equal(cancellableMembershipId([], "2026-10-27"), null);
});

test("cancelling sends the optional reason to the cancel endpoint", async () => {
  const { cancelMembershipApi } = await import("../src/api.js");
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => { calls.push({ url, options }); return new Response("{}", { status: 200 }); };
  try {
    await cancelMembershipApi("t", 5);
    await cancelMembershipApi("t", 6, "Medical");
  } finally {
    globalThis.fetch = original;
  }
  assert.equal(calls[0].url, "http://localhost:8080/api/memberships/5/cancel");
  assert.equal(calls[0].options.method, "PATCH");
  assert.deepEqual(JSON.parse(calls[0].options.body), { reason: null });
  assert.deepEqual(JSON.parse(calls[1].options.body), { reason: "Medical" });
});

test("admins reach the membership sales page through their own guarded route", async () => {
  const { readFile } = await import("node:fs/promises");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const adminGuard = app.indexOf("<Route element={<AdminRoute auth={auth} />}>");
  const route = app.indexOf('path="/admin/membership-sales"');
  assert.ok(adminGuard >= 0 && route > adminGuard);
  assert.ok(route < app.indexOf("<Route element={<MemberRoute auth={auth} />}>"));
});
