import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { getPendingPtPaymentsApi, createPaymentApi, cancelPaymentApi } from "../src/api.js";
import { acquirePtPaymentAction, canViewPtPayments, createPtCounterQueue, pendingPtPayment, ptCancellationWarning, ptOnlineUrlIsTrusted, ptPaymentExpired, ptPaymentPayload } from "../src/ptPaymentFlow.js";

const booking = { id: 18, status: "PENDING_PAYMENT", paymentExpiresAt: "2099-10-26T18:00:00" };
const payment = (status = "PENDING", method = "ONLINE") => ({ id: 81, memberId: 7, purpose: "PT_BOOKING", referenceId: 18, status, method, amount: 299000 });
const trustedUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Command=pay&vnp_SecureHash=abc";

test("PT payment helpers call the exact API contracts", async () => {
  const original = globalThis.fetch, calls = [];
  globalThis.fetch = async (url, options) => { calls.push({ url: new URL(url), options }); return new Response(JSON.stringify({ content: [] }), { status: 200 }); };
  try {
    await getPendingPtPaymentsApi("token", 2, 20);
    await createPaymentApi("token", ptPaymentPayload(18, "ONLINE"));
    await cancelPaymentApi("token", 81);
    assert.equal(calls[0].url.pathname, "/api/payments/pending-pt");
    assert.equal(calls[0].url.search, "?page=2&size=20");
    assert.deepEqual(JSON.parse(calls[1].options.body), { purpose: "PT_BOOKING", referenceId: 18, method: "ONLINE" });
    assert.equal(calls[2].url.pathname, "/api/payments/81/cancel");
    assert.equal(calls[2].options.method, "PATCH");
  } finally { globalThis.fetch = original; }
});

test("ONLINE and COUNTER payloads are explicit and a pending payment blocks another", () => {
  assert.deepEqual(ptPaymentPayload(18, "ONLINE"), { purpose: "PT_BOOKING", referenceId: 18, method: "ONLINE" });
  assert.deepEqual(ptPaymentPayload(18, "COUNTER"), { purpose: "PT_BOOKING", referenceId: 18, method: "COUNTER" });
  assert.throws(() => ptPaymentPayload(0, "ONLINE"));
  assert.equal(pendingPtPayment([payment()], 18).id, 81);
  assert.equal(pendingPtPayment([{ ...payment(), purpose: "MEMBERSHIP" }], 18), undefined);
});

test("member payment lock rejects repeated clicks synchronously", () => {
  const lock = { current: false };
  assert.equal(acquirePtPaymentAction(lock), true);
  assert.equal(acquirePtPaymentAction(lock), false);
  lock.current = false;
  assert.equal(acquirePtPaymentAction(lock), true);
});

test("trusted VNPay URL policy rejects non-sandbox or malformed URLs", () => {
  assert.equal(ptOnlineUrlIsTrusted(trustedUrl), true);
  for (const url of ["http://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "https://evil.example/paymentv2/vpcpay.html?vnp_Command=pay&vnp_SecureHash=x", "https://sandbox.vnpayment.vn/"]) assert.equal(ptOnlineUrlIsTrusted(url), false);
});

test("online cancellation warning explains checkout URL and possible refund", () => {
  assert.match(ptCancellationWarning(payment("PENDING", "ONLINE")), /does not revoke an existing VNPay checkout URL.*may require a refund/i);
  assert.doesNotMatch(ptCancellationWarning(payment("PENDING", "COUNTER")), /VNPay/);
});

test("expired booking holds disable further payments", async () => {
  assert.equal(ptPaymentExpired({ paymentExpiresAt: "2020-01-01T00:00:00" }, Date.now()), true);
  assert.equal(ptPaymentExpired(booking, Date.now()), false);
});

test("only Admin and Receptionist may use PT counter queue", () => {
  assert.equal(canViewPtPayments("ADMIN"), true); assert.equal(canViewPtPayments("RECEPTIONIST"), true);
  for (const role of ["MEMBER", "COACH", undefined]) assert.equal(canViewPtPayments(role), false);
});

test("App and Sidebar expose the queue only through the staff area and add both staff navigation entries", async () => {
  const root = new URL("../src/", import.meta.url);
  const app = await readFile(new URL("App.jsx", root), "utf8");
  const sidebar = await readFile(new URL("components/Sidebar.jsx", root), "utf8");
  const page = await readFile(new URL("pages/ReceptionistPtPayments.jsx", root), "utf8");
  assert.match(app, /<Route element={<StaffRoute auth={auth} \/>}>[\s\S]*?<Route path="\/receptionist\/pt-payments"/);
  assert.equal((sidebar.match(/\["pt-payments"/g) || []).length, 2);
  assert.match(page, /canViewPtPayments\(role\)/);
  assert.match(page, /<Navigate to="\/" replace \/>/);
});

test("cash confirmation requires explicit consent and duplicate/uncertain results are not retried", async () => {
  const row = { paymentId: 81, bookingId: 18, memberId: 7, memberName: "Demo Member", sportName: "Tennis", coachName: "Demo Coach", startTime: "2099-10-27T10:00:00", endTime: "2099-10-27T11:30:00", amount: 299000, paymentDeadline: "2099-10-26T18:00:00", paymentStatus: "PENDING", bookingStatus: "PENDING_PAYMENT" };
  let confirmText = "", posts = 0; const uncertainStates = [];
  const queue = createPtCounterQueue({ fetchQueue: async () => ({ content: [row], totalPages: 1, totalElements: 1, last: true }),
    settle: async () => { posts++; throw new Error("network lost"); }, fetchHistory: async () => [payment("PENDING", "COUNTER")],
    confirm: (text) => { confirmText = text; return true; }, onQueue: () => {}, onBusy: () => {}, onOutcome: () => {}, onUncertain: (x) => uncertainStates.push(x) });
  await queue.receive(row); await queue.receive(row);
  assert.match(confirmText, /only confirm if full cash payment was received/i);
  assert.equal(posts, 1); assert.equal(uncertainStates.at(-1)[0].paymentId, 81);
  queue.dispose();
});

test("component handles 409 and refuses automatic duplicate payment attempts", async () => {
  const source = await readFile(new URL("../src/pages/PtBooking.jsx", import.meta.url), "utf8");
  assert.match(source, /requestError\.status === 409/);
  assert.match(source, /if \(pendingPtPayment\(payments, booking\.id\)\)/);
  assert.match(source, /do not create another payment/);
  assert.match(source, /ptOnlineUrlIsTrusted\(payment\.paymentUrl\)/);
});

test("component requires confirmation before canceling and exposes the VNPay warning", async () => {
  const source = await readFile(new URL("../src/pages/PtBooking.jsx", import.meta.url), "utf8");
  assert.match(source, /window\.confirm\(ptCancellationWarning\(payment\)\)/);
  assert.match(source, /await cancelPaymentApi\(token, payment\.id\)/);
});
