import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
const page = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");

test("Receptionist Renewal APIs are scoped to a selected member and create COUNTER attempts without client quote data", () => {
  assert.match(api, /getMemberRenewalIntentsApi\(token, memberId\)/);
  assert.match(api, /\/api\/receptionist\/members\/.*\/renewal-intents/);
  assert.match(api, /startReceptionistRenewalCounterAttemptApi\(token, memberId, intentId\)/);
  assert.match(api, /counter-attempts/);
  assert.match(api, /method: "POST"/);
});

test("Receptionist page presents frozen Renewal quote and deadline, and uses existing COUNTER confirmation", () => {
  assert.match(page, /Gia hạn Membership/);
  assert.match(page, /quotedAmount/);
  assert.match(page, /validUntil/);
  assert.match(page, /successorStartDate/);
  assert.match(page, /startReceptionistRenewalCounterAttemptApi/);
  assert.match(page, /processCounterPaymentApi\(token, attempt\.paymentId, true/);
  assert.match(page, /REFUND_REQUIRED không có nghĩa là tiền đã được hoàn/);
});

test("Receptionist page blocks duplicate live attempts and manual-reconciliation retries", () => {
  assert.match(page, /const livePending = intent\.attempts/);
  assert.match(page, /open && !livePending/);
  assert.match(page, /!needsReview/);
  assert.match(page, /Có khoản thu cần đối soát thủ công; không tạo lượt thanh toán mới/);
});
