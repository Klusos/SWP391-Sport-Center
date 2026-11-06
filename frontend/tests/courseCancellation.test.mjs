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
test("Receptionist refund UI displays course context and confirms before existing API", async () => {
 const source = await read("../src/pages/ReceptionistMembershipSales.jsx");
 assert.match(source, /payment\.courseName/);
 assert.match(source, /payment\.courseOfferingName/);
 assert.match(source, /payment\.className/);
 assert.match(source, /payment\.registrationId/);
 assert.match(source, /window\.confirm/);
 assert.match(source, /confirmRefundApi\(token, payment.id/);
 assert.match(source, /getRefundRequiredPaymentsApi\(token, 0, 50\)/);
});
