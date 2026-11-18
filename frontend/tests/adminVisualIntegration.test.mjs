import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Admin dashboard keeps real revenue data and uses scoped presentation", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8");
  assert.match(source, /function Admin\(\{ d, token, errors = \{\} \}\)/);
  assert.match(source, /className="admin-dashboard"/);
  assert.match(source, /getAdminRevenueReportApi\(token/);
  assert.match(source, /AdminRevenueCard token=\{token\}/);
  assert.doesNotMatch(source, /Dữ liệu mẫu|months\.map\(/);
  assert.match(css, /\.admin-dashboard \.dash-stat/);
  assert.match(css, /\.admin-dashboard \.dash-grid\.wide/);
});

test("Admin KPI cards use separate top-left label and top-right icon grid areas", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8");
  const stat = source.match(/function Stat\(\{[\s\S]*?\n\}/)?.[0] || "";
  assert.match(stat, /adminCard \? <>[\s\S]*className="dash-stat-admin-label">\{label\}[\s\S]*\{tile\}[\s\S]*<strong>\{value\}<\/strong>[\s\S]*\{description\}/);
  assert.match(source, /const Stats = \(\{ items, adminCards = false \}\)/);
  assert.match(source, /\]\} adminCards \/>/);
  assert.match(css, /\.admin-dashboard \.dash-stat-admin\s*\{[^}]*grid-template-columns: minmax\(0, 1fr\) 43px;[^}]*grid-template-areas:[\s\S]*?"label icon"[\s\S]*?"value value"[\s\S]*?"note note"/);
  assert.match(css, /\.admin-dashboard \.dash-stat-admin > \.dash-stat-admin-label\s*\{[^}]*grid-area: label;[^}]*overflow-wrap: anywhere;[^}]*text-align: left;/);
  assert.match(css, /\.admin-dashboard \.dash-stat-admin > \.dash-stat-admin-label\s*\{[^}]*color: var\(--text-main\);[^}]*font-family: var\(--font-sans\);[^}]*font-size: 14px;[^}]*font-weight: 600;/);
  assert.match(css, /\.admin-dashboard \.dash-stat-admin > \.dash-tile\s*\{[^}]*grid-area: icon;[^}]*justify-self: end;[^}]*transform: translate\(4px, -4px\);/);
  assert.doesNotMatch(css, /\.admin-dashboard \.dash-stat-admin > \.dash-tile\s*\{[^}]*margin[^}]*-/);
  const globalStyles = await readFile(new URL("../src/styles.css", import.meta.url), "utf8");
  assert.match(globalStyles, /--font-sans: "Plus Jakarta Sans"/);
  const adminView = source.slice(source.indexOf("function Admin("), source.indexOf("function Coach("));
  for (const [label, icon, data] of [
    ["Tổng thành viên", "members", "total(d.members)"],
    ["Bộ môn", "sports", "total(d.sports)"],
    ["Yêu cầu tư vấn chờ", "consult", 'by("PENDING")'],
    ["Tổng HLV", "pt", "total(d.coaches)"],
  ]) {
    assert.ok(adminView.includes(`label: "${label}"`), `${label} KPI label remains`);
    assert.ok(adminView.includes(`icon: "${icon}"`), `${label} KPI icon remains`);
    assert.ok(adminView.includes(`value: ${data}`), `${label} KPI data source remains`);
  }
  assert.doesNotMatch(source.slice(source.indexOf("function Coach(")), /adminCards|adminCard/);
});

