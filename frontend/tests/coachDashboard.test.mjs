import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { confirmedEnrollmentCount, upcomingTeachingSessions } from "../src/coachDashboard.js";

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
