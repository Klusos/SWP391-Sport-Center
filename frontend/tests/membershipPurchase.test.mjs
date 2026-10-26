import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { membershipPurchaseErrorMessage } from "../src/membershipPurchase.js";

test("disabled demo online payments produce a clear, non-success message", () => {
  const message = membershipPurchaseErrorMessage({ status: 503, message: "An unexpected error occurred" });

  assert.match(message, /chưa được bật cho bản demo/);
  assert.match(message, /Không có thanh toán mới nào được tạo/);
});

test("purchase errors remain visible after payment history is reloaded", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  const reloadIndex = source.indexOf("await load();", source.indexOf("membershipPurchaseErrorMessage(requestError)"));
  const displayIndex = source.indexOf("setError(`${message}", reloadIndex);

  assert.notEqual(reloadIndex, -1);
  assert.ok(displayIndex > reloadIndex);
});

test("membership payment methods are chosen in the shared modal before payment creation", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  const start = source.slice(source.indexOf("async function startPurchase"), source.indexOf("function openPurchaseModal"));

  assert.match(source, /onClick=\{\(\) => openPurchaseModal\(plan\)\}/);
  assert.match(source, /<PaymentMethodModal/);
  assert.match(source, /function openPurchaseModal\(plan\)[\s\S]*?setPaymentModalPlan\(plan\)/);
  assert.match(source, /function closePaymentModal\(\)[\s\S]*?setPaymentModalPlan\(null\)/);
  assert.match(source, /onContinue=\{\(method\) => startPurchase\(paymentModalPlan, method\)\}/);
  assert.match(start, /method,\s*purpose: "MEMBERSHIP",\s*referenceId: Number\(plan\.id\),\s*autoRenew: false/s);
  assert.match(start, /if \(busyPlanId !== null \|\| pendingMembershipPayments\.length > 0 \|\| paymentReconciliationRequired\) return false/);
  assert.doesNotMatch(start, /window\.confirm/);
  assert.match(start, /validVnpayUrl\(payment\.paymentUrl\)/);
  assert.match(start, /const payment = await createPaymentApi/);
  assert.match(start, /const refreshedPayments = await load\(\)/);
  assert.match(start, /if \(!validVnpayUrl\(payment\.paymentUrl\)\)[\s\S]*?return true/);
  assert.match(source, /onSuccess=\{\(\) => setPaymentModalPlan\(null\)\}/);
  assert.match(start, /setPaymentReconciliationRequired\(true\)/);
});