test("Admin and Coach dashboard polish reuses login identity without another profile request", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8");
  const topBar = await readFile(new URL("../src/components/AdminTopBar.jsx", import.meta.url), "utf8");
  assert.match(app, /currentUserName: auth\?\.name/);
  assert.match(source, /<AdminTopBar[\s\S]*?displayName=\{currentUserName \|\| roleName\}[\s\S]*?\/>\s*\)\s*:\s*\(\s*<header className="rc-topbar">/);
  assert.match(topBar, /function initialsFrom\(name\)/);
  assert.match(topBar, /avatarName/);
  assert.match(topBar, /displayName[\s\S]*?\|\| roleLabel/);
  assert.match(topBar, /import "\.\/AdminTopBar\.css"/);
  assert.match(source, /dash-status st-/);
  assert.doesNotMatch(source, /ADMIN:\s*\{\s*me:\s*getCurrentUserApi/);
  assert.doesNotMatch(source, /COACH:\s*\{\s*me:\s*getCurrentUserApi/);
  assert.match(css, /\.rc-main\.dash-main \.dash-stats\.cols-5/);
  assert.match(css, /@media \(max-width: 640px\)/);
});

test("Admin consultation summary shows API-backed status counts without percentage bars", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8");
  assert.match(source, /getConsultationRequestsApi\(t, \{ status, page: 0, size: 1 \}\)/);
  assert.match(source, /Number\(page\?\.totalElements \?\? 0\)/);
  assert.match(source, /\["PENDING", "Chờ xử lý", "orange", "pending"\]/);
  assert.match(source, /\["IN_PROGRESS", "Đang xử lý", "blue", "search"\]/);
  assert.match(source, /\["COMPLETED", "Hoàn thành", "green", "ok"\]/);
  assert.match(source, /const by = \(status\) => d\.requests\?\.byStatus\?\.\[status\]/);
  assert.match(source, /errors\.requests[\s\S]*?role="alert"/);
  assert.match(source, /dash-consultation-status/);
  assert.doesNotMatch(source, /dash-progress-row|dash-meter|Math\.round\(\(v \/ sum\)/);
  assert.match(css, /\.dash-consultation-summary\s*\{[^}]*grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(css, /\.dash-consultation-status\.orange/);
  assert.match(css, /\.dash-consultation-status\.green/);
});

test("Admin staff management retains its scoped design and real staff actions", async () => {
  const source = await readFile(new URL("../src/pages/StaffManagement.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/StaffManagement.css", import.meta.url), "utf8");
  assert.match(source, /<main className="main admin-staff-main">[\s\S]*?<AdminTopBar[\s\S]*?className="staff-page staff-management-page"/);
  assert.match(source, /getUsersApi\(token\)/);
  assert.match(source, /createStaffCoachApi\(token/);
  assert.match(source, /createStaffReceptionistApi\(token/);
  assert.match(source, /updateStaffStatusApi\(token/);
  assert.match(source, /admin = role === "ADMIN"/);
  assert.match(source, /className="staff-toolbar-refresh" onClick=\{load\}/);
  assert.match(source, /staff-toolbar-create" aria-haspopup="dialog" aria-expanded=\{creating\} onClick=\{openCreateModal\}/);
  assert.match(css, /\.staff-page \.queue-table/);
  assert.match(css, /\.staff-page \.member-detail/);
  assert.match(css, /\.staff-management-page \{[^}]*width: 100%; min-width: 0; box-sizing: border-box;/);
  assert.doesNotMatch(css, /\.staff-management-page\s*\{[^}]*max-width:/);
  assert.doesNotMatch(css, /100vw/);
  assert.match(css, /\.staff-toolbar \{[^}]*grid-template-columns: minmax\(0, 1fr\) auto auto;[^}]*width: 100%; min-width: 0/);
  assert.doesNotMatch(source, /staff-toolbar-actions/);
  assert.doesNotMatch(css, /min-width:\s*(?:104|150)px/);
  assert.match(css, /\.staff-toolbar > button \{ min-width: 0; max-width: 100%;/);
  assert.match(css, /\.staff-page \.queue-table \{ width: 100%;[^}]*min-width: 0/);
  assert.match(css, /\.staff-page \.queue-table-wrap \{[\s\S]*width: 100%; max-width: 100%; min-width: 0; box-sizing: border-box;[\s\S]*overflow-x: auto/);
  assert.match(css, /\.staff-page \.form-card[\s\S]*width: 100%; max-width: none; box-sizing: border-box/);
  assert.match(css, /\.staff-page \.queue-table td:nth-child\(3\) \{ overflow-wrap: anywhere; \}/);
  assert.match(css, /\.staff-page \.member-detail-grid[\s\S]*repeat\(auto-fit, minmax\(220px, 1fr\)\)/);
  assert.match(css, /@media \(max-width: 480px\)[\s\S]*grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(css, /@media \(max-width: 1200px\)[\s\S]*\.staff-page \.queue-table \{ min-width: 900px; \}/);
  assert.match(css, /@media \(max-width: 760px\)/);
});
