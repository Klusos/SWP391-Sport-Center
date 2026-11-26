import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Admin PT supervision is housed in Training and keeps compact, searchable correction controls", async () => {
  const [attendance, training, panel] = await Promise.all([
    read("../src/pages/StaffAttendance.jsx"),
    read("../src/pages/CoachTraining.jsx"),
    read("../src/pages/AdminPtOutcomePanel.jsx"),
  ]);
  assert.doesNotMatch(attendance, /AdminPtOutcomePanel/);
  assert.match(training, /isAdmin && adminTrainingView === "pt" \? <section className="training-admin-pt-supervision"/);
  assert.match(training, /Giáo án &amp; kết quả lớp học/);
  assert.match(training, /Giám sát huấn luyện cá nhân/);
  assert.match(training, /<AdminPtOutcomePanel token=\{token\} \/>/);
  assert.match(panel, /const PAGE_SIZE = 10/);
  assert.match(panel, /rows\.filter\(/);
  assert.match(panel, /reason\.trim\(\)\.length > 300/);
  assert.match(panel, /correctAdminPtBookingOutcomeApi/);
  assert.match(panel, /role="dialog"/);
  assert.match(panel, /admin-pt-transition-arrow/);
  assert.match(panel, /không tạo khoản hoàn tiền/);
});

test("plan list compacts rows while preserving full descriptions and containing narrow-table scrolling", async () => {
  const [page, css] = await Promise.all([
    read("../src/pages/MembershipPlans.jsx"),
    read("../src/pages/MembershipPlans.css"),
  ]);
  assert.match(page, /<small title=\{plan\.description \|\| "Chưa có mô tả\."\}/);
  assert.match(page, /membership-plan-modal-backdrop/);
  assert.match(page, /role="dialog" aria-modal="true" aria-labelledby="membership-plan-form-title"/);
  assert.match(css, /table-layout: fixed/);
  assert.match(css, /-webkit-line-clamp: 3/);
  assert.match(css, /\.queue-table-wrap \{ overflow-x: auto; \}/);
});

test("Consultation table fits action controls when desktop content is wide enough and contains overflow on mobile", async () => {
  const css = await read("../src/pages/ConsultationQueue.css");
  assert.match(css, /@container \(min-width:900px\)/);
  assert.match(css, /\.queue-table \{ width:100%; min-width:0; table-layout:fixed; \}/);
  assert.match(css, /\.queue-actions \{ min-width:0; flex-direction:column; align-items:stretch; \}/);
  assert.match(css, /\.consultation-modal-backdrop/);
  assert.match(css, /\.consultation-modal \{ width:min\(100%,920px\); max-height:min\(92vh,900px\); overflow:auto/);
});

test("Membership counter selection controls use high-contrast interactive states", async () => {
  const css = await read("../src/pages/ReceptionistMembershipSales.css");
  assert.match(css, /\.membership-sales-history-row button[\s\S]*?color:\s*#fff/);
  assert.match(css, /\.membership-sales-history-row button:hover:not\(:disabled\)[\s\S]*?color:\s*#fff/);
  assert.match(css, /\.membership-sales-history-row button:focus-visible/);
  assert.match(css, /\.membership-sales-history-row button:disabled[\s\S]*?color:\s*#fff/);
});

test("Audit actor and technical details remain accessible without repeated history disclaimer", async () => {
  const page = await read("../src/pages/AdminAuditLogs.jsx");
  assert.match(page, /User #\{entry\.actorUserId\}/);
  assert.match(page, /Chi tiết kỹ thuật/);
  assert.doesNotMatch(page, /tên hiện tại, không phải ảnh chụp lịch sử/);
});
