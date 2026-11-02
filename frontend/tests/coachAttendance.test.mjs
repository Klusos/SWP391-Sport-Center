import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { attendanceRowKey, isCurrentAttendanceRosterRequest } from "../src/attendanceRequests.js";

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
