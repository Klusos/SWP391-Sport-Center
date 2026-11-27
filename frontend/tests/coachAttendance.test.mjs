import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { attendanceRowKey, isCurrentAttendanceRosterRequest } from "../src/attendanceRequests.js";
import {
  attendanceClassOptions,
  attendanceSessionsForClass,
  paginateAttendanceSessions,
} from "../src/coachAttendanceView.js";

const sourceUrl = new URL("../src/pages/CoachAttendance.jsx", import.meta.url);

test("Coach attendance stores a selected status under the session-scoped row key", async () => {
  const source = await readFile(sourceUrl, "utf8");

  assert.match(
    source,
    /setRowValue\(setSelectedStatuses,\s*rowKey,\s*event\.target\.value\)/,
  );
  assert.doesNotMatch(
    source,
    /setRowValue\(setSelectedStatuses,\s*memberId,\s*event\.target\.value\)/,
  );
});

function rosterRequest(requestId, sessionId, latestRequestId, selectedSessionId, extra = {}) {
  return isCurrentAttendanceRosterRequest({
    requestId,
    latestRequestId,
    sessionId,
    selectedSessionId,
    mounted: true,
    ...extra,
  });
}

test("late save reload for Session A is ignored after switching to Session B", () => {
  assert.equal(rosterRequest(4, 10, 4, 11), false);
});

test("fast Session B roster wins over an earlier slow Session A request", () => {
  assert.equal(rosterRequest(5, 10, 6, 11), false);
  assert.equal(rosterRequest(6, 11, 6, 11), true);
});

test("older roster request for the same session cannot overwrite the newer response", () => {
  assert.equal(rosterRequest(7, 11, 8, 11), false);
  assert.equal(rosterRequest(8, 11, 8, 11), true);
});

test("a failed save remains keyed to its original session and does not qualify as current roster data", () => {
  const rowErrors = { [attendanceRowKey(10, 42)]: "Network unavailable" };
  assert.equal(rowErrors[attendanceRowKey(10, 42)], "Network unavailable");
  assert.equal(rowErrors[attendanceRowKey(11, 42)], undefined);
  assert.equal(rosterRequest(9, 10, 10, 11), false);
});

test("normal save reload for the still-selected session is accepted", () => {
  assert.equal(rosterRequest(12, 11, 12, 11), true);
});

test("unmounted component and invalidated effect responses are ignored", () => {
  assert.equal(rosterRequest(13, 11, 13, 11, { mounted: false }), false);
  assert.equal(rosterRequest(13, 11, 13, 11, { effectActive: false }), false);
});

test("class-first filtering derives class choices and ordered sessions from assigned API rows", () => {
  const sessions = [
    { classSessionId: 12, courseClassId: 3, courseClassName: "CV03", courseName: "Badminton", startTime: "2026-11-03T10:00:00" },
    { classSessionId: 11, courseClassId: 3, courseClassName: "CV03", courseName: "Badminton", startTime: "2026-11-03T08:00:00" },
    { classSessionId: 21, courseClassId: 4, courseClassName: "CV04", courseName: "Tennis", startTime: "2026-11-04T08:00:00" },
  ];
  const options = attendanceClassOptions(sessions);
  assert.equal(options.length, 2);
  assert.equal(options.find((item) => item.key === "id:3").sessionCount, 2);
  assert.deepEqual(attendanceSessionsForClass(sessions, "id:3").map((item) => item.classSessionId), [11, 12]);
  assert.deepEqual(attendanceSessionsForClass(sessions, ""), []);
});

test("session pagination keeps small classes on one page and preserves ordering across pages", () => {
  const twoSessions = [{ classSessionId: 5 }, { classSessionId: 6 }];
  assert.deepEqual(paginateAttendanceSessions(twoSessions, 1), {
    items: twoSessions,
    page: 1,
    pageSize: 8,
    total: 2,
    totalPages: 1,
  });

  const tenSessions = Array.from({ length: 10 }, (_, index) => ({ classSessionId: index + 1 }));
  assert.deepEqual(paginateAttendanceSessions(tenSessions, 1).items.map((session) => session.classSessionId), [1, 2, 3, 4, 5, 6, 7, 8]);
  assert.deepEqual(paginateAttendanceSessions(tenSessions, 2).items.map((session) => session.classSessionId), [9, 10]);
  assert.equal(paginateAttendanceSessions(tenSessions, 20).page, 2);
});

