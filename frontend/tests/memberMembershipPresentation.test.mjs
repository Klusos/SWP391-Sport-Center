import test from "node:test";
import assert from "node:assert/strict";
import { buildPendingMembershipPresentation } from "../src/memberMembershipPresentation.js";
import { readFile } from "node:fs/promises";

const payment = (id, purpose = "MEMBERSHIP", status = "PENDING") => ({ id, purpose, status, amount: 100 });
const intent = (overrides = {}) => ({ intentId: 9, status: "OPEN", quotedAmount: 100,
  attempts: [], ...overrides });

test("an open renewal intent without a Payment is presented once as a request", () => {
  const result = buildPendingMembershipPresentation([intent()], []);
  assert.equal(result.requestIntents.length, 1);
  assert.equal(result.paymentItems.length, 0);
});

test("a linked pending renewal Payment replaces the standalone request by persisted paymentId", () => {
  const result = buildPendingMembershipPresentation([
    intent({ attempts: [{ paymentId: 42, status: "PENDING", deadline: "2026-12-01T10:00:00" }] }),
  ], [payment(42), payment(43)]);
  assert.equal(result.requestIntents.length, 0);
  assert.equal(result.paymentItems.length, 2);
  assert.equal(result.paymentItems.find((item) => item.payment.id === 42).renewalIntent.intentId, 9);
  assert.equal(result.paymentItems.find((item) => item.payment.id === 43).renewalIntent, null);
});

test("an expired linked attempt returns a retry action while retaining its exact Payment", () => {
  const result = buildPendingMembershipPresentation([
    intent({ attempts: [{ paymentId: 42, status: "EXPIRED" }] }),
  ], [payment(42)], Date.parse("2026-11-26T00:00:00Z"));
  assert.equal(result.requestIntents.length, 0);
  assert.equal(result.paymentItems[0].renewalRetry, true);
  assert.equal(result.paymentItems[0].payment.id, 42);
});

test("settled intents are omitted from pending and unresolved collection remains visible", () => {
  const settled = intent({ intentId: 1, status: "SETTLED" });
  const conflict = intent({ intentId: 2, latestResult: "REFUND_REQUIRED", attempts: [{ paymentId: 51, manualReconciliationRequired: true }] });
  const result = buildPendingMembershipPresentation([settled, conflict], [payment(51, "MEMBERSHIP", "REFUND_REQUIRED")]);
  assert.equal(result.requestIntents.length, 0);
  assert.deepEqual(result.unresolvedIntents.map((item) => item.intent.intentId), [2]);
});

test("normal purchase, Upgrade and Renewal Payments coexist and use no amount or plan heuristics", async () => {
  const intents = [intent({ attempts: [{ paymentId: 7, status: "PENDING" }] })];
  const result = buildPendingMembershipPresentation(intents, [payment(7), payment(8, "UPGRADE"), payment(9)]);
  assert.deepEqual(result.paymentItems.map(({ payment: item, renewalIntent: renewal }) => [item.id, Boolean(renewal)]), [[7, true], [8, false], [9, false]]);
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /Yêu cầu và thanh toán đang chờ/);
  assert.match(source, /pendingPresentation\.paymentItems\.map\(\(\{ payment, renewalIntent/);
  assert.doesNotMatch(source, /aria-labelledby="member-renewal-title"/);
});
