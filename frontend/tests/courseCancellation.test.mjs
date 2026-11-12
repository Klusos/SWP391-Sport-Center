import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { cancellationErrorMessage, coursePaymentInfo, paidCancellationEligibility } from "../src/courseCancellation.js";
const read = (file) => readFile(new URL(file, import.meta.url), "utf8");
const registration = (overrides = {}) => ({ id: 17, status: "CONFIRMED", cancellationDeadline: "2026-11-05T15:00:00", ...overrides });
const payment = (overrides = {}) => ({ id: 9, purpose: "COURSE_REGISTRATION", referenceId: 17, status: "SUCCESS", amount: 900000, ...overrides });
test("confirmed registration is eligible strictly before its Vietnam deadline", () => {
 const rows = [payment()];
 assert.equal(paidCancellationEligibility(registration(), rows, new Date("2026-11-05T07:59:59Z")).allowed, true);
 assert.equal(paidCancellationEligibility(registration(), rows, new Date("2026-11-05T08:00:00Z")).reason, "deadline-passed");
 assert.equal(paidCancellationEligibility(registration({ cancellationDeadline: null }), rows).reason, "deadline-unavailable");
});
test("cancellation uses only a unique successful actual payment amount", () => {
 assert.equal(coursePaymentInfo([payment()], 17).amount, 900000);
 assert.equal(coursePaymentInfo([payment({ status: "REFUND_REQUIRED" })], 17).status, "REFUND_REQUIRED");
 assert.equal(coursePaymentInfo([payment({ status: "REFUNDED" })], 17).status, "REFUNDED");
 assert.equal(coursePaymentInfo([payment(), payment({ id: 10 })], 17).amount, null);
 assert.equal(paidCancellationEligibility(registration(), [payment({ status: "PENDING" })]).allowed, false);
});
test("Member UI preserves pending cancellation and uses existing paid-cancel API with refresh", async () => {
 const source = await read("../src/pages/MemberCourses.jsx");
 assert.match(source, /status === "PENDING_PAYMENT"[\s\S]*?cancelHold\(item.id\)/);
 assert.match(source, /window\.confirm\(/);
 assert.match(source, /cancelCourseRegistrationApi\(token, item.id\)/);
 assert.match(source, /Promise\.all\(\[getMyRegistrationsApi\(token\), getMyPaymentsApi\(token\)\]\)/);
 assert.match(source, /info.status === "REFUND_REQUIRED"/);
 assert.match(source, /info.status === "REFUNDED"/);
});
test("cancellation errors are converted to safe user-facing text", () => {
 const deadline = "Course registration cancellation deadline has passed";
 const internal = "SQL Server exception stack";
 assert.notEqual(cancellationErrorMessage({ message: deadline }), deadline);
 assert.notEqual(cancellationErrorMessage({ message: internal }), internal);
});
test("refund queues and confirmation are on their matching Course and PT payment pages", async () => {
 const sales = await read("../src/pages/ReceptionistMembershipSales.jsx");
 const payments = await read("../src/pages/ReceptionistCoursePayments.jsx");
 assert.doesNotMatch(sales, /getRefundRequiredPaymentsApi|confirmRefundApi|refunds\.content/);
 const refundQueue = await read("../src/components/ReceptionistRefundQueue.jsx");
 const ptPayments = await read("../src/pages/ReceptionistPtPayments.jsx");
 assert.match(payments, /purposes=\{\["COURSE_REGISTRATION", "MEMBERSHIP", "UPGRADE"\]\}/);
 assert.match(ptPayments, /purposes=\{\["PT_BOOKING"\]\}/);
 assert.match(refundQueue, /refundPurposeLabel\(payment\)/);
 assert.match(refundQueue, /refundReferenceLabel\(payment\)/);
 assert.match(refundQueue, /payment\.courseName/);
 assert.match(refundQueue, /confirmRefundApi\(token, payment\.id, note\.trim\(\) \|\| null\)/);
 assert.match(refundQueue, /payment\?\.status !== "REFUND_REQUIRED"/);
 assert.match(refundQueue, /queue\.loading/);
 assert.match(refundQueue, /queue\.error/);
 assert.match(refundQueue, /queue\.notice/);
 assert.doesNotMatch(ptPayments, /confirmRefundApi/);
});

test("Member course detail keeps its upper checkout card and separate schedule action", async () => {
 const source = await read("../src/public-site/pages/CourseDetail.jsx");
 assert.match(source, /member-course-registration-primary/);
 assert.match(source, /Tiếp tục thanh toán/);
 assert.match(source, /Xem chi tiết đăng ký/);
 assert.doesNotMatch(source, /Thanh toán VNPay Sandbox \/ tại lễ tân/);
 assert.match(source, /registration\.status === "PENDING_PAYMENT" && <p role="status">/);
 assert.match(source, /<Link to="\/member\/schedule">Đi đến lịch học<\/Link>/);
});
