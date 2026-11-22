import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const sales = await readFile(new URL("../src/pages/ReceptionistMembershipSales.jsx", import.meta.url), "utf8");
const salesCss = await readFile(new URL("../src/pages/ReceptionistMembershipSales.css", import.meta.url), "utf8");
const queue = await readFile(new URL("../src/pages/ConsultationQueue.jsx", import.meta.url), "utf8");
const queueCss = await readFile(new URL("../src/pages/ConsultationQueue.css", import.meta.url), "utf8");
const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");

test("membership sales presents real member and plan selections beside a truthful payment summary", () => {
  assert.match(sales, /membership-sales-checkout-layout/);
  assert.match(sales, /membership-sales-selected-member/);
  assert.match(sales, /membershipPlan/);
  assert.match(sales, /membership-sales-order-summary/);
  assert.match(sales, /pendingPayment \? money\(pendingPayment\.amount\) : selectedPlan \? money\(selectedPlan\.price\)/);
  assert.match(sales, /membership-sales-order-note/);
  assert.equal((sales.match(/name="membershipPlan"/g) || []).length, 1);
  assert.match(salesCss, /@media \(max-width:900px\)/);
  assert.match(salesCss, /membership-sales-order-summary/);
});

test("Receptionist plan choices adapt to the narrower portal content column without changing Admin breakpoints", () => {
  assert.match(salesCss, /@media \(max-width: 1200px\)\s*\{\s*\.membership-sales-page:not\(\.membership-sales-page--admin\) \.membership-sales-plan-grid \{ grid-template-columns:repeat\(2,minmax\(0,1fr\)\); \}/);
  assert.match(salesCss, /@media \(max-width: 600px\)\s*\{\s*\.membership-sales-page:not\(\.membership-sales-page--admin\) \.membership-sales-plan-grid \{ grid-template-columns:minmax\(0,1fr\); \}/);
  assert.match(salesCss, /\.membership-sales-page \.membership-sales-plan-grid \{ display:grid; grid-template-columns:repeat\(3,minmax\(0,1fr\)\);/);
});

test("membership sales keeps counter payment creation, explicit confirmation, and duplicate-submit guards", () => {
  assert.match(sales, /createPaymentApi\(token, \{/);
  assert.match(sales, /method: "COUNTER"/);
  assert.match(sales, /if \(saving \|\| !selectedMember \|\| !selectedPlan \|\| pendingPayment \|\| purchaseBlockedByMembership\) return/);
  assert.match(sales, /processCounterPaymentApi\(token, pendingPayment\.id, success/);
  assert.match(sales, /pendingPayment\.status === "PENDING" && pendingPayment\.method === "COUNTER"/);
  assert.match(sales, /if \(result\.status === "SUCCESS"\)/);
});

test("membership sales adds Admin-only account context and preserves Receptionist context", () => {
  assert.match(sales, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(sales, /currentUserName/);
  assert.match(sales, /isAdmin && <AdminTopBar pageLabel="QUẢN LÝ GÓI THÀNH VIÊN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.doesNotMatch(sales, /SPORTCENTER \/ RECEPTIONIST/);
  assert.match(sales, /membership-sales-receptionist-content/);
  assert.doesNotMatch(sales, /NotificationBell/);
  assert.match(salesCss, /membership-sales-page\.membership-sales-page--admin/);
  assert.match(salesCss, /membership-sales-receptionist-content > \.membership-sales-card/);
  assert.match(app, /path="\/admin\/membership-sales" element=\{<ReceptionistMembershipSales \{\.\.\.staff\} \/>\}/);
  assert.match(app, /path="\/receptionist\/membership-sales"[\s\S]*?element=\{<ReceptionistMembershipSales \{\.\.\.staff\} \/>\}/);
});

test("consultation segmented filters preserve backend status paging and role restrictions", () => {
  assert.match(queue, /const STATUSES = \["", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"\]/);
  assert.match(queue, /getConsultationRequestsApi\(token, \{ status, page, size: PAGE_SIZE \}\)/);
  assert.match(queue, /aria-pressed=\{status === value\}/);
  assert.match(queue, /setSearchParams\(value \? \{ status: value \} : \{\}\); setPage\(0\); setNotice\(""\)/);
  assert.match(queue, /disabled=\{!allowedRole\}/);
  assert.match(queue, /request\.status === "PENDING" && allowedRole/);
  assert.match(queue, /actorId != null && request\.handledBy === actorId/);
  assert.match(queue, /setPage\(\(current\) => current \+ 1\)/);
  assert.match(queueCss, /consultation-filter-segments/);
  assert.match(queueCss, /consultation-workspace\.has-detail/);
  assert.match(queueCss, /@media \(max-width:1150px\)/);
});

test("Consultations adds Admin-only account context and preserves Receptionist presentation and workflow", () => {
  const adminBarIndex = queue.indexOf("{isAdmin && <AdminTopBar");
  const adminContentIndex = queue.indexOf('<div className={isAdmin ? "consultation-admin-content"');
  assert.ok(adminBarIndex >= 0 && adminBarIndex < adminContentIndex, "AdminTopBar is a sibling before the padded content wrapper");
  assert.match(queue, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(queue, /isAdmin && <AdminTopBar pageLabel="YÊU CẦU TƯ VẤN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.doesNotMatch(queue, /SPORTCENTER \/ RECEPTIONIST/);
  assert.match(queue, /consultation-receptionist-content/);
  assert.match(queue, /allowedRole = role === "RECEPTIONIST" \|\| role === "ADMIN"/);
  assert.match(queue, /completeConsultationRequestApi/);
  assert.match(queue, /takeConsultationRequestApi/);
  assert.doesNotMatch(queue, /NotificationBell/);
  assert.match(queueCss, /consultation-admin-content/);
  assert.match(queueCss, /\.main\.consultation-queue-page--admin \{ padding: 0; gap: 0; \}/);
  assert.match(queue, /consultation-queue-page--receptionist/);
  assert.match(queueCss, /\.main\.consultation-queue-page--receptionist \{ width: 100%; max-width: none; margin-inline: 0; padding: clamp\(18px,2\.5vw,34px\) clamp\(16px,2\.7vw,40px\) 48px; \}/);
  assert.match(queueCss, /consultation-receptionist-content \{ display: contents; \}/);
  assert.match(app, /path="\/staff\/consultations"[\s\S]*?element=\{<ConsultationQueue \{\.\.\.staff\} \/>\}/);
  assert.match(app, /currentUserName: auth\?\.name/);
});
