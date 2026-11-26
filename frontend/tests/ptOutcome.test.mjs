import test from "node:test";
import assert from "node:assert/strict";
import { correctAdminPtBookingOutcomeApi, getAdminPtBookingOutcomeApi, recordPtBookingOutcomeApi } from "../src/api.js";
import { canMemberCancel, ptBookingOnCalendar, ptStatusLabel, upcomingPtBookings } from "../src/ptBooking.js";

test("PT outcomes have accurate Member labels and do not become cancellable", () => {
  assert.equal(ptStatusLabel.COMPLETED, "Đã hoàn thành");
  assert.match(ptStatusLabel.NO_SHOW, /buổi đã sử dụng/);
  assert.equal(canMemberCancel({ status: "COMPLETED" }), false);
  assert.equal(canMemberCancel({ status: "NO_SHOW" }), false);
  assert.doesNotMatch(ptStatusLabel.NO_SHOW, /hoàn tiền|refund/i);
});

test("completed and no-show sessions remain on their historical calendar date, not upcoming", () => {
  const now = new Date("2026-11-24T12:00:00+07:00").getTime();
  for (const status of ["COMPLETED", "NO_SHOW"]) {
    const booking = { id: status, status, startTime: "2026-11-20T10:00:00", endTime: "2026-11-20T11:30:00" };
    assert.equal(ptBookingOnCalendar(booking, now), true);
    assert.equal(upcomingPtBookings([booking], now).length, 0);
  }
});

test("Coach and Admin outcome APIs use only their scoped endpoints and send requested data", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ method: options.method || "GET", url, body: options.body });
    return new Response('{"id":7,"status":"COMPLETED"}', { status: 200 });
  };
  try {
    await recordPtBookingOutcomeApi("test", 7, "COMPLETED");
    await getAdminPtBookingOutcomeApi("test", 7);
    await correctAdminPtBookingOutcomeApi("test", 7, "NO_SHOW", "Verified attendance record");
  } finally {
    globalThis.fetch = original;
  }
  assert.deepEqual(calls.map(({ method, url }) => [method, url]), [
    ["PATCH", "http://localhost:8080/api/pt-bookings/7/outcome"],
    ["GET", "http://localhost:8080/api/admin/pt-bookings/7/outcome"],
    ["PATCH", "http://localhost:8080/api/admin/pt-bookings/7/outcome"],
  ]);
  assert.deepEqual(JSON.parse(calls[0].body), { outcome: "COMPLETED" });
  assert.deepEqual(JSON.parse(calls[2].body), { outcome: "NO_SHOW", reason: "Verified attendance record" });
});

test("Coach action is limited to ended confirmed sessions and Admin UI requires correction reason", async () => {
  const { readFile } = await import("node:fs/promises");
  const coach = await readFile(new URL("../src/pages/CoachPtSessions.jsx", import.meta.url), "utf8");
  const admin = await readFile(new URL("../src/pages/AdminAuditLogs.jsx", import.meta.url), "utf8");
  assert.match(coach, /selectedBooking\.status === "CONFIRMED" && selectedSessionEnded/);
  assert.match(coach, /recordPtBookingOutcomeApi\(token, selectedBooking\.id, outcome\)/);
  assert.match(admin, /ptReason\.trim\(\)\.length === 0/);
  assert.match(admin, /PT_BOOKING_OUTCOME/);
  assert.match(admin, /correctAdminPtBookingOutcomeApi/);
});
