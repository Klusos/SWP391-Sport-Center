import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { findOwnedPayment, findOwnedPtBooking, ptPaymentReturnMessage } from "../src/paymentReturn.js";
import { startStatusPolling } from "../src/courseCheckout.js";

const payments = [
  { id: 10, memberId: 4, purpose: "PT_BOOKING", referenceId: 71, status: "PENDING" },
  { id: 11, memberId: 4, purpose: "MEMBERSHIP", referenceId: 2, status: "SUCCESS" },
  { id: 12, memberId: 4, purpose: "COURSE_REGISTRATION", referenceId: 33, status: "CANCELLED" },
];

test("return resolves only an exact positive ID found in authenticated owner payment history", () => {
  assert.equal(findOwnedPayment(payments, "10"), payments[0]);
  for (const invalid of [null, "", "0", "1.2", "-1", "1e2", "9007199254740992"]) assert.equal(findOwnedPayment(payments, invalid), null);
  assert.equal(findOwnedPayment(payments, "999"), null, "another owner's payment is absent from the authenticated history response");
});

test("PT return resolves the booking only from PT purpose and its backend reference ID", () => {
  const booking = { id: 71, status: "CONFIRMED" };
  assert.equal(findOwnedPtBooking([booking], payments[0]), booking);
  assert.equal(findOwnedPtBooking([booking], payments[1]), null);
  assert.equal(findOwnedPtBooking([{ id: 72 }], payments[0]), null);
});

test("PT result messages cover actual status outcomes without treating URL parameters as proof", () => {
  assert.match(ptPaymentReturnMessage({ status: "SUCCESS" }), /backend confirms payment success/);
  assert.match(ptPaymentReturnMessage({ status: "PENDING" }), /browser return alone does not confirm/);
  assert.match(ptPaymentReturnMessage({ status: "FAILED" }), /backend reports payment failure/);
  assert.match(ptPaymentReturnMessage({ status: "CANCELLED" }), /backend reports this payment as cancelled/);
  assert.match(ptPaymentReturnMessage({ status: "REFUND_REQUIRED" }), /reconciliation or refund/);
});

test("delayed PT settlement is re-read and polling stops only when backend status is terminal", async () => {
  let status = "PENDING", reads = 0, scheduled;
  const updates = [];
  const stop = startStatusPolling(async () => ({ status: status === "PENDING" && ++reads === 1 ? "PENDING" : status }),
    (value, ended) => updates.push({ value, ended }), () => assert.fail("unexpected poll error"), (value) => value.status === "PENDING",
    { setTimer: (fn) => { scheduled = fn; return 1; }, clearTimer: () => {} });
  await Promise.resolve(); await Promise.resolve();
  assert.equal(updates[0].value.status, "PENDING");
  status = "SUCCESS";
  await scheduled();
  assert.equal(updates.at(-1).value.status, "SUCCESS");
  assert.equal(updates.at(-1).ended, true);
  stop();
});

test("shared result loader branches on authenticated purpose and never creates payment during return", async () => {
  const source = await readFile(new URL("../src/pages/CourseCheckout.jsx", import.meta.url), "utf8");
  const readBlock = source.slice(source.indexOf("  const read = useCallback"), source.indexOf("  useEffect(() => {", source.indexOf("  const read = useCallback")));
  assert.match(readBlock, /findOwnedPayment\(payments, paymentId\)/);
  assert.match(readBlock, /returnedPayment\?\.purpose === "MEMBERSHIP"/);
  assert.match(readBlock, /returnedPayment\?\.purpose === "PT_BOOKING"/);
  assert.match(readBlock, /getMyPtBookingsApi\(token\)/);
  assert.match(readBlock, /getMyRegistrationsApi\(token\)/);
  assert.doesNotMatch(readBlock, /createPaymentApi/);
  assert.match(source, /payment\?\.purpose === "PT_BOOKING" && payment\.status === "PENDING"/);
  assert.match(source, /targetPayment\.purpose === "MEMBERSHIP"/);
  assert.match(source, /targetPayment\?\.purpose === "COURSE_REGISTRATION"/);
});
