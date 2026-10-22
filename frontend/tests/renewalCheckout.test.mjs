import test from "node:test";
import assert from "node:assert/strict";
import { createRenewalCheckoutApi, selectRenewalPaymentMethodApi } from "../src/api.js";
import {
  canRequestRenewalCheckout,
  renewalAttemptState,
  renewalOptionsPayload,
  renewalPayments,
  safeRenewalCheckoutUrl,
} from "../src/renewalCheckout.js";

test("renewal discovery uses the persisted isRenewal flag, not Membership purpose or references", () => {
  const payments = [
    { id: 1, purpose: "MEMBERSHIP", referenceId: 9, transactionCode: "RNLanything", isRenewal: false },
    { id: 2, purpose: "MEMBERSHIP", referenceId: 9, transactionCode: "ordinary", isRenewal: true },
  ];
  assert.deepEqual(renewalPayments(payments).map(({ id }) => id), [2]);
});

test("renewal choices require explicit method and boolean, retaining explicit false", () => {
  assert.deepEqual(renewalOptionsPayload("ONLINE", true), { method: "ONLINE", renewalAutoRenewChoice: true });
  assert.deepEqual(renewalOptionsPayload("COUNTER", false), { method: "COUNTER", renewalAutoRenewChoice: false });
  assert.throws(() => renewalOptionsPayload("ONLINE", ""));
  assert.throws(() => renewalOptionsPayload("", false));
});

test("attempt expiry is separate from parent payment state and expired attempts can retry", () => {
  const now = Date.parse("2026-10-22T00:00:00Z");
  const base = { isRenewal: true, status: "PENDING", method: "ONLINE", renewalAutoRenewChoice: false };
  const issued = { ...base, renewalCheckoutAttempt: { status: "URL_ISSUED", expiresAt: "2026-10-23T12:00:00" } };
  const expired = { ...base, renewalCheckoutAttempt: { status: "URL_ISSUED", expiresAt: "2026-10-21T12:00:00" } };
  const failed = { ...base, renewalCheckoutAttempt: { status: "FAILED", expiresAt: "2026-10-23T12:00:00" } };
  assert.equal(renewalAttemptState(issued, now), "ISSUED");
  assert.equal(canRequestRenewalCheckout(issued, now), false);
  assert.equal(renewalAttemptState(expired, now), "EXPIRED");
  assert.equal(canRequestRenewalCheckout(expired, now), true);
  assert.equal(renewalAttemptState(failed, now), "FAILED");
  assert.equal(canRequestRenewalCheckout(failed, now), true);
  assert.equal(renewalAttemptState({ ...base, status: "REFUND_REQUIRED" }, now), "RECONCILIATION");
  assert.equal(canRequestRenewalCheckout({ ...base, status: "REFUND_REQUIRED" }, now), false);
});

test("checkout is available only for pending, configured ONLINE renewal and accepts only validated VNPay URL", () => {
  const eligible = { isRenewal: true, status: "PENDING", method: "ONLINE", renewalAutoRenewChoice: true };
  assert.equal(canRequestRenewalCheckout(eligible), true);
  assert.equal(canRequestRenewalCheckout({ ...eligible, method: "COUNTER" }), false);
  assert.equal(canRequestRenewalCheckout({ ...eligible, renewalAutoRenewChoice: null }), false);
  assert.equal(safeRenewalCheckoutUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Command=pay&vnp_SecureHash=abc"), true);
  assert.equal(safeRenewalCheckoutUrl("https://evil.example/payment?success=true"), false);
});

test("renewal APIs use authenticated endpoints and send no client price", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return new Response(JSON.stringify({ paymentId: 12 }), { status: 200, headers: { "Content-Type": "application/json" } });
  };
  try {
    await selectRenewalPaymentMethodApi("member-token", 12, renewalOptionsPayload("ONLINE", false));
    await createRenewalCheckoutApi("member-token", 12);
    assert.match(calls[0].url, /\/api\/payments\/12\/renewal-method$/);
    assert.equal(calls[0].options.method, "PATCH");
    assert.equal(calls[0].options.headers.Authorization, "Bearer member-token");
    assert.deepEqual(JSON.parse(calls[0].options.body), { method: "ONLINE", renewalAutoRenewChoice: false });
    assert.match(calls[1].url, /\/api\/payments\/12\/renewal-checkout$/);
    assert.equal(calls[1].options.method, "POST");
    assert.equal(calls[1].options.body, undefined);
  } finally { globalThis.fetch = originalFetch; }
});

test("renewal API errors preserve HTTP status for safe UI handling", async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ message: "conflict" }), { status: 409, headers: { "Content-Type": "application/json" } });
  try {
    await assert.rejects(createRenewalCheckoutApi("member-token", 12), (error) => error.status === 409 && error.message === "conflict");
  } finally { globalThis.fetch = originalFetch; }
});
