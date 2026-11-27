import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Audit Logs reuses AdminTopBar and preserves its content and local layout", async () => {
  const page = await read("../src/pages/AdminAuditLogs.jsx");
  const css = await read("../src/pages/AdminAuditLogs.css");
  const app = await read("../src/App.jsx");

  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /<AdminTopBar pageLabel=\{"NH\\u1eacT K\\u00dd"\} displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.match(page, /activePage="audit-logs"/);
  assert.match(page, /className="admin-audit-header admin-page-header"[\s\S]*?<h1>Nhật ký hoạt động<\/h1>/);
  assert.doesNotMatch(page, /admin-audit-eyebrow|SPORTCENTER \/ ADMIN/);
  assert.match(page, /admin-audit-filters[\s\S]*?admin-audit-results/);
  assert.doesNotMatch(page, /NotificationBell/);
  assert.match(css, /\.admin-audit-main \{ min-width: 0; padding: 0; \}/);
  assert.match(css, /\.admin-audit-content \{ min-width: 0; padding: 32px 48px; \}/);
  assert.match(app, /path="\/admin\/audit-logs"\s+element=\{<AdminAuditLogs \{\.\.\.staff\} \/>\}/);
  assert.match(app, /currentUserName: auth\?\.name/);
  assert.match(app, /<Route element=\{<AdminRoute auth=\{auth\} \/>\}>[\s\S]*?path="\/admin\/audit-logs"/);
});

test("Staff Management adds the shared bar only to its Admin view", async () => {
  const page = await read("../src/pages/StaffManagement.jsx");
  const css = await read("../src/pages/StaffManagement.css");
  const app = await read("../src/App.jsx");

  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /if \(!admin\) \{[\s\S]*?return <div className="layout"[\s\S]*?<main className="main">/);
  assert.match(page, /<AdminTopBar pageLabel=\{"NH\\u00c2N S\\u1ef0"\} displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.match(page, /activePage="staff"/);
  assert.match(page, /className="admin-staff-content">[\s\S]*?className="staff-page staff-management-page"/);
  assert.doesNotMatch(page, /NotificationBell/);
  assert.match(css, /\.admin-staff-main \{ min-width: 0; padding: 0; \}/);
  assert.match(css, /\.admin-staff-content \{ min-width: 0; padding: 32px 48px; \}/);
  assert.match(app, /path="\/staff\/staff"\s+element=\{<StaffManagement \{\.\.\.staff\} \/>\}/);
  assert.match(app, /<Route element=\{<AdminRoute auth=\{auth\} \/>\}>[\s\S]*?path="\/staff\/staff"/);
});

test("the rollout keeps the accepted Dashboard and Reports integrations and print selectors", async () => {
  const dashboard = await read("../src/pages/Dashboard.jsx");
  const reports = await read("../src/pages/ReportPage.jsx");
  const reportCss = await read("../src/pages/ReportPage.css");
  const app = await read("../src/App.jsx");

  assert.match(dashboard, /<AdminTopBar[\s\S]*?pageLabel="TỔNG QUAN"/);
  assert.match(reports, /className="report-admin-topbar"/);
  assert.match(reportCss, /@media print[\s\S]*?\.report-admin-topbar\s*\{\s*display:\s*none;/);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
  assert.equal([...app.matchAll(/<NotificationBell\b/g)].length, 1);
  assert.doesNotMatch(dashboard + reports, /<NotificationBell/);
});

test("Admin management routes use the framed header pattern while dashboards remain specialized", async () => {
  const app = await read("../src/App.jsx");
  const routes = [
    ["../src/pages/StaffAttendance.jsx", /coach-attendance-header/],
    ["../src/pages/CourseList.jsx", /admin-page-header/],
    ["../src/pages/CourseForm.jsx", /admin-page-header/],
    ["../src/pages/CourseManagementPages.jsx", /course-detail-heading admin-page-header/],
    ["../src/pages/MembershipPlans.jsx", /admin-page-header/],
    ["../src/pages/MemberList.jsx", /admin-page-header/],
    ["../src/pages/StaffManagement.jsx", /admin-page-header/],
    ["../src/pages/AdminAuditLogs.jsx", /admin-audit-header admin-page-header/],
    ["../src/pages/AdminNotificationBroadcast.jsx", /nb-header admin-page-header/],
    ["../src/pages/ReportPage.jsx", /page-intro admin-page-header/],
    ["../src/pages/ReceptionistMembershipSales.jsx", /membership-sales-header[^\n]*admin-page-header/],
    ["../src/pages/ReceptionistCoursePayments.jsx", /cp-hero-header[^\n]*admin-page-header/],
    ["../src/pages/ReceptionistPtPayments.jsx", /cp-hero-header[^\n]*admin-page-header/],
    ["../src/pages/RegisterMember.jsx", /register-member-header[^\n]*admin-page-header/],
  ];
  for (const [path, pattern] of routes) assert.match(await read(path), pattern, path);
  assert.match(await read("../src/pages/SportManagement.jsx"), /AdminSectionHeader/);
  assert.match(await read("../src/pages/FacilityManagement.jsx"), /AdminSectionHeader/);
  assert.match(await read("../src/pages/SupportTickets.css"), /\.support-admin-content \.support-heading[^{]*\{[^}]*background:linear-gradient/);
  assert.match(app, /path="\/admin\/attendance"[\s\S]*?path="\/staff\/staff"[\s\S]*?path="\/staff\/sports"[\s\S]*?path="\/staff\/facilities"[\s\S]*?path="\/staff\/courses"[\s\S]*?path="\/admin\/notifications\/broadcast"[\s\S]*?path="\/admin\/audit-logs"[\s\S]*?path="\/admin\/reports"/);
});
