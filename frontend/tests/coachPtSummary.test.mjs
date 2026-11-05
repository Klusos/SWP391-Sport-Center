import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { coachPtStatusSummary } from "../src/coachPtSummary.js";

test("Coach PT summary uses backend booking statuses without inferring completion from time", () => {
  assert.deepEqual(coachPtStatusSummary([
    { status: "CONFIRMED", endTime: "2020-01-01T00:00:00" },
    { status: "PENDING_PAYMENT" },
    { status: "CANCELLED" },
  ]), { total: 3, confirmed: 1, pendingPayment: 1 });
});

test("Coach PT page retains the assigned API and weekly calendar filters", async () => {
  const source = await readFile(new URL("../src/pages/CoachPtSessions.jsx", import.meta.url), "utf8");
  assert.match(source, /getAssignedPtBookingsApi\(token\)/);
  assert.match(source, /coachPtSessionGroups\(rows, days, selectedDay, statusFilter\)/);
  assert.match(source, /role="group" aria-label="Lọc theo trạng thái"/);
  assert.match(source, /aria-pressed=\{statusFilter === value\}/);
  assert.match(source, /coach-pt-summary/);
  assert.doesNotMatch(source, /status:\s*["']COMPLETED["']/);
});
