import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { memberOfferingLists, offeringVisibility } from "../src/memberCourseOfferingVisibility.js";
import { memberRegistrationSections } from "../src/memberRegistrationSections.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");
const at = (value) => new Date(`${value}+07:00`);

test("Member course discovery trusts server registration availability and excludes history and incomplete offerings", () => {
  const rows = [
    { id: 1, registrationAvailable: true, registrationReadyClassIds: [10] },
    { id: 2, registrationAvailable: false, registrationReadyClassIds: [20] },
    { id: 3, registrationAvailable: true, registrationReadyClassIds: [] },
    { id: 4, registrationAvailable: false, status: "CLOSED" },
  ];
  assert.equal(offeringVisibility(rows[0]), "REGISTRABLE");
  assert.deepEqual(memberOfferingLists(rows).current.map((row) => row.id), [1]);
  assert.deepEqual(memberOfferingLists(rows).history, []);
});

test("Member registration sections preserve every record and use status and reliable class schedule dates", () => {
  const now = at("2026-12-11T12:00:00").getTime();
  const registrations = [
    { id: 1, classId: 10, status: "PENDING_PAYMENT", paymentDeadline: "2026-12-12T12:00:00", registeredAt: "2026-12-10T09:00:00" },
    { id: 2, classId: 20, plannedSessions: 1, status: "CONFIRMED", registeredAt: "2026-12-09T09:00:00" },
    { id: 3, classId: 30, plannedSessions: 1, status: "CONFIRMED", registeredAt: "2026-12-08T09:00:00", paymentReference: "payment-3" },
    { id: 4, classId: 40, status: "CANCELLED", registeredAt: "2026-12-07T09:00:00", refundStatus: "REFUND_REQUIRED" },
    { id: 5, classId: 50, status: "EXPIRED", registeredAt: "2026-12-06T09:00:00" },
    { id: 6, classId: 60, status: "PENDING_PAYMENT", paymentDeadline: "2026-12-11T11:59:00", registeredAt: "2026-12-11T09:00:00" },
  ];
  const sessions = [
    { id: 1, classId: 20, status: "SCHEDULED", startTime: "2026-12-11T11:30:00", endTime: "2026-12-11T12:30:00" },
    { id: 2, classId: 30, status: "COMPLETED", startTime: "2026-12-01T09:00:00", endTime: "2026-12-01T10:00:00" },
  ];
  const result = memberRegistrationSections(registrations, sessions, now);
  assert.deepEqual(result.current.map((item) => item.id), [2, 1]);
  assert.equal(result.current[1].scheduleUnavailable, false);
  assert.deepEqual(result.history.map((item) => item.id), [6, 3, 4, 5]);
  assert.equal(result.history.find((item) => item.id === 4).refundStatus, "REFUND_REQUIRED");
  assert.equal(result.history.find((item) => item.id === 3).paymentReference, "payment-3");
});

test("Member Course UI removes the historical Offering toggle and keeps registration history navigation", async () => {
  const [courses, detail, css, api] = await Promise.all([
    read("../src/pages/MemberCourses.jsx"),
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/pages/MemberCourses.css"),
    read("../src/api.js"),
  ]);
  assert.match(courses, /memberRegistrationSections\(registrations, classSessions\)/);
  assert.match(courses, /getMyClassSessionsApi\(token\)/);
  assert.match(courses, /member\/course-checkout\/\$\{item\.id\}/);
  assert.match(courses, /info\.status === "REFUND_REQUIRED"/);
  assert.match(courses, /info\.status === "REFUNDED"/);
  assert.match(courses, /cancelCourseRegistrationApi\(token, item\.id\)/);
  assert.match(courses, /member-registration-show-more/);
  assert.doesNotMatch(detail, /Show\/Hide past offerings|Xem đợt đã kết thúc|showOfferingHistory/);
  assert.match(detail, /registrationReadyClassIds/);
  assert.match(detail, /visibleOfferings\.length === 0/);
  assert.match(detail, /getCourseOfferingsByCourseApi\(auth\.accessToken, course\.id, true\)/);
  assert.match(api, /includeRegistrationReadiness=true/);
  assert.match(css, /\.member-registration-tabs/);
  assert.match(css, /\.member-registration-show-more/);
});
