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

test("Admin staff management retains its scoped design and real staff actions", async () => {
  const source = await readFile(new URL("../src/pages/StaffManagement.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/StaffManagement.css", import.meta.url), "utf8");
  assert.match(source, /<main className="main"><div className="staff-page staff-management-page">/);
  assert.match(source, /getUsersApi\(token\)/);
  assert.match(source, /createStaffCoachApi\(token/);
  assert.match(source, /createStaffReceptionistApi\(token/);
  assert.match(source, /updateStaffStatusApi\(token/);
  assert.match(source, /admin = role === "ADMIN"/);
  assert.match(source, /className="staff-toolbar-refresh" onClick=\{load\}/);
  assert.match(source, /staff-toolbar-create" onClick=\{\(\) => \{ setCreating/);
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
