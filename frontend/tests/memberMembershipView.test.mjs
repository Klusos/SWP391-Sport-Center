import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
const css = await readFile(new URL("../src/pages/MemberMembership.css", import.meta.url), "utf8");

test("Membership overview uses the effective membership and has a real empty state", () => {
  assert.match(source, /const sections = membershipSections\(memberships, today\)/);
  assert.match(source, /currentMemberships\.length === 0[\s\S]*?Bạn chưa có Membership đang hiệu lực/);
  assert.match(source, /ngày kết thúc không bao gồm/);
  assert.match(source, /const remainingDays = membership\.endDate/);
  assert.equal((source.match(/aria-labelledby="membership-current-title"/g) || []).length, 1);
  assert.match(source, /currentMemberships\.map\(\(membership\)/);
});

test("Benefits match the purchased plan by ID and unavailable data stays neutral", () => {
  assert.match(source, /Number\(item\.id\) === Number\(membership\.planId\)/);
  assert.match(source, /Chưa có dữ liệu quyền lợi cho gói Membership này/);
  assert.match(source, /plan\.courseDiscountPercent/);
  assert.match(source, /plan\.allowOneOnOneCoaching/);
  assert.match(source, /plan\.allowPersonalTrainingPlan/);
  assert.doesNotMatch(source, /courseDiscountPercent \?\? 20|personalTrainingDiscountPercent \?\? 15/);
});

test("All effective Membership records remain visible in the single current area", () => {
  assert.match(source, /currentMemberships\.map\(\(membership\)/);
  assert.match(source, /hasOverlappingEffectiveMemberships\(sections\)/);
  assert.doesNotMatch(source, /Membership đang hiệu lực<\/h2>/);
});

test("The Phase 16N.3 two-column overview is restored without a duplicate current section", () => {
  assert.match(source, /className="membership-overview-grid"/);
  assert.match(source, /className="dash-card membership-current-card"/);
  assert.match(source, /className="dash-card membership-benefits-card"/);
  assert.match(source, /membership-auto-renew-control/);
  assert.match(source, /renderMembershipBenefits\(plan\)/);
  assert.equal((source.match(/aria-labelledby="membership-current-title"/g) || []).length, 1);
  assert.doesNotMatch(source, /membership-current-list/);
});

test("Every queued membership and pending payment is rendered from the loaded collections", () => {
  assert.match(source, /renderMembershipRows\(sections\.upcoming\)/);
  assert.match(source, /pendingMembershipPayments\.map\(\(payment\)/);
  assert.match(source, /paymentStatusLabel\[payment\.status\] \|\| payment\.status/);
  assert.match(source, /pagedPaymentHistory\.items\.map\(\(payment\)/);
});

test("Membership and payment history have independent accessible pagination and payment filters", () => {
  assert.match(source, /paginateItems\(membershipHistory, membershipHistoryPage, 5\)/);
  assert.match(source, /paginateItems\(filteredPaymentHistory, paymentHistoryPage, 10\)/);
  assert.match(source, /pagedMembershipHistory\.items\.map/);
  assert.match(source, /paymentStatusFilter/);
  assert.match(source, /paymentPurposeFilter/);
  assert.match(source, /setMembershipHistoryPage\(0\)/);
  assert.match(source, /setPaymentHistoryPage\(0\)/);
  assert.match(source, /aria-label=\{`Phân trang lịch sử \$\{label\}`\}/);
  assert.match(source, /aria-live="polite"/);
});

test("Auto-renew stays on the existing API flow and explains that payment is still required", () => {
  assert.match(source, /setAutoRenewApi\(token, membership\.id, enable\)/);
  assert.match(source, /không tự động trừ tiền hoặc kích hoạt gói gia hạn nếu chưa có thanh toán thành công/);
  assert.equal((source.match(/onClick=\{\(\) => void toggleAutoRenew\(membership\)\}/g) || []).length, 2);
  assert.match(source, /\{last && <button/);
  assert.match(source, /\{last \? <button/);
});

test("Upgrade choices and purchase actions use existing server data and handlers", () => {
  assert.match(source, /getMembershipUpgradeOptionsApi\(token, user\.id\)/);
  assert.match(source, /upgrade\.options\.map\(\(option\)/);
  assert.match(source, /onClick=\{\(\) => openUpgradeModal\(option\)\}/);
  assert.match(source, /createPaymentApi\(token, \{[\s\S]*purpose: "MEMBERSHIP"/);
  assert.match(source, /onContinue=\{\(method\) => startPurchase\(paymentModalPlan, method\)\}/);
});

test("Pending payment view does not invent a VNPay expiry or countdown", () => {
  assert.match(source, /payment\.createdAt/);
  assert.doesNotMatch(source, /23:59 hôm nay|Hết hạn thanh toán|countdown|expiresAt/);
});

test("Unsupported mock membership claims and fake identities are absent", () => {
  for (const unsupported of ["RILEY CHEN", "PLATINUM", "MEM-2026-", "Smart Locker", "CHECK-IN NHANH", "50 ngày hiệu lực", "Miễn phí 100% ra vào"]) {
    assert.ok(!source.includes(unsupported), `unexpected unsupported content: ${unsupported}`);
  }
  assert.match(source, /Membership sắp có hiệu lực/);
  assert.match(source, /Lịch sử Membership/);
  assert.match(source, /Yêu cầu hủy Membership/);
});

test("Membership page uses scoped responsive light-theme styles", () => {
  assert.match(css, /\.membership-overview-grid/);
  assert.match(css, /#fff/);
  assert.match(css, /#1769a8/);
  assert.match(css, /#ef8b2c/);
  assert.match(css, /@media \(max-width: 900px\)/);
  assert.match(css, /@media \(max-width: 650px\)/);
  assert.match(css, /\.member-membership-page button:focus-visible/);
  assert.match(css, /\.membership-overview-grid\s*\{[\s\S]*?grid-template-columns: minmax\(0, 1\.25fr\) minmax\(280px, \.85fr\)/);
  assert.match(css, /\.membership-current-entry-heading/);
});
