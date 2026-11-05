import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { attendanceRowKey, isCurrentAttendanceRosterRequest } from "../src/attendanceRequests.js";
import { attendanceClassOptions, attendanceSessionsForClass } from "../src/coachAttendanceView.js";

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
  assert.match(source, /onChange=\{\(event\) => chooseClass\(event\.target\.value\)\}/);
  assert.match(source, /filteredSessions\.map\(\(session\)/);
  assert.match(source, /aria-pressed=\{active\}/);
  assert.match(source, /sessionCancelled/);
  assert.match(css, /@media \(max-width: 760px\)/);
  assert.match(css, /:focus-visible/);
});