test("Coach page keeps backend attendance contracts and backend-controlled edit eligibility", async () => {
  const source = await readFile(sourceUrl, "utf8");
  for (const api of ["getCoachAttendanceSessionsApi", "getAttendanceRosterApi", "markAttendanceApi", "getAttendanceHistoryApi"]) {
    assert.match(source, new RegExp(api));
  }
  assert.match(source, /roster\.markingAllowed === true/);
  assert.match(source, /disabled=\{!roster\.markingAllowed \|\| sessionCancelled/);
  assert.match(source, /sessionStatus \|\| selectedSession\?\.sessionStatus/);
  assert.match(source, /roster\?\.sessionStatus \|\| selectedSession\?\.sessionStatus/);
  assert.match(source, /getAttendanceHistoryApi\(token, attendanceId\)/);
  assert.match(source, /if \(correcting && !reason\)/);
  assert.match(source, /if \(correcting && !window\.confirm/);
  assert.doesNotMatch(source, /setSessions\(\(current\).*sessionStatus:\s*"COMPLETED"/s);
  assert.doesNotMatch(source, /selectedSessionIsToday|selectedSessionStarted|markSessionCompletedLocally/);
});

test("class filter controls responsive session selection and cancelled session state without removing keyboard focus", async () => {
  const [source, css] = await Promise.all([
    readFile(sourceUrl, "utf8"),
    readFile(new URL("../src/pages/CoachAttendance.css", import.meta.url), "utf8"),
  ]);
  assert.match(source, /className="coach-attendance-class-filter"/);
  assert.match(source, /<span>Chọn lớp<\/span>/);
  assert.match(source, /onChange=\{\(event\) => chooseClass\(event\.target\.value\)\}/);
  assert.match(source, /sessionPageData\.items\.map\(\(session\)/);
  assert.match(source, /aria-pressed=\{active\}/);
  assert.match(source, /sessionCancelled/);
  assert.match(css, /@media \(max-width: 760px\)/);
  assert.match(css, /:focus-visible/);
});

test("Coach Attendance exposes scoped summaries, search, bulk marking, and per-member partial failures", async () => {
  const [source, css] = await Promise.all([
    readFile(sourceUrl, "utf8"),
    readFile(new URL("../src/pages/CoachAttendance.css", import.meta.url), "utf8"),
  ]);
  assert.match(source, /members\.reduce\(/);
  assert.match(source, /visibleMembers/);
  assert.match(source, /function markAllPresent\(\)/);
  assert.match(source, /function saveAll\(\)/);
  assert.match(source, /submitInitialAttendanceApi\(token, sessionId/);
  assert.match(source, /G\u1eedi \u0111i\u1ec3m danh/);
  assert.doesNotMatch(source, /ch\u00ednh th\u1ee9c/);
  assert.match(source, /showActionColumn = !initialSubmission/);
  assert.doesNotMatch(source, /L\u01b0u \u0111i\u1ec3m danh/);
  assert.match(source, /attendanceWorkRows = sessions\.filter\(\(session\) => \["NOT_RECORDED", "PARTIAL"\]/);
  assert.match(source, /attendanceSessionStatusLabel\(session\)/);
  assert.match(source, /Promise\.all\(\[loadRoster\(sessionId\), loadSessions\(\)\]\)/);
  assert.match(source, /attendanceCompleteness/);
  assert.match(source, /attendanceStatusPresentation/);
  assert.match(source, /Buổi học cần điểm danh/);
  assert.match(source, /for \(const member of batch\)/);
  assert.match(source, /setRowErrors\(\(current\) => \(\{ \.\.\.current, \[rowKey\]: errorMessage/);
  assert.match(source, /if \(correctionCount > 0 && !window\.confirm/);
  assert.match(source, /savingAll \? "Đang gửi/);
  assert.match(source, /coach-attendance-stats/);
  assert.match(source, /Thống kê tính theo lựa chọn hiện tại/);
  assert.match(source, /Thống kê phản ánh điểm danh đã lưu/);
  assert.match(source, /Thống kê tính theo lựa chọn hiện tại/);
  assert.match(source, /Thống kê phản ánh điểm danh đã lưu/);
  assert.match(source, /Quay lại danh sách/);
  assert.match(source, /sessionPageData\.items\.map/);
  assert.match(css, /coach-attendance-session-pagination/);
  assert.match(css, /coach-attendance-detail-view/);
  assert.match(css, /@media \(max-width: 1000px\)/);
});

test("Coach Attendance aligns search and action controls by their field edges", async () => {
  const css = await readFile(new URL("../src/pages/CoachAttendance.css", import.meta.url), "utf8");
  assert.match(css, /\.coach-attendance-toolbar \{[^}]*align-items: end/);
  assert.match(css, /\.coach-attendance-search \{ box-sizing: border-box; width: 100%; height: 44px; min-height: 44px; \}/);
  assert.match(css, /\.coach-attendance-toolbar-actions \{ align-items:center; align-self:end; \}/);
  assert.match(css, /height:44px; min-height:44px/);
});

test("row validation errors render below the full row and cannot resize the action cell", async () => {
  const [coach, staff, css] = await Promise.all([
    readFile(sourceUrl, "utf8"),
    readFile(new URL("../src/pages/StaffAttendance.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/CoachAttendance.css", import.meta.url), "utf8"),
  ]);
  for (const source of [coach, staff]) {
    assert.match(source, /coach-attendance-error-row"><td colSpan=\{showActionColumn \? 5 : 4\}><small className="coach-attendance-row-error" role="alert">\{rowErrors\[rowKey\]/);
    assert.doesNotMatch(source, /<td>[\s\S]{0,800}\{rowErrors\[rowKey\] && <small className="coach-attendance-row-error"/);
  }
  assert.match(css, /coach-attendance-table th:last-child, \.coach-attendance-table td:last-child \{ text-align: right; white-space: nowrap; \}/);
  assert.match(css, /\.coach-attendance-error-row td \{[^{]*text-align: left !important/);
});

test("Coach and Admin attendance action controls share a right-aligned column before and after row errors", async () => {
  const [coach, staff, css] = await Promise.all([
    readFile(sourceUrl, "utf8"),
    readFile(new URL("../src/pages/StaffAttendance.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/CoachAttendance.css", import.meta.url), "utf8"),
  ]);
  for (const source of [coach, staff]) assert.match(source, /className="coach-attendance-row-actions"/);
  assert.match(css, /\.coach-attendance-row-actions \{ display: flex; flex-direction: column; align-items: flex-end; gap: 7px; \}/);
  assert.match(css, /\.coach-attendance-row-actions > button \{[^}]*width: 100%;[^}]*max-width: 100%;[^}]*margin: 0;/);
});
