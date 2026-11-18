import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { membershipPlanBenefits } from "../src/membershipPlanPresentation.js";

test("membership plan benefit display uses only API values and preserves missing data as unavailable", () => {
  assert.deepEqual(membershipPlanBenefits({
    courseDiscountPercent: 10,
    personalTrainingDiscountPercent: 5,
    allowPersonalTrainingPlan: true,
    allowOneOnOneCoaching: false,
  }), [
    { label: "Ưu đãi khóa học", value: "10%" },
    { label: "Ưu đãi huấn luyện cá nhân", value: "5%" },
    { label: "Giáo án cá nhân", value: "Bao gồm" },
    { label: "Huấn luyện 1-1", value: "Không bao gồm" },
  ]);
  assert.ok(membershipPlanBenefits({}).every((item) => item.value === "—"));
});

test("Member List retains staff APIs, current search/paging, selected detail, and Admin-only actions", async () => {
  const source = await readFile(new URL("../src/pages/MemberList.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/MemberList.css", import.meta.url), "utf8");
  assert.match(source, /getUsersApi\(token\)/);
  assert.match(source, /getMemberProfilesApi\(token\)/);
  assert.match(source, /getUserApi\(token, memberId\)/);
  assert.match(source, /detailOpen && createPortal\(/);
  assert.match(source, /role="dialog"[\s\S]*?aria-modal="true"[\s\S]*?aria-labelledby="member-detail-dialog-title"/);
  assert.match(source, /setPage\(0\)/);
  assert.match(source, /member-initials/);
  assert.match(source, /member-detail-grid/);
  assert.match(source, /isAdmin && selected\.user\.status === "ACTIVE"/);
  assert.match(source, /selected\?\.user\.id === member\.id/);
  assert.match(source, /className="member-list-modal-close"[^>]*onClick=\{\(\) => closeDetailRef\.current\?\.\(\)\}/);
  assert.doesNotMatch(source, /member-list-detail-column|has-selection|is-editing/);
  assert.match(css, /\.member-list-modal-dialog \{[^}]*width:min\(1080px,100%\)[^}]*overflow-y:auto/);
  assert.match(css, /\.member-list-modal \{[^}]*background:rgb\(5 18 33 \/ 58%\)/);
  assert.match(css, /\.member-list-modal \.member-edit-grid \{[^}]*repeat\(2,minmax\(0,1fr\)\)/);
  assert.match(css, /\.member-list-main \.member-table td:first-child \{[^}]*white-space:nowrap;[^}]*overflow-wrap:normal/);
  assert.doesNotMatch(source, /bi-x-lg/);
  assert.match(css, /overflow-wrap:anywhere/);
  assert.match(css, /\.member-list-main:not\(\.is-admin\) \{ width:100%; max-width:none; margin-inline:0; \}/);
  assert.match(css, /\.member-list-main:not\(\.is-admin\) \.member-table \{ width:100%; table-layout:fixed; \}/);
  assert.match(css, /\.member-list-main:not\(\.is-admin\) \.member-contact-cell \{ max-width:none; overflow-wrap:anywhere; white-space:normal; \}/);
  assert.match(css, /\.member-list-main \.queue-table-wrap \{ overflow:auto;/);
});

test("Receptionist plan cards share the existing selection and payment creation path", async () => {
  const source = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/ReceptionistMembershipSales.css", import.meta.url), "utf8");
  assert.match(source, /getPublicMembershipPlansApi\(\)/);
  assert.match(source, /plan\?\.status === "ACTIVE"/);
  assert.match(source, /checked=\{String\(plan\.id\) === String\(planId\)\}/);
  assert.match(source, /onChange=\{\(\) => setPlanId\(String\(plan\.id\)\)\}/);
  assert.match(source, /onSubmit=\{createPendingPayment\}/);
  assert.match(source, /membershipPlanBenefits\(plan\)/);
  assert.match(css, /membership-sales-plan-grid/);
  assert.match(css, /@media \(max-width: 650px\)/);
  assert.doesNotMatch(source, /VIP|Phổ biến nhất|Ưu đãi chọn chu kỳ/);
});
