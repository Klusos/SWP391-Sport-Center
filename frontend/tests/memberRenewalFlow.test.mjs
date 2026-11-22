import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
const page = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
const controller = await readFile(new URL("../../backend/src/main/java/group5/example/sportCenter/controller/MemberRenewalController.java", import.meta.url), "utf8");
const service = await readFile(new URL("../../backend/src/main/java/group5/example/sportCenter/service/MemberRenewalApiService.java", import.meta.url), "utf8");

test("Member renewal API exposes own Intent reads and an attempt action without client quote fields", () => {
  assert.match(api, /\/api\/memberships\/renewal-intents\/me/);
  assert.match(api, /\/api\/memberships\/renewal-intents\/.*\/attempts/);
  assert.match(controller, /@AuthenticationPrincipal Jwt jwt/);
  assert.match(controller, /startOnlineAttempt\(intentId, jwt, request\.getRemoteAddr\(\)\)/);
  assert.match(service, /Only the owning Member can access Renewal Intents/);
  assert.match(service, /attemptService\.createAttempt\(intentId, PaymentMethod\.ONLINE\)/);
  assert.doesNotMatch(controller, /@RequestBody/);
  assert.doesNotMatch(service, /setAmount\(|setPaidAt\(|setDurationMonthsSnapshot\(/);
});

test("Member page displays server Renewal quote/result and does not infer settlement from VNPay Return", () => {
  assert.match(page, /getMyRenewalIntentsApi\(token\)/);
  assert.match(page, /startRenewalPaymentAttemptApi\(token, intent\.intentId\)/);
  assert.match(page, /intent\.quotedAmount/);
  assert.match(page, /intent\.validUntil/);
  assert.match(page, /intent\.latestResult/);
  assert.match(page, /manualReconciliationRequired/);
  assert.match(page, /window\.location\.assign\(action\.checkoutUrl\)/);
  assert.match(page, /chuyển hướng trình duyệt không tự xác nhận thanh toán/);
  assert.doesNotMatch(page, /set.*Status\("SUCCESS"\)/);
});
