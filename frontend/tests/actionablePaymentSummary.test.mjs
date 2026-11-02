import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { getMyActionablePendingPaymentSummaryApi } from "../src/api.js";

test("dashboard reads the member-scoped actionable summary, not raw pending history", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  assert.match(dashboard, /actionablePayments:\s*getMyActionablePendingPaymentSummaryApi/);
  assert.match(dashboard, /pendingBillCount = actionablePayments\?\.count/);
  assert.match(dashboard, /pendingBillTotal = actionablePayments\?\.totalAmount/);
  assert.doesNotMatch(dashboard, /getMyPaymentsApi/);
  assert.match(dashboard, /pendingBillCount > 0/);
  assert.match(dashboard, /vnd\(pendingBillTotal\)/);
});

test("actionable summary API uses the authenticated Member endpoint", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (...args) => {
    calls.push(args);
    return new Response(JSON.stringify({ count: 2, totalAmount: 450000 }), { status: 200 });
  };
  try {
    assert.deepEqual(await getMyActionablePendingPaymentSummaryApi("member-token"), {
      count: 2, totalAmount: 450000,
    });
    assert.equal(new URL(calls[0][0]).pathname, "/api/payments/me/actionable-summary");
    assert.equal(calls[0][1].headers.Authorization, "Bearer member-token");
  } finally {
    globalThis.fetch = originalFetch;
  }
});
