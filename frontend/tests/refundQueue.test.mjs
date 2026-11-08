import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { confirmRefundApi, getRefundRequiredPaymentsApi } from "../src/api.js";
import { REFUND_PURPOSES, refundPurposeLabel, refundQueueRows, refundReferenceLabel } from "../src/refundQueue.js";

test("refund purposes are explicitly classified and each keeps its purpose-scoped reference", () => {
  assert.deepEqual(REFUND_PURPOSES, ["MEMBERSHIP", "COURSE_REGISTRATION", "PT_BOOKING", "UPGRADE"]);
  const cases = [
    [{ purpose: "MEMBERSHIP", referenceId: 10 }, "Gói hội viên", "Gói #10"],
    [{ purpose: "COURSE_REGISTRATION", referenceId: 11, registrationId: 11 }, "Đăng ký khóa học", "Đăng ký #11"],
    [{ purpose: "PT_BOOKING", referenceId: 12 }, "Buổi PT", "Booking PT #12"],
    [{ purpose: "UPGRADE", referenceId: 13 }, "Nâng cấp gói", "Gói nâng cấp #13"],
  ];
  for (const [payment, purpose, reference] of cases) {
    assert.equal(refundPurposeLabel(payment), purpose);
    assert.equal(refundReferenceLabel(payment), reference);
  }
  assert.match(refundPurposeLabel({ purpose: "FUTURE_KIND" }), /FUTURE_KIND/);
  assert.equal(refundReferenceLabel({ purpose: "FUTURE_KIND", referenceId: 14 }), "Tham chiếu #14");
});

test("course refund context, actual amount, payment status and pagination rows are preserved", () => {
  const payment = { id: 7, purpose: "COURSE_REGISTRATION", referenceId: 21, registrationId: 21,
    courseName: "Swimming", courseOfferingName: "Summer", className: "Morning", amount: 475000, status: "REFUND_REQUIRED" };
  assert.equal(refundReferenceLabel(payment), "Đăng ký #21");
  assert.equal(payment.amount, 475000);
  assert.equal(payment.status, "REFUND_REQUIRED");
  assert.deepEqual(refundQueueRows({ content: [payment], totalElements: 3 }), [payment]);
  assert.deepEqual(refundQueueRows(null), []);
});

test("refund list and confirmation keep the existing authenticated endpoints and payload", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url: new URL(url), options });
    return new Response(JSON.stringify({ content: [] }), { status: 200 });
  };
  try {
    await getRefundRequiredPaymentsApi("test-token", 2, 50);
    await confirmRefundApi("test-token", 7, "Cash returned at reception");
    assert.equal(calls[0].url.pathname, "/api/payments/refund-required");
    assert.equal(calls[0].url.search, "?page=2&size=50");
    assert.equal(calls[0].options.headers.Authorization, "Bearer test-token");
    assert.equal(calls[1].url.pathname, "/api/payments/7/refund");
    assert.equal(calls[1].options.method, "PATCH");
    assert.deepEqual(JSON.parse(calls[1].options.body), { note: "Cash returned at reception" });
  } finally { globalThis.fetch = original; }
});

test("only Course & PT Payments renders refund confirmation actions", async () => {
  const coursePayments = await readFile(new URL("../src/pages/ReceptionistCoursePayments.jsx", import.meta.url), "utf8");
  const membershipSales = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
  assert.match(coursePayments, /getRefundRequiredPaymentsApi/);
  assert.match(coursePayments, /confirmRefundApi/);
  assert.match(coursePayments, /payment\.status !== "REFUND_REQUIRED"/);
  assert.doesNotMatch(membershipSales, /getRefundRequiredPaymentsApi|confirmRefundApi/);
  assert.match(membershipSales, /getCancellationRequestsApi/);
});
