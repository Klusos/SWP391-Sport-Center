import test from "node:test";
import assert from "node:assert/strict";
import { membershipPaymentMessage, membershipPaymentPayload } from "../src/membershipCheckout.js";
import { validVnpayUrl } from "../src/courseCheckout.js";

test("new membership checkout is fixed to one month without client amount or member identity", () => {
  assert.deepEqual(membershipPaymentPayload("42", "ONLINE"), {
    method: "ONLINE", purpose: "MEMBERSHIP", referenceId: 42, autoRenew: false, purchasedMonths: 1,
  });
  assert.deepEqual(membershipPaymentPayload(42, "COUNTER"), {
    method: "COUNTER", purpose: "MEMBERSHIP", referenceId: 42, autoRenew: false, purchasedMonths: 1,
  });
  assert.throws(() => membershipPaymentPayload(0, "ONLINE"));
  assert.throws(() => membershipPaymentPayload(42, "SUCCESS"));
});

test("membership ONLINE URL policy uses the existing strict VNPay Sandbox validation", () => {
  assert.equal(validVnpayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Command=pay&vnp_SecureHash=abc"), true);
  assert.equal(validVnpayUrl("http://localhost:8080/api/payments/online/callback?success=true"), false);
});

test("membership payment messages keep pending, canceled, success and refund states distinct", () => {
  assert.match(membershipPaymentMessage({ method: "ONLINE", status: "PENDING" }), /đang được xác minh/);
  assert.match(membershipPaymentMessage({ method: "COUNTER", status: "PENDING" }), /nhân viên xác nhận/);
  assert.match(membershipPaymentMessage({ status: "SUCCESS" }), /Máy chủ đã xác minh/);
  assert.match(membershipPaymentMessage({ status: "CANCELLED" }), /không hoàn tất/);
  assert.match(membershipPaymentMessage({ status: "REFUND_REQUIRED" }), /đối soát hoặc xử lý hoàn tiền/);
});
