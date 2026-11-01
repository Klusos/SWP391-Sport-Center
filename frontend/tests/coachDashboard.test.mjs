import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  coachAssessmentSummary,
  confirmedEnrollmentCount,
  pendingCoachAssessments,
  upcomingTeachingSessions,
} from "../src/coachDashboard.js";

test("Coach summaries total confirmed class enrollments and upcoming sessions are sorted and exclude cancelled or past rows", () => {
  assert.equal(confirmedEnrollmentCount([{ confirmedMembers: 4 }, { confirmedMembers: 3 }]), 7);
  assert.equal(confirmedEnrollmentCount(null), 0);
  const result = upcomingTeachingSessions([
    { classSessionId: 8, startTime: "2099-02-02T09:00:00", sessionStatus: "SCHEDULED" },
    { classSessionId: 9, startTime: "2099-02-02T09:00:00", sessionStatus: "SCHEDULED" },
    { classSessionId: 3, startTime: "2099-02-01T09:00:00", sessionStatus: "CANCELLED" },
    { classSessionId: 4, startTime: "2099-02-01T10:00:00", sessionStatus: "COMPLETED" },
    { classSessionId: 2, startTime: "2000-01-01T09:00:00", sessionStatus: "SCHEDULED" },
  ], new Date("2099-01-01T00:00:00").getTime());
  assert.deepEqual(result.map((item) => item.classSessionId), [8, 9]);
});

test("Coach Dashboard uses coach-scoped class, session and PT APIs with per-source error and empty states", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
  assert.match(dashboard, /COACH: \{[\s\S]*getAssignedPtBookingsApi,[\s\S]*getTrainingClassesApi,[\s\S]*getCoachAttendanceSessionsApi/);
  assert.match(api, /request\("\/api\/training\/classes"/);
  assert.match(api, /request\("\/api\/attendance\/coach\/sessions"/);
  assert.match(dashboard, /errors\.classes/);
  assert.match(dashboard, /errors\.teachingSessions/);
  assert.match(dashboard, /errors\.bookings/);
  assert.match(dashboard, /Lượt đăng ký lớp đã xác nhận/);
  assert.match(dashboard, /Chưa có lớp nào được phân công/);
  assert.match(dashboard, /Không có buổi dạy sắp tới/);
});

test("Coach Dashboard includes only pending assessments and presents the assignment IDs when names are unavailable", () => {
  const assignments = [
    { id: 2, memberId: 7, sportId: 1, assignedCoachId: 4, status: "PENDING" },
    { id: 1, memberId: 7, sportId: 1, assignedCoachId: 4, status: "COMPLETED" },
    { id: 3, memberId: 8, sportId: 2, assignedCoachId: 4, status: "CANCELLED" },
  ];
  const pending = pendingCoachAssessments(assignments);
  assert.deepEqual(pending.map((item) => item.id), [2]);
  assert.deepEqual(coachAssessmentSummary(pending[0]), {
    member: "Học viên #7",
    sport: "Bộ môn #1",
    status: "PENDING",
    assignment: "Được giao cho Coach #4",
  });
  assert.equal(coachAssessmentSummary({ ...pending[0], memberName: "Demo Member" }, [{ id: 1, name: "Badminton" }]).member, "Demo Member");
  assert.equal(coachAssessmentSummary(pending[0], [{ id: 1, name: "Badminton" }]).sport, "Badminton");
  assert.deepEqual(pendingCoachAssessments([]), []);
});

test("Coach Dashboard uses available names, stays Coach-scoped, and links to the existing assessment page", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const coachLoader = dashboard.slice(dashboard.indexOf("  COACH: {"), dashboard.indexOf("  MEMBER: {"));
  assert.match(coachLoader, /assessments: getMySkillAssessmentAssignmentsApi/);
  assert.match(coachLoader, /sports: getSportsApi/);
  assert.doesNotMatch(coachLoader, /getConsultationRequestsApi|consultations/);
  assert.match(dashboard, /coachAssessmentSummary\(a, sports\)/);
  assert.match(dashboard, /to="\/coach\/skill-assessments"/);
  assert.match(app, /"coach-assessments": "\/coach\/skill-assessments"/);
});
