import test from "node:test";
import assert from "node:assert/strict";
import {
  activeSeatCount, counterCoursePaymentPayload, courseAgeWarning, eligibilityIssues, estimateCoursePrice,
  formatVietnamTime, membershipDiscount,
} from "../src/receptionistRegistration.js";

test("staff counter payment payload is tied to the selected member and registration", () => {
  assert.deepEqual(counterCoursePaymentPayload("12", "45"), {
    memberId: 12, method: "COUNTER", purpose: "COURSE_REGISTRATION", referenceId: 45,
  });
  assert.throws(() => counterCoursePaymentPayload(0, 2));
});

test("seat estimate counts confirmed and unexpired holds only", () => {
  const now = Date.parse("2026-10-20T00:00:00Z");
  assert.equal(activeSeatCount([
    { classId: 3, status: "CONFIRMED" },
    { classId: 3, status: "PENDING_PAYMENT", paymentDeadline: "2026-10-21T09:00:00" },
    { classId: 3, status: "PENDING_PAYMENT", paymentDeadline: "2026-10-19T09:00:00" },
    { classId: 4, status: "CONFIRMED" },
  ], 3, now), 2);
});

test("eligibility preview identifies age, verified level and duplicate offering issues", () => {
  const member = { id: 8, role: "MEMBER", status: "ACTIVE", dateOfBirth: "2015-01-01" };
  const course = { level: "INTERMEDIATE", agePolicy: "REQUIRED", targetAgeMin: 18, targetAgeMax: 35 };
  const offering = { id: 14, startDate: "2026-12-20", registrationDeadline: "2099-01-01T00:00:00" };
  const registrations = [{ memberId: 8, courseOfferingId: 14, status: "PENDING_PAYMENT", paymentDeadline: "2099-01-01T00:00:00" }];
  const issues = eligibilityIssues(member, course, null, offering, registrations, "2026-11-05");
  assert.ok(issues.some((issue) => /độ tuổi/i.test(issue)));
  assert.ok(issues.some((issue) => issue.includes("HLV xác minh")));
  assert.ok(issues.some((issue) => issue.includes("đăng ký đang giữ chỗ")));
});

test("recommended age warning is informational while required age mismatch blocks", () => {
  const member = { id: 8, role: "MEMBER", status: "ACTIVE", dateOfBirth: "2012-01-01" };
  const recommended = { level: "BEGINNER", agePolicy: "RECOMMENDED", targetAgeMin: 18, targetAgeMax: 35 };
  assert.match(courseAgeWarning(member, recommended, "2026-11-05"), /khuyến nghị/i);
  assert.equal(eligibilityIssues(member, recommended, null, null, [], "2026-11-05").some((issue) => /độ tuổi/i.test(issue)), false);
  assert.equal(courseAgeWarning(member, { ...recommended, targetAgeMin: 5, targetAgeMax: 9 }, "2026-11-05"), "");
  const required = { ...recommended, agePolicy: "REQUIRED" };
  assert.ok(eligibilityIssues(member, required, null, null, [], "2026-11-05").some((issue) => /bắt buộc/i.test(issue)));
});

test("membership quote is a display estimate and preserves cents", () => {
  assert.deepEqual(estimateCoursePrice("900000.00", 15), {
    originalPrice: 900000, membershipDiscount: 135000, finalPrice: 765000,
  });
  const percent = membershipDiscount(
    [{ planId: 2, status: "ACTIVE", startDate: "2026-01-01", endDate: "2026-12-31" }],
    [{ id: 2, courseDiscountPercent: 15 }], new Date("2026-10-20T04:00:00Z"),
  );
  assert.equal(percent, 15);
});

test("registration deadline is shown in Vietnam-local time", () => {
  assert.match(formatVietnamTime("2026-10-21T09:00:00"), /2026|21/);
});

test("Receptionist registration opening is 14 days before the Vietnam-local deadline", () => {
  const member = { id: 8, role: "MEMBER", status: "ACTIVE", dateOfBirth: "1990-01-01" };
  const course = { level: "BEGINNER" };
  const atBoundary = { id: 14, startDate: "2027-01-12", registrationDeadline: "2026-12-09T13:19:00" };
  const beforeBoundary = { ...atBoundary, registrationDeadline: "2026-12-10T13:19:00" };
  const earlierDeadline = { ...atBoundary, registrationDeadline: "2026-12-08T13:19:00" };
  const beforeOpening = Date.parse("2026-11-25T06:18:59Z");
  const openingBoundary = Date.parse("2026-11-25T06:19:00Z");
  const afterOpening = Date.parse("2026-11-25T06:19:01Z");
  assert.match(eligibilityIssues(member, course, null, atBoundary, [], "2026-11-25", beforeOpening).find((issue) => /mở từ/.test(issue)), /13:19.*25 thg 11, 2026/);
  assert.match(eligibilityIssues(member, course, null, beforeBoundary, [], "2026-11-25", beforeOpening).find((issue) => /mở từ/.test(issue)), /13:19.*26 thg 11, 2026/);
  assert.equal(eligibilityIssues(member, course, null, atBoundary, [], "2026-11-25", openingBoundary).some((issue) => /mở từ/.test(issue)), false);
  assert.equal(eligibilityIssues(member, course, null, earlierDeadline, [], "2026-11-25", afterOpening).some((issue) => /mở từ/.test(issue)), false);
  assert.ok(eligibilityIssues(member, course, null, { ...atBoundary, registrationDeadline: "2026-11-24T13:19:00" }, [], "2026-11-25", Date.parse("2026-11-25T06:19:00Z")).some((issue) => /qua hạn/.test(issue)));
});
