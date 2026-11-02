import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  coachPtDayCounts,
  coachPtSessionGroups,
  coachPtStatusClass,
  coachPtWeekDays,
  vietnamDateKey,
} from "../src/coachPtCalendar.js";

const booking = (id, startTime, status = "CONFIRMED") => ({
  id,
  startTime,
  endTime: startTime,
  status,
  memberName: `Member ${id}`,
});

test("weekly calendar starts Monday, ends Sunday, and navigates in seven-day steps", () => {
  const now = new Date("2026-11-01T16:30:00Z"); // Sunday night in Vietnam
  const current = coachPtWeekDays(now);
  const previous = coachPtWeekDays(now, -1);
  const next = coachPtWeekDays(now, 1);

  assert.deepEqual(current.map((day) => day.key), [
    "2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01",
  ]);
  assert.equal(previous[0].key, "2026-10-19");
  assert.equal(next[0].key, "2026-11-02");
});

test("Vietnam date grouping follows the existing LocalDateTime +07 convention", () => {
  assert.equal(vietnamDateKey("2026-11-01T23:30:00"), "2026-11-01");
  assert.equal(vietnamDateKey("2026-11-01T17:30:00Z"), "2026-11-02");
});

test("day selection filters sessions and null selection restores the entire week", () => {
  const days = coachPtWeekDays(new Date("2026-11-01T16:30:00Z"));
  const rows = [booking(1, "2026-10-26T08:00:00"), booking(2, "2026-10-27T09:00:00")];
  assert.deepEqual(coachPtSessionGroups(rows, days, "2026-10-27").flatMap((group) => group.sessions.map((item) => item.id)), [2]);
  assert.deepEqual(coachPtSessionGroups(rows, days, null).flatMap((group) => group.sessions.map((item) => item.id)), [1, 2]);
});

test("status filtering is display-only and pending payment retains its own visual treatment", () => {
  const days = coachPtWeekDays(new Date("2026-11-01T16:30:00Z"));
  const rows = [
    booking(1, "2026-10-27T08:00:00", "PENDING_PAYMENT"),
    booking(2, "2026-10-27T09:00:00", "CONFIRMED"),
    booking(3, "2026-10-27T10:00:00", "CANCELLED"),
  ];
  assert.deepEqual(coachPtSessionGroups(rows, days, null, "PENDING_PAYMENT").flatMap((group) => group.sessions.map((item) => item.id)), [1]);
  assert.deepEqual(coachPtSessionGroups(rows, days).flatMap((group) => group.sessions.map((item) => item.status)), ["PENDING_PAYMENT", "CONFIRMED", "CANCELLED"]);
  assert.equal(coachPtStatusClass("PENDING_PAYMENT"), "pending-payment");
  assert.equal(coachPtStatusClass("CONFIRMED"), "confirmed");
  assert.deepEqual(coachPtDayCounts(rows, days, "ALL")["2026-10-27"], 3);
});

test("sessions are ordered chronologically within date groups with stable ID tie-breaking", () => {
  const days = coachPtWeekDays(new Date("2026-11-01T16:30:00Z"));
  const rows = [
    booking(5, "2026-10-27T11:00:00"),
    booking(8, "2026-10-27T09:00:00"),
    booking(2, "2026-10-27T09:00:00"),
    booking(1, "2026-10-26T18:00:00"),
  ];
  const groups = coachPtSessionGroups(rows, days);
  assert.deepEqual(groups.map((group) => group.key), ["2026-10-26", "2026-10-27"]);
  assert.deepEqual(groups.flatMap((group) => group.sessions.map((item) => item.id)), [1, 2, 8, 5]);
});

test("empty weeks return no groups and detail controls preserve a read-only details dialog", async () => {
  const days = coachPtWeekDays(new Date("2026-11-01T16:30:00Z"));
  assert.deepEqual(coachPtSessionGroups([], days), []);
  const page = await readFile(new URL("../src/pages/CoachPtSessions.jsx", import.meta.url), "utf8");
  assert.match(page, /setSelectedBooking\(booking\)/);
  assert.match(page, /role="dialog" aria-modal="true"/);
  assert.match(page, /selectedBooking\.memberName/);
});
