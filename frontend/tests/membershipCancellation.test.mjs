import test from "node:test";
import assert from "node:assert/strict";
import {
  confirmRefundApi,
  decideCancellationRequestApi,
  getCancellationRequestsApi,
  getRefundRequiredPaymentsApi,
  requestMembershipCancellationApi,
  withdrawCancellationRequestApi,
} from "../src/api.js";
import {
  canRequestCancellation,
  cancellationRequestErrorMessage,
  formatDate,
  formatDateTime,
  lastRequestDay,
  paymentStatusLabel,
} from "../src/membershipCancellation.js";

test("requests close 72 hours before 00:00 of the start date", () => {
  // Starts 2026-11-15 -> closes at 00:00 on 2026-11-12, so 2026-11-11 is the last day.
  assert.equal(lastRequestDay("2026-11-15"), "2026-11-11");
  assert.equal(lastRequestDay("2026-03-02"), "2026-02-26");
  const upcoming = { startDate: "2026-11-15" };
  assert.equal(canRequestCancellation(upcoming, "2026-11-11"), true);
  assert.equal(canRequestCancellation(upcoming, "2026-11-12"), false);
  assert.equal(canRequestCancellation({ startDate: "2026-10-15" }, "2026-10-27"), false);
});

test("dates, statuses and backend errors are shown in Vietnamese", () => {
  assert.equal(formatDate("2026-11-15"), "15/11/2026");
  assert.equal(formatDateTime("2026-10-27T15:04:05.123"), "15:04 27/10/2026");
  assert.equal(formatDate(null), "—");
  assert.equal(paymentStatusLabel.REFUNDED, "Đã hoàn tiền");
  assert.equal(paymentStatusLabel.REFUND_REQUIRED, "Chờ hoàn tiền");
  assert.match(cancellationRequestErrorMessage({ status: 409, message: "Cancellation requests close 72 hours before the membership starts (00:00 12/11/2026)" }), /quá hạn/);
  assert.match(cancellationRequestErrorMessage({ status: 409, message: "This membership already has a pending cancellation request" }), /đang chờ duyệt/);
  assert.match(cancellationRequestErrorMessage({ status: 400, message: "A note is required" }), /lý do/);
});

test("the cancellation request and refund calls hit the backend endpoints", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => {
    calls.push([options.method || "GET", url, options.body ? JSON.parse(options.body) : undefined]);
    return new Response("{}", { status: 200 });
  };
  try {
    await requestMembershipCancellationApi("t", 5, "Chuyển nhà");
    await withdrawCancellationRequestApi("t", 8);
    await getCancellationRequestsApi("t");
    await decideCancellationRequestApi("t", 8, true);
    await decideCancellationRequestApi("t", 8, false, "Không đủ điều kiện");
    await getRefundRequiredPaymentsApi("t");
    await confirmRefundApi("t", 12, "Tiền mặt");
  } finally {
    globalThis.fetch = original;
  }
  const base = "http://localhost:8080/api";
  assert.deepEqual(calls, [
    ["POST", `${base}/memberships/cancellation-requests`, { membershipId: 5, reason: "Chuyển nhà" }],
    ["PATCH", `${base}/memberships/cancellation-requests/8/withdraw`, undefined],
    ["GET", `${base}/memberships/cancellation-requests?status=PENDING`, undefined],
    ["PATCH", `${base}/memberships/cancellation-requests/8/approve`, { note: null }],
    ["PATCH", `${base}/memberships/cancellation-requests/8/reject`, { note: "Không đủ điều kiện" }],
    ["GET", `${base}/payments/refund-required?page=0&size=20`, undefined],
    ["PATCH", `${base}/payments/12/refund`, { note: "Tiền mặt" }],
  ]);
});
