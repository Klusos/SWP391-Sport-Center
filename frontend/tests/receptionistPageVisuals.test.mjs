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
  assert.match(source, /setPage\(0\)/);
  assert.match(source, /member-initials/);
  assert.match(source, /member-detail-grid/);
  assert.match(source, /isAdmin && selected\.user\.status === "ACTIVE"/);
  assert.match(source, /selected\?\.user\.id === member\.id/);
  assert.match(css, /member-list-workspace\.has-selection/);
  assert.match(css, /overflow-wrap:anywhere/);
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
