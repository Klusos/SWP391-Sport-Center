import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

import { membershipPurchaseErrorMessage } from "../src/membershipPurchase.js";

test("effective or future Membership conflict explains ordinary Purchase is blocked", () => {
  assert.match(
    membershipPurchaseErrorMessage({ status: 409, message: "An effective or future Membership already exists for this member" }),
    /đang hiệu lực hoặc sắp có hiệu lực.*Không thể tạo giao dịch mua thông thường/
  );
});

test("pending payment conflicts remain distinct from Membership eligibility conflicts", () => {
  assert.match(
    membershipPurchaseErrorMessage({ status: 409, message: "A pending membership payment already exists for this member" }),
    /thanh toán Membership chờ xử lý/
  );
});

test("Member UI blocks ordinary purchase for current or future ACTIVE Memberships and keeps server errors visible", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /currentMemberships\.length > 0 \|\| sections\.upcoming\.length > 0/);
  assert.match(source, /disabled=\{busyPlanId !== null \|\| purchaseBlockedByMembership/);
  assert.match(source, /if \(busyPlanId !== null \|\| purchaseBlockedByMembership \|\| pendingMembershipPayments\.length > 0/);
  assert.match(source, /membershipPurchaseErrorMessage\(requestError\)/);
  assert.match(source, /reconciliationUnknown \? `\$\{message\} Làm mới lịch sử thanh toán trước khi thử lại\.` : message/);
});

test("Receptionist and Admin shared sales UI blocks Purchase and maps backend conflicts", async () => {
  const source = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
  assert.match(source, /import \{ membershipPurchaseErrorMessage \} from "\.\.\/membershipPurchase\.js"/);
  assert.match(source, /const purchaseBlockedByMembership = memberChain\.length > 0/);
  assert.match(source, /membershipPurchaseErrorMessage\(requestError\)/);
  assert.match(source, /memberMemberships === null \|\| purchaseBlockedByMembership/);
});

test("historical membership benefits render from Membership response fields, not the current Plan catalog", async () => {
  const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
  assert.match(source, /renderMembershipBenefits\(membership\)/);
  assert.match(source, /membership\.benefitSource === "LEGACY_PLAN_FALLBACK"/);
  assert.doesNotMatch(source, /find\(\(item\) => Number\(item\.id\) === Number\(membership\.planId\)\)/);
});
