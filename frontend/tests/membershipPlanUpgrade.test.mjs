import test from "node:test";
import assert from "node:assert/strict";
import {
  addMembershipPlanUpgradePathApi,
  getMembershipPlanUpgradePathsApi,
  removeMembershipPlanUpgradePathApi,
} from "../src/api.js";
import { availableUpgradeTargets, upgradePathRequestError } from "../src/membershipPlanUpgrade.js";

test("upgrade target picker includes only active, more expensive, unconfigured targets", () => {
  const source = { id: 1, status: "ACTIVE", monthlyPrice: 100 };
  const plans = [source,
    { id: 2, status: "ACTIVE", monthlyPrice: 200 },
    { id: 3, status: "ACTIVE", monthlyPrice: 100 },
    { id: 4, status: "INACTIVE", monthlyPrice: 300 },
    { id: 5, status: "ACTIVE", monthlyPrice: 50 }];
  const paths = [{ id: 8, fromPlanId: 1, toPlanId: 2 }];
  assert.deepEqual(availableUpgradeTargets(source, plans, paths), []);
  assert.deepEqual(availableUpgradeTargets(source, plans, []), [plans[1]]);
  assert.deepEqual(availableUpgradeTargets({ ...source, status: "INACTIVE" }, plans, []), []);
});

test("upgrade path API uses authenticated ADMIN routes and directional plan IDs", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return options.method === "DELETE" ? new Response(null, { status: 204 }) : Response.json([]);
  };
  try {
    await getMembershipPlanUpgradePathsApi("admin-token");
    await addMembershipPlanUpgradePathApi("admin-token", 11, 22);
    await removeMembershipPlanUpgradePathApi("admin-token", 9);
    assert.match(calls[0].url, /\/api\/membership-plans\/upgrade-paths$/);
    assert.equal(calls[0].options.headers.Authorization, "Bearer admin-token");
    assert.equal(calls[1].options.method, "POST");
    assert.deepEqual(JSON.parse(calls[1].options.body), { fromPlanId: 11, toPlanId: 22 });
    assert.equal(calls[2].options.method, "DELETE");
    assert.match(calls[2].url, /\/api\/membership-plans\/upgrade-paths\/9$/);
  } finally { globalThis.fetch = originalFetch; }
});

test("upgrade path UI maps expected authorization and validation failures", () => {
  assert.match(upgradePathRequestError(400), /đang hoạt động/i);
  assert.match(upgradePathRequestError(403), /ADMIN/);
  assert.match(upgradePathRequestError(409), /đã tồn tại/i);
  assert.equal(upgradePathRequestError(500), null);
});
