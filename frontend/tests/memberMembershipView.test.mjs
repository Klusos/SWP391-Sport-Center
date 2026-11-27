import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("../src/pages/MemberMembership.jsx", import.meta.url), "utf8");
const css = await readFile(new URL("../src/pages/MemberMembership.css", import.meta.url), "utf8");

test("Membership overview uses the effective membership and has a real empty state", () => {
  assert.match(source, /const sections = membershipSections\(memberships, today\)/);
assert.match(source, /currentMemberships\.length === 0[\s\S]*?Bạn chưa có gói hội viên đang hiệu lực/);
  assert.match(source, /ngày kết thúc không bao gồm/);
  assert.match(source, /const remainingDays = membership\.endDate/);
  assert.equal((source.match(/aria-labelledby="membership-current-title"/g) || []).length, 1);
  assert.match(source, /currentMemberships\.map\(\(membership\)/);
});

test("Benefits render purchased Membership values and distinguish legacy Plan fallback", () => {
  assert.match(source, /renderMembershipBenefits\(membership\)/);
  assert.match(source, /membership\.benefitSource === "LEGACY_PLAN_FALLBACK"/);
assert.match(source, /Chưa có dữ liệu quyền lợi cho gói hội viên này/);
  assert.match(source, /membership\.courseDiscountPercent/);
  assert.match(source, /membership\.allowOneOnOneCoaching/);
  assert.match(source, /membership\.allowPersonalTrainingPlan/);
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
  assert.match(source, /renderMembershipBenefits\(membership\)/);
  assert.equal((source.match(/aria-labelledby="membership-current-title"/g) || []).length, 1);
  assert.doesNotMatch(source, /membership-current-list/);
});

test("Every queued membership and pending payment is rendered from the loaded collections", () => {
  assert.match(source, /renderMembershipRows\(sections\.upcoming\)/);
  assert.match(source, /pendingPresentation\.paymentItems\.map\(\(\{ payment, renewalIntent/);
  assert.match(source, /renewalRequestIntents\.map/);
  assert.match(source, /unresolvedRenewalIntents\.map/);
  assert.match(source, /Yêu cầu và thanh toán đang chờ/);
  assert.doesNotMatch(source, /aria-labelledby="member-renewal-title"/);
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
  assert.match(source, /tạo yêu cầu gia hạn/);
  assert.match(source, /3 ngày cuối/);
  assert.match(source, /thanh toán thành công/);
  assert.match(source, /không tự động thu tiền/);
  assert.equal((source.match(/onClick=\{\(\) => void toggleAutoRenew\(membership\)\}/g) || []).length, 2);
  assert.match(source, /\{last && <button/);
  assert.match(source, /\{last \? <button/);
});

test("Renewal guidance preserves the 72-hour policy and is scoped to Renewal intents and attempts", () => {
  assert.match(source, /Bạn có thể tắt gia hạn trước khi thanh toán\. Hệ thống không tự động thu tiền\./);
  assert.match(source, /intent\.status === "OPEN"[\s\S]*?renewal-cancellation-warning/);
  assert.match(source, /\{renewalIntent && <p className="membership-policy-note renewal-cancellation-warning"/);
  assert.match(source, /async function cancelPendingPayment[\s\S]*?setNotice\([\s\S]*?await load\(\);[\s\S]*?\} catch/);
  assert.match(source, /không thể gửi yêu cầu hủy thông thường vì gói mới đã ở trong thời hạn báo trước 72 giờ/);
  assert.match(css, /renewal-cancellation-warning[^}]*background: #fff7e8/);
  assert.match(source, /sections\.upcoming\.length > 0 && <p className="membership-policy-note">Với gói hội viên sắp có hiệu lực/);
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

test("Pending Upgrade presents the frozen quote once in a compact responsive price row", () => {
  const pendingCard = source.slice(source.indexOf('className="membership-pending-card"'), source.indexOf("</article>;", source.indexOf('className="membership-pending-card"')));
  assert.match(pendingCard, /membership-upgrade-payment-quote/);
  assert.match(pendingCard, /money\(quote\.targetPrice\)/);
  assert.match(pendingCard, /Giá trị khấu trừ/);
  assert.match(pendingCard, /money\(quote\.appliedCredit\)/);
  assert.match(pendingCard, /money\(quote\.payable\)/);
  assert.match(pendingCard, /quote\.targetDurationMonths/);
  assert.match(pendingCard, /paymentMethodLabel\[payment\.method\]/);
  const upgradeQuote = pendingCard.slice(pendingCard.indexOf('className="membership-upgrade-payment-quote"'), pendingCard.indexOf("</dl>", pendingCard.indexOf('className="membership-upgrade-payment-quote"')));
  assert.doesNotMatch(upgradeQuote, /quote\.sourceMembershipId|quote\.sourceStartDate|Tạo lúc/);
  assert.match(css, /membership-upgrade-payment-quote\s*\{[^}]*grid-template-columns:repeat\(3,minmax\(0,1fr\)\)/);
  assert.match(css, /membership-upgrade-payable/);
  assert.match(css, /@media \(max-width: 520px\)\s*\{[\s\S]*?membership-upgrade-payment-quote\s*\{\s*grid-template-columns:1fr/);
});

test("Member-facing status and plan benefit descriptions avoid implementation jargon", () => {
  assert.doesNotMatch(source, /TRẠNG THÁI TỪ MÁY CHỦ|snapshot|Purchase thông thường/);
  assert.match(source, /Yêu cầu và thanh toán đang chờ/);
  assert.match(source, /Quyền lợi theo gói tại thời điểm mua/);
});

test("Unsupported mock membership claims and fake identities are absent", () => {
  for (const unsupported of ["RILEY CHEN", "PLATINUM", "MEM-2026-", "Smart Locker", "CHECK-IN NHANH", "50 ngày hiệu lực", "Miễn phí 100% ra vào"]) {
    assert.ok(!source.includes(unsupported), `unexpected unsupported content: ${unsupported}`);
  }
  assert.match(source, /Gói sắp có hiệu lực/);
  assert.match(source, /Lịch sử gói hội viên/);
  assert.match(source, /Bạn có thể tắt gia hạn trước khi thanh toán/);
  assert.doesNotMatch(source, /Mã Membership #\{membership\.id\}/);
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
  assert.match(css, /\.member-membership-page > \.member-membership-header\s*\{[^}]*padding-right: 58px/);
  assert.match(css, /prefers-reduced-motion: reduce/);
  assert.match(css, /\.membership-plan-card:hover/);
});
