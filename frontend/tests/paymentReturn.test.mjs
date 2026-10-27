import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { findOwnedPayment, findOwnedPtBooking } from "../src/paymentReturn.js";
import { paymentPurposePresentation, paymentServiceLabel, paymentStatusPresentation, serviceStatusLabel } from "../src/paymentResult.js";
import { startStatusPolling } from "../src/courseCheckout.js";

const payments = [
  { id: 10, memberId: 4, purpose: "PT_BOOKING", referenceId: 71, status: "PENDING" },
  { id: 11, memberId: 4, purpose: "MEMBERSHIP", referenceId: 2, status: "SUCCESS" },
  { id: 12, memberId: 4, purpose: "COURSE_REGISTRATION", referenceId: 33, status: "CANCELLED" },
];

test("return resolves only an exact positive ID found in authenticated owner payment history", () => {
  assert.equal(findOwnedPayment(payments, "10"), payments[0]);
  for (const invalid of [null, "", "0", "1.2", "-1", "1e2", "9007199254740992"]) assert.equal(findOwnedPayment(payments, invalid), null);
  assert.equal(findOwnedPayment(payments, "999"), null, "another owner's payment is absent from the authenticated history response");
});

test("URL paymentStatus cannot override the status on the authenticated payment", () => {
  const url = new URL("http://localhost/member/membership?paymentId=10&paymentStatus=SUCCESS");
  const payment = findOwnedPayment(payments, url.searchParams.get("paymentId"));
  assert.equal(payment.status, "PENDING");
  assert.equal(paymentStatusPresentation(payment.status).title, "Đang chờ xác nhận");
});

test("PT return resolves the booking only from PT purpose and its backend reference ID", () => {
  const booking = { id: 71, status: "CONFIRMED" };
  assert.equal(findOwnedPtBooking([booking], payments[0]), booking);
  assert.equal(findOwnedPtBooking([booking], payments[1]), null);
  assert.equal(findOwnedPtBooking([{ id: 72 }], payments[0]), null);
});

test("each Payment purpose has only its own primary destination", () => {
  assert.deepEqual(paymentPurposePresentation({ purpose: "PT_BOOKING" }), { label: "Thanh toán buổi tập cá nhân", action: "Xem lịch PT", path: "/member/pt-booking" });
  assert.deepEqual(paymentPurposePresentation({ purpose: "MEMBERSHIP" }), { label: "Thanh toán gói hội viên", action: "Xem gói hội viên", path: "/member/membership" });
  assert.equal(paymentPurposePresentation({ purpose: "UPGRADE" }).path, "/member/membership");
  assert.deepEqual(paymentPurposePresentation({ purpose: "COURSE_REGISTRATION" }), { label: "Thanh toán khóa học", action: "Xem khóa học của tôi", path: "/member/courses" });
  assert.equal(paymentPurposePresentation({ purpose: "OTHER" }), null);
});

test("result status is localized and independent of the service resource status", () => {
  assert.match(paymentStatusPresentation("SUCCESS").explanation, /Máy chủ xác nhận khoản thanh toán/);
  assert.match(paymentStatusPresentation("PENDING").explanation, /chưa xác nhận buổi tập, đăng ký khóa học hoặc gói hội viên/);
  assert.equal(paymentStatusPresentation("CANCELLED").title, "Giao dịch đã hủy");
  assert.equal(paymentStatusPresentation("FAILED").title, "Thanh toán thất bại");
  assert.equal(paymentStatusPresentation("REFUND_REQUIRED").tone, "warning");
  assert.equal(paymentStatusPresentation("UNKNOWN").tone, "unknown");
  assert.equal(paymentServiceLabel("PT_BOOKING"), "Trạng thái buổi tập");
  assert.equal(serviceStatusLabel("CONFIRMED"), "Đã xác nhận");
  assert.equal(serviceStatusLabel("PENDING_PAYMENT"), "Chờ thanh toán");
});

test("delayed PT settlement is re-read and polling stops only when backend status is terminal", async () => {
  let status = "PENDING", reads = 0, scheduled;
  const updates = [];
  const stop = startStatusPolling(async () => ({ status: status === "PENDING" && ++reads === 1 ? "PENDING" : status }),
    (value, ended) => updates.push({ value, ended }), () => assert.fail("unexpected poll error"), (value) => value.status === "PENDING",
    { setTimer: (fn) => { scheduled = fn; return 1; }, clearTimer: () => {} });
  await Promise.resolve(); await Promise.resolve();
  assert.equal(updates[0].value.status, "PENDING");
  status = "SUCCESS";
  await scheduled();
  assert.equal(updates.at(-1).value.status, "SUCCESS");
  assert.equal(updates.at(-1).ended, true);
  stop();
});

test("shared result loader branches on authenticated purpose and never creates payment during return", async () => {
  const source = await readFile(new URL("../src/pages/CourseCheckout.jsx", import.meta.url), "utf8");
  const readBlock = source.slice(source.indexOf("  const read = useCallback"), source.indexOf("  useEffect(() => {", source.indexOf("  const read = useCallback")));
  assert.match(readBlock, /findOwnedPayment\(payments, paymentId\)/);
  assert.match(readBlock, /isMembershipPayment\(returnedPayment\?\.purpose\)/);
  assert.match(readBlock, /returnedPayment\?\.purpose === "PT_BOOKING"/);
  assert.match(readBlock, /returnedPayment\?\.purpose === "COURSE_REGISTRATION"/);
  assert.match(readBlock, /getMyPtBookingsApi\(token\)/);
  assert.match(readBlock, /getMyRegistrationsApi\(token\)/);
  assert.doesNotMatch(readBlock, /createPaymentApi/);
  assert.doesNotMatch(readBlock, /paymentStatus/);
  assert.match(source, /payment\?\.purpose === "PT_BOOKING" && payment\.status === "PENDING"/);
  assert.match(source, /if \(result\) \{\s+return <main className="payment-result-page">[\s\S]*?<PaymentResultCard/);
  assert.doesNotMatch(source, /params\.get\("paymentStatus"\)/);
});
