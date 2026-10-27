import test from "node:test";
import assert from "node:assert/strict";
import { acquireAction, canCheckout, checkoutAccess, checkoutPayload, paymentMessage, positiveId, prepareCheckout, registrationMessage, registrationPayments, shouldPoll, startStatusPolling, validVnpayUrl } from "../src/courseCheckout.js";

const registration = { id: 123, classId: 8, status: "PENDING_PAYMENT", paymentDeadline: "2099-01-01T10:00:00", finalPrice: 500000 };
const pending = { id: 1, purpose: "COURSE_REGISTRATION", referenceId: 123, method: "COUNTER", status: "PENDING" };
const snapshot = (payments = [], row = registration) => ({ registrations: [row], payments });

test("return page requires normal MEMBER authentication before ownership reads", () => {
  assert.equal(checkoutAccess(null), "LOGIN");
  assert.equal(checkoutAccess({ role: "MEMBER" }), "LOGIN");
  assert.equal(checkoutAccess({ role: "COACH", accessToken: "test-token" }), "FORBIDDEN");
  assert.equal(checkoutAccess({ role: "MEMBER", accessToken: "test-token" }), "MEMBER");
});

test("ONLINE uses registration identity and excludes client price/member fields", () => {
  assert.deepEqual(prepareCheckout(snapshot(), 123, "ONLINE").payload, { method: "ONLINE", purpose: "COURSE_REGISTRATION", referenceId: 123 });
});
test("COUNTER uses the same registration without creating another hold", () => {
  assert.deepEqual(checkoutPayload(123, "COUNTER"), { method: "COUNTER", purpose: "COURSE_REGISTRATION", referenceId: 123 });
});
test("synchronous action lock rejects double clicks before any await", () => {
  const lock = { current: false };
  assert.equal(acquireAction(lock), true);
  assert.equal(acquireAction(lock), false);
  lock.current = false;
  assert.equal(acquireAction(lock), true);
});
test("existing counter is reused even when selecting online", () => {
  assert.deepEqual(prepareCheckout(snapshot([pending]), 123, "ONLINE"), { counter: pending });
});
test("redirect SUCCESS cannot override authenticated PENDING_PAYMENT", () => {
  assert.match(registrationMessage({ ...registration, paymentStatus: "SUCCESS" }), /chưa được xác nhận/);
});
test("backend confirmed is successful; expired and canceled never allow checkout", () => {
  assert.match(registrationMessage({ status: "CONFIRMED" }), /máy chủ xác nhận/);
  assert.match(registrationMessage({ status: "EXPIRED" }), /giải phóng/);
  for (const status of ["CONFIRMED", "EXPIRED", "CANCELLED"]) {
    assert.throws(() => prepareCheckout(snapshot([], { ...registration, status }), 123, "ONLINE"));
  }
  assert.equal(canCheckout({ ...registration, paymentDeadline: "2020-01-01T10:00:00" }), false);
});
test("refund-required has distinct guidance and blocks another payment", () => {
  const refund = { ...pending, status: "REFUND_REQUIRED" };
  assert.match(paymentMessage(refund), /hoàn tiền/);
  assert.throws(() => prepareCheckout(snapshot([refund]), 123, "COUNTER"));
});
test("lost or potentially expired ONLINE links require explicit acknowledgement", () => {
  const online = { ...pending, method: "ONLINE" };
  assert.equal(validVnpayUrl(online.paymentUrl), false);
  assert.throws(() => prepareCheckout(snapshot([online]), 123, "ONLINE", false));
  assert.equal(prepareCheckout(snapshot([online]), 123, "ONLINE", true).payload.referenceId, 123);
  assert.throws(() => prepareCheckout(snapshot(), 123, "COUNTER", false, true));
  assert.equal(prepareCheckout(snapshot([{ ...online, status: "CANCELLED" }]), 123, "ONLINE").payload.method, "ONLINE");
});
test("URL policy rejects simulator, alternate hosts, credentials and wrong protocols", () => {
  const good = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Command=pay&vnp_SecureHash=abc";
  assert.equal(validVnpayUrl(good), true);
  for (const value of [good.replace("https:", "http:"), good.replace("sandbox.vnpayment.vn", "sandbox.vnpayment.vn.evil.test"), good.replace("https://", "https://user@"), "javascript:alert(1)", "http://localhost:8080/api/payments/online/callback", undefined]) assert.equal(validVnpayUrl(value), false);
});
test("invalid IDs and unrelated purposes cannot select a registration payment", () => {
  for (const value of [null, "1.2", "-1", "01", "1e2", "9007199254740992"]) assert.equal(positiveId(value), null);
  assert.deepEqual(registrationPayments([{ ...pending, purpose: "MEMBERSHIP" }, { ...pending, referenceId: 456 }, pending], 123), [pending]);
});
test("poll predicate stops at terminal registration/payment states", () => {
  assert.equal(shouldPoll(registration, [pending]), true);
  assert.equal(shouldPoll({ status: "EXPIRED" }, [pending]), false);
  assert.equal(shouldPoll({ status: "CONFIRMED" }, [pending]), false);
  assert.equal(shouldPoll(registration, [{ ...pending, status: "CANCELLED" }]), false);
});
test("terminal reads do not schedule another poll", async () => {
  let scheduled = 0;
  let ended;
  const stop = startStatusPolling(async () => ({ status: "CONFIRMED" }), (_data, done) => { ended = done; }, assert.fail,
    (row) => shouldPoll(row, [pending]), { setTimer: () => scheduled++, clearTimer: () => {} });
  await Promise.resolve(); await Promise.resolve();
  assert.equal(scheduled, 0);
  assert.equal(ended, true);
  stop();
});
test("polling is bounded and cleanup prevents an in-flight response from updating UI", async () => {
  let scheduled;
  let reads = 0;
  const stop = startStatusPolling(async () => ++reads, () => {}, assert.fail, () => true, { maxReads: 2, setTimer: (fn) => { scheduled = fn; return 1; }, clearTimer: () => {} });
  await Promise.resolve(); await Promise.resolve();
  await scheduled();
  assert.equal(reads, 2);
  stop();
  let resolve;
  let updates = 0;
  const cleanup = startStatusPolling(() => new Promise((done) => { resolve = done; }), () => updates++, assert.fail, () => true);
  cleanup(); resolve({});
  await Promise.resolve(); await Promise.resolve();
  assert.equal(updates, 0);
});
