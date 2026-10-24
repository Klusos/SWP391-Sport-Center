import test from "node:test";
import assert from "node:assert/strict";
import {
  activeSeatCount, counterCoursePaymentPayload, eligibilityIssues, estimateCoursePrice,
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
  const course = { level: "INTERMEDIATE", targetAgeMin: 18 };
  const offering = { id: 14, startDate: "2026-12-20", registrationDeadline: "2099-01-01T00:00:00" };
  const registrations = [{ memberId: 8, courseOfferingId: 14, status: "PENDING_PAYMENT", paymentDeadline: "2099-01-01T00:00:00" }];
  const issues = eligibilityIssues(member, course, null, offering, registrations);
  assert.ok(issues.some((issue) => issue.includes("độ tuổi")));
  assert.ok(issues.some((issue) => issue.includes("HLV xác minh")));
  assert.ok(issues.some((issue) => issue.includes("đăng ký đang giữ chỗ")));
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
