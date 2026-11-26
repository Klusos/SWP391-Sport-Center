import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sourceUrl = new URL("../src/pages/StaffAttendance.jsx", import.meta.url);

test("Staff Attendance keeps server APIs, filters, and normal ten-session pagination", async () => {
  const [source, css] = await Promise.all([
    readFile(sourceUrl, "utf8"),
    readFile(new URL("../src/pages/StaffAttendance.css", import.meta.url), "utf8"),
  ]);
  for (const api of ["getStaffAttendanceSessionsApi", "getAttendanceRosterApi", "markAttendanceApi", "getAttendanceHistoryApi"]) {
    assert.match(source, new RegExp(api));
  }
  assert.match(source, /sportFilter/);
  assert.match(source, /memberFilter/);
  assert.match(source, /const PAGE_SIZE = 10/);
  assert.match(source, /pagedMembers/);
  assert.match(source, /pagedSessions/);
  assert.match(source, /attendanceClassOptions\(sessions\)/);
  assert.match(source, /selectedClassKey/);
  assert.match(source, /staff-attendance-admin-navigation/);
  assert.match(source, /Quay lại danh sách lớp/);
  assert.match(source, /Quay lại buổi học/);
  assert.match(source, /is-admin-roster/);
  assert.match(source, /sessionPageCount/);
  assert.match(source, /setSessionPage\(1\)/);
  assert.doesNotMatch(css, /max-height: 60vh;[\s\S]*?overflow-y: auto/);
  assert.match(css, /staff-attendance-session-pager/);
  assert.match(source, /coach-attendance-stats/);
  assert.match(source, /Trạng thái đã lưu/);
  assert.match(source, /buildAttendanceCsv\(rows\)/);
  assert.match(source, /function saveAll\(\)/);
  assert.match(source, /setRowErrors\(\(current\) => \(\{ \.\.\.current, \[rowKey\]: requestError/);
  assert.match(source, /state\.correction && !String\(reasons\[state\.rowKey\]/);
  assert.match(source, /requestId !== rosterRequestRef\.current/);
  assert.match(css, /\.staff-attendance-pager/);
  assert.match(css, /@media \(max-width: 760px\)/);
});
