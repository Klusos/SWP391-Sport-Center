import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const component = await readFile(new URL("../src/components/PaymentResultCard.jsx", import.meta.url), "utf8");
const css = await readFile(new URL("../src/components/PaymentResultCard.css", import.meta.url), "utf8");
const checkout = await readFile(new URL("../src/pages/CourseCheckout.jsx", import.meta.url), "utf8");

test("shared result card renders transaction and service state independently", () => {
  assert.match(component, /paymentStatusPresentation\(payment\?\.status\)/);
  assert.match(component, /ServiceDetails payment=\{payment\}/);
  assert.match(component, /payment\.status === "SUCCESS" && serviceStatus/);
  assert.match(component, /status-\$\{String\(ptBooking\.status/);
  assert.match(component, /status-\$\{String\(registration\.status/);
  assert.match(component, /status-\$\{String\(membership\.status/);
  assert.match(component, /payment\.status === "REFUND_REQUIRED"/);
});

test("result card contains one purpose-selected primary link and no cross-purpose shortcuts", () => {
  assert.match(component, /paymentPurposePresentation\(payment\)/);
  assert.match(component, /to=\{purpose\.path\}/);
  assert.equal((component.match(/className="payment-result-primary"/g) || []).length, 2);
  assert.doesNotMatch(component, /to="\/member\/(?:courses|membership|pt-booking)"/);
  assert.match(component, /purpose\.action/);
});

test("missing, pending, failed and unknown results keep a safe manual reconciliation path", () => {
  assert.match(component, /loading \? "Đang tải kết quả thanh toán"/);
  assert.match(component, /error \|\| statusError \? "Chưa tải được trạng thái"/);
  assert.match(component, /payment\?\.status === "PENDING"/);
  assert.match(component, /status\.tone === "unknown"/);
  assert.match(component, /onClick=\{onRefresh\}/);
  assert.match(component, /disabled=\{busy \|\| loading\}/);
});

test("result resolution ignores URL payment status and keeps authenticated owner lookup", () => {
  assert.match(checkout, /findOwnedPayment\(payments, paymentId\)/);
  assert.doesNotMatch(checkout, /params\.get\("paymentStatus"\)/);
  assert.doesNotMatch(checkout, /params\.get\("purpose"\)/);
  assert.match(checkout, /returnedPayment\?\.purpose === "PT_BOOKING"/);
  assert.match(checkout, /isMembershipPayment\(returnedPayment\?\.purpose\)/);
  assert.match(checkout, /returnedPayment\?\.purpose === "COURSE_REGISTRATION"/);
  const resultBlock = checkout.slice(checkout.indexOf("  if (result) {"), checkout.indexOf("\n  return <div className=\"layout\"", checkout.indexOf("  if (result) {")));
  assert.doesNotMatch(resultBlock, /<Sidebar/);
  assert.match(resultBlock, /<PaymentResultCard/);
});

test("Upgrade results use the Membership destination without claiming the upgraded service is active", () => {
  assert.match(component, /case "UPGRADE"/);
  assert.match(component, /payment\?\.purpose === "UPGRADE" \? null/);
  assert.match(component, /case "UPGRADE":[\s\S]*payment\.referenceId/);
  assert.doesNotMatch(component, /payment\.status === "SUCCESS"[^\n]*UPGRADE/);
});

test("result layout is responsive, centered and keyboard-visible", () => {
  assert.match(css, /width: min\(100%, 720px\)/);
  assert.match(css, /justify-content: center/);
  assert.match(css, /@media \(max-width: 600px\)/);
  assert.match(css, /:focus-visible/);
  assert.match(component, /aria-labelledby="payment-result-title"/);
  assert.match(component, /aria-live="polite"/);
  assert.match(component, /aria-label="Điều hướng kết quả thanh toán"/);
});

test("successful PT result omits the expired hold-deadline detail", () => {
  assert.doesNotMatch(component, /paymentExpiresAt|Hạn thanh toán|Payment hold deadline/);
  assert.match(component, /formatVietnamDateTime\(ptBooking\.startTime\)/);
  assert.match(component, /formatVietnamDateTime\(ptBooking\.endTime\)/);
});
