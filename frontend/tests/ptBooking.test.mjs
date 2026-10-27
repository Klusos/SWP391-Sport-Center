import test from "node:test";
import assert from "node:assert/strict";
import { cancelPtBookingApi, getPendingPtPaymentsApi } from "../src/api.js";
import { canMemberCancel, holdActive, minutesLeft, pendingPtPayment, ptErrorMessage, ptStatusLabel, vietnamInstant } from "../src/ptBooking.js";

const at = (iso) => new Date(iso).getTime();

test("backend times without an offset are read as Vietnam time", () => {
  assert.equal(vietnamInstant("2026-11-20T09:00:00").toISOString(), "2026-11-20T02:00:00.000Z");
  assert.equal(vietnamInstant("2026-11-20T02:00:00Z").toISOString(), "2026-11-20T02:00:00.000Z");
  assert.equal(vietnamInstant(null), null);
});

test("the payment hold is active only for pending bookings before it expires", () => {
  const booking = { status: "PENDING_PAYMENT", paymentExpiresAt: "2026-10-27T16:42:00" };
  assert.equal(holdActive(booking, at("2026-10-27T16:30:00+07:00")), true);
  assert.equal(minutesLeft(booking, at("2026-10-27T16:30:00+07:00")), 12);
  assert.equal(holdActive(booking, at("2026-10-27T16:43:00+07:00")), false);
  assert.equal(holdActive({ ...booking, status: "CONFIRMED" }, at("2026-10-27T16:30:00+07:00")), false);
});

test("members cancel pending bookings any time and confirmed ones until 24 hours before", () => {
  const confirmed = { status: "CONFIRMED", startTime: "2026-11-20T09:00:00" };
  assert.equal(canMemberCancel({ status: "PENDING_PAYMENT" }), true);
  assert.equal(canMemberCancel(confirmed, at("2026-11-19T08:59:00+07:00")), true);
  assert.equal(canMemberCancel(confirmed, at("2026-11-19T09:01:00+07:00")), false);
  assert.equal(canMemberCancel({ status: "EXPIRED", startTime: "2026-11-20T09:00:00" }, 0), false);
});

test("only the pending PT payment of that booking is picked", () => {
  const payments = [
    { id: 1, purpose: "PT_BOOKING", referenceId: 7, status: "CANCELLED" },
    { id: 2, purpose: "COURSE_REGISTRATION", referenceId: 7, status: "PENDING" },
    { id: 3, purpose: "PT_BOOKING", referenceId: 7, status: "PENDING", method: "COUNTER" },
  ];
  assert.equal(pendingPtPayment(payments, 7).id, 3);
  assert.equal(pendingPtPayment(payments, 8), null);
});

test("statuses and backend errors are shown in Vietnamese", () => {
  assert.equal(ptStatusLabel.EXPIRED, "Hết hạn giữ chỗ");
  assert.match(ptErrorMessage({ status: 409, message: "Confirmed PT sessions can be cancelled up to 24 hours before they start; contact the front desk" }), /24 giờ/);
  assert.match(ptErrorMessage({ status: 409, message: "The selected time conflicts with a coach class or an existing PT booking" }), /trùng lịch/);
  assert.match(ptErrorMessage({ status: 403, message: "An ACTIVE membership with 1-on-1 coaching eligibility is required on the session date" }), /PT 1-1/);
});

test("the cancel and counter-queue calls hit the backend endpoints", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => { calls.push([options.method || "GET", url]); return new Response("{}", { status: 200 }); };
  try {
    await cancelPtBookingApi("t", 11);
    await getPendingPtPaymentsApi("t");
  } finally {
    globalThis.fetch = original;
  }
  assert.deepEqual(calls, [
    ["PATCH", "http://localhost:8080/api/pt-bookings/11/cancel"],
    ["GET", "http://localhost:8080/api/payments/pending-pt?page=0&size=20"],
  ]);
});

test("staff reach the PT payment queue and VNPay results for PT go back to the PT page", async () => {
  const { readFile } = await import("node:fs/promises");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const sidebar = await readFile(new URL("../src/components/Sidebar.jsx", import.meta.url), "utf8");
  const checkout = await readFile(new URL("../src/pages/CourseCheckout.jsx", import.meta.url), "utf8");
  assert.ok(app.includes('path="/receptionist/pt-payments"'));
  assert.ok(app.indexOf('path="/receptionist/pt-payments"') > app.indexOf("<Route element={<StaffRoute auth={auth} />}>"));
  assert.equal((sidebar.match(/\["pt-payments", "Thanh toán buổi PT"\]/g) || []).length, 2);
  assert.ok(checkout.includes('targetPayment?.purpose === "PT_BOOKING"'));
});
