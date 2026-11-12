import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { confirmRefundApi, getRefundRequiredPaymentsApi } from "../src/api.js";
import { loadAllRefundQueuePages, REFUND_PURPOSES, refundPurposeLabel, refundQueuePage, refundQueueRows, refundQueueRowsForPurposes, refundReferenceLabel } from "../src/refundQueue.js";

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

test("refund categories are purpose-specific and membership and upgrade refunds stay discoverable", () => {
  const payments = [
    { id: 11001, purpose: "COURSE_REGISTRATION", status: "REFUND_REQUIRED" },
    { id: 11002, purpose: "PT_BOOKING", status: "REFUND_REQUIRED" },
    { id: 11003, purpose: "MEMBERSHIP", status: "REFUND_REQUIRED" },
    { id: 11004, purpose: "UPGRADE", status: "REFUND_REQUIRED" },
  ];
  const course = refundQueueRowsForPurposes(payments, ["COURSE_REGISTRATION"]);
  const pt = refundQueueRowsForPurposes(payments, ["PT_BOOKING"]);
  const other = refundQueueRowsForPurposes(payments, ["MEMBERSHIP", "UPGRADE"]);
  assert.deepEqual(course.map(({ id }) => id), [11001]);
  assert.deepEqual(pt.map(({ id }) => id), [11002]);
  assert.deepEqual(other.map(({ id }) => id), [11003, 11004]);
  assert.equal(course.some(({ purpose }) => purpose === "PT_BOOKING"), false);
  assert.equal(pt.some(({ purpose }) => purpose === "COURSE_REGISTRATION"), false);
  assert.ok(payments.every(({ status }) => status === "REFUND_REQUIRED"));
  assert.equal(refundQueueRowsForPurposes([{ id: 1, status: "REFUNDED", purpose: "PT_BOOKING" }], ["PT_BOOKING"])[0].status, "REFUNDED");
});

test("all backend pages are loaded before purpose filtering and display pagination", async () => {
  const calls = [];
  const pages = [
    { content: [{ id: 1, purpose: "MEMBERSHIP" }, { id: 2, purpose: "MEMBERSHIP" }], totalPages: 2, last: false },
    { content: [{ id: 3, purpose: "PT_BOOKING" }], totalPages: 2, last: true },
  ];
  const all = await loadAllRefundQueuePages(async (page, size) => { calls.push([page, size]); return pages[page]; }, 2);
  assert.deepEqual(calls, [[0, 2], [1, 2]]);
  assert.deepEqual(refundQueueRowsForPurposes(all, ["PT_BOOKING"]).map(({ id }) => id), [3]);
  assert.deepEqual(refundQueuePage(all, 0, 2), { content: all.slice(0, 2), totalElements: 3, totalPages: 2, number: 0, last: false });
  assert.deepEqual(refundQueuePage(all, 1, 2).content.map(({ id }) => id), [3]);
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
    await confirmRefundApi("test-token", 11002, "Cash returned at reception");
    assert.equal(calls[0].url.pathname, "/api/payments/refund-required");
    assert.equal(calls[0].url.search, "?page=2&size=50");
    assert.equal(calls[0].options.headers.Authorization, "Bearer test-token");
    assert.equal(calls[1].url.pathname, "/api/payments/11002/refund");
    assert.equal(calls[1].options.method, "PATCH");
    assert.deepEqual(JSON.parse(calls[1].options.body), { note: "Cash returned at reception" });
  } finally { globalThis.fetch = original; }
});

test("only Course & PT Payments renders refund confirmation actions", async () => {
  const coursePayments = await readFile(new URL("../src/pages/ReceptionistCoursePayments.jsx", import.meta.url), "utf8");
  const ptPayments = await readFile(new URL("../src/pages/ReceptionistPtPayments.jsx", import.meta.url), "utf8");
  const refundComponent = await readFile(new URL("../src/components/ReceptionistRefundQueue.jsx", import.meta.url), "utf8");
  const membershipSales = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
  assert.match(coursePayments, /purposes=\{\["COURSE_REGISTRATION", "MEMBERSHIP", "UPGRADE"\]\}/);
  assert.match(coursePayments, /id: "other", purposes: \["MEMBERSHIP", "UPGRADE"\]/);
  assert.match(ptPayments, /purposes=\{\["PT_BOOKING"\]\}/);
  assert.equal((coursePayments.match(/<ReceptionistRefundQueue\b/g) || []).length, 1);
  assert.equal((ptPayments.match(/<ReceptionistRefundQueue\b/g) || []).length, 1);
  assert.doesNotMatch(coursePayments, /className="cp-refund-queue"|aria-labelledby="cp-refund-title"|Hàng đợi hoàn tiền|Tất cả mục đích thanh toán/);
  assert.match(refundComponent, /refundQueueRowsForPurposes\(queue\.payments, group\.purposes \?\? purposes\)/);
  assert.match(refundComponent, /loadAllRefundQueuePages/);
  assert.match(refundComponent, /confirmRefundApi\(token, payment\.id/);
  assert.match(refundComponent, /payment\?\.status !== "REFUND_REQUIRED"/);
  assert.doesNotMatch(membershipSales, /getRefundRequiredPaymentsApi|confirmRefundApi/);
  assert.doesNotMatch(ptPayments, /confirmRefundApi/);
  assert.match(membershipSales, /getCancellationRequestsApi/);
});
