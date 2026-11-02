import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { filterPtBookingHistory, paginatePtBookingHistory, partitionPtBookings, pendingPtPayment, ptCancellationWarning } from "../src/ptPaymentFlow.js";

const now = Date.parse("2026-10-26T12:00:00+07:00");
const pending = (id, deadline, overrides = {}) => ({
  id, status: "PENDING_PAYMENT", paymentExpiresAt: deadline,
  createdAt: `2026-10-26T10:0${id}:00`, startTime: "2026-10-28T08:00:00", sportName: "Tennis", coachName: "Coach A", ...overrides,
});

test("valid pending bookings are separated from history and ordered by nearest hold deadline", () => {
  const source = [
    { id: 90, status: "CONFIRMED", createdAt: "2026-10-26T11:00:00" },
    pending(3, "2026-10-26T12:08:00"),
    pending(2, "2026-10-26T12:03:00"),
    pending(1, "2026-10-26T11:59:00"),
  ];
  const { actionable, history } = partitionPtBookings(source, now);
  assert.deepEqual(actionable.map((item) => item.id), [2, 3]);
  assert.deepEqual(history.map((item) => item.id), [90, 1]);
});

test("existing COUNTER or ONLINE pending payments are surfaced without allowing duplicate creation", async () => {
  const payments = [
    { id: 11, purpose: "PT_BOOKING", referenceId: 2, method: "COUNTER", status: "PENDING" },
    { id: 12, purpose: "PT_BOOKING", referenceId: 3, method: "ONLINE", status: "PENDING" },
  ];
  assert.equal(pendingPtPayment(payments, 2).method, "COUNTER");
  assert.equal(pendingPtPayment(payments, 3).method, "ONLINE");
  assert.match(ptCancellationWarning(pendingPtPayment(payments, 3)), /does not revoke an existing VNPay checkout URL/);
  const page = await readFile(new URL("../src/pages/PtBooking.jsx", import.meta.url), "utf8");
  assert.match(page, /pending\s*\?\s*<p role="status">Đã có thanh toán \{pending\.method\} đang chờ/);
  assert.match(page, /allowCreate && <div className="pt-payment-choice"/);
  assert.match(page, /Chờ xác nhận tại quầy/);
  assert.match(page, /Đang chờ VNPay xác nhận/);
});

test("confirmed, cancelled, and expired bookings move into history", () => {
  const { actionable, history } = partitionPtBookings([
    pending(1, "2026-10-26T12:09:00"),
    pending(2, "2026-10-26T11:59:00"),
    { id: 3, status: "CONFIRMED", createdAt: "2026-10-26T10:00:00" },
    { id: 4, status: "CANCELLED", createdAt: "2026-10-26T09:00:00" },
    { id: 5, status: "EXPIRED", createdAt: "2026-10-26T08:00:00" },
  ], now);
  assert.deepEqual(actionable.map((item) => item.id), [1]);
  assert.deepEqual(history.map((item) => item.id), [2, 3, 4, 5]);
});

test("history is newest-first with deterministic ID tie breaking", () => {
  const { history } = partitionPtBookings([
    { id: 7, status: "CONFIRMED", createdAt: "2026-10-25T10:00:00" },
    { id: 9, status: "CANCELLED", createdAt: "2026-10-26T10:00:00" },
    { id: 8, status: "EXPIRED", createdAt: "2026-10-26T10:00:00" },
  ], now);
  assert.deepEqual(history.map((item) => item.id), [9, 8, 7]);
});

test("history supports status and sport/coach search filters", () => {
  const items = [
    { id: 1, status: "CONFIRMED", sportName: "Tennis", coachName: "Coach Lan" },
    { id: 2, status: "CANCELLED", sportName: "Yoga", coachName: "Coach Minh" },
  ];
  assert.deepEqual(filterPtBookingHistory(items, "CONFIRMED").map((item) => item.id), [1]);
  assert.deepEqual(filterPtBookingHistory(items, "ALL", "minh").map((item) => item.id), [2]);
  assert.deepEqual(filterPtBookingHistory(items, "CANCELLED", "yoga").map((item) => item.id), [2]);
  assert.deepEqual(filterPtBookingHistory(items, "EXPIRED", "missing"), []);
});

test("history pagination uses five rows, reports totals, and returns an empty state cleanly", () => {
  const records = Array.from({ length: 12 }, (_, index) => ({ id: index + 1 }));
  const first = paginatePtBookingHistory(records, 0, 5);
  const second = paginatePtBookingHistory(records, 1, 5);
  const last = paginatePtBookingHistory(records, 99, 5);
  const empty = paginatePtBookingHistory([], 0, 5);
  assert.deepEqual(first.items.map((item) => item.id), [1, 2, 3, 4, 5]);
  assert.deepEqual(second.items.map((item) => item.id), [6, 7, 8, 9, 10]);
  assert.deepEqual(last.items.map((item) => item.id), [11, 12]);
  assert.equal(first.totalPages, 3);
  assert.equal(empty.totalPages, 0);
  assert.deepEqual(empty.items, []);
});

test("the page uses the full list response, separate action/history sections, and retains modal success handling", async () => {
  const [api, page, modal] = await Promise.all([
    readFile(new URL("../src/api.js", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/PtBooking.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/components/PaymentMethodModal.jsx", import.meta.url), "utf8"),
  ]);
  assert.match(api, /export function getMyPtBookingsApi\(token\)[\s\S]*?request\("\/api\/pt-bookings\/me"/);
  assert.match(page, /Cần xử lý/);
  assert.match(page, /Lịch sử Booking/);
  assert.match(page, /historyPageData\.items\.map/);
  assert.match(page, /onSuccess=\{\(\) => setPaymentModalBooking\(null\)\}/);
  assert.match(modal, /if \(closeAfterContinue === true\) onSuccess\?\.\(\)/);
});
