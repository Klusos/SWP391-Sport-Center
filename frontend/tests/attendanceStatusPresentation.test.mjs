import assert from "node:assert/strict";
import test from "node:test";
import { attendanceSessionStatusLabel } from "../src/attendanceStatusPresentation.js";

test("attendance labels prioritize cancellation and authoritative future eligibility", () => {
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "CANCELLED", attendanceTimeEligible: false, attendanceCompleteness: "NOT_APPLICABLE" }), "");
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "SCHEDULED", attendanceTimeEligible: false, attendanceCompleteness: "NOT_APPLICABLE", eligibleStudentCount: 0 }), "Chưa đến giờ điểm danh");
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "SCHEDULED", attendanceTimeEligible: false, attendanceCompleteness: "NOT_YET_ELIGIBLE", eligibleStudentCount: 2 }), "Chưa đến giờ điểm danh");
});

test("attendance labels distinguish empty elapsed rosters from persisted completeness", () => {
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "COMPLETED", attendanceTimeEligible: true, attendanceCompleteness: "NOT_APPLICABLE", eligibleStudentCount: 0 }), "Không có học viên");
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "COMPLETED", attendanceTimeEligible: true, attendanceCompleteness: "NOT_RECORDED", eligibleStudentCount: 2, recordedAttendanceCount: 0 }), "Chưa điểm danh");
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "COMPLETED", attendanceTimeEligible: true, attendanceCompleteness: "PARTIAL", eligibleStudentCount: 2, recordedAttendanceCount: 1 }), "Chưa hoàn tất");
  assert.equal(attendanceSessionStatusLabel({ sessionStatus: "COMPLETED", attendanceTimeEligible: true, attendanceCompleteness: "COMPLETE", eligibleStudentCount: 2, recordedAttendanceCount: 2 }), "Đã điểm danh");
});
