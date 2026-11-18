import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Sport Management adds one shared AdminTopBar while preserving its section header and workflows", async () => {
  const [page, css, app] = await Promise.all([
    read("../src/pages/SportManagement.jsx"),
    read("../src/pages/SportManagement.css"),
    read("../src/App.jsx"),
  ]);

  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /function SportManagement\(\{ token, role, currentUserName, onLogout, onNavigate \}\)/);
  assert.match(page, /<AdminTopBar pageLabel="BỘ MÔN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.equal([...page.matchAll(/<AdminTopBar\b/g)].length, 1);
  assert.match(page, /<AdminTopBar[\s\S]*?<div className="sport-management-content">[\s\S]*?<AdminSectionHeader/);
  assert.match(page, /role !== "ADMIN"/);
  assert.match(page, /createSportApi\(token/);
  assert.match(page, /updateSportApi\(token/);
  assert.match(page, /uploadSportImageApi\(token/);
  assert.match(page, /sport-admin-table/);
  assert.match(page, /sport-admin-editor/);
  assert.match(css, /\.sport-management-main\s*\{[^}]*padding:\s*0/);
  assert.match(css, /\.sport-management-content\s*\{[^}]*padding:\s*32px 48px/);
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*?\.sport-management-content\s*\{\s*padding:\s*24px 18px/);
  assert.match(app, /currentUserName: auth\?\.name/);
  assert.match(app, /path="\/staff\/sports"\s+element=\{<SportManagement \{\.\.\.staff\} \/>\}/);
  assert.match(app, /<Route element=\{<AdminRoute auth=\{auth\} \/>\}>[\s\S]*?path="\/staff\/sports"/);
  assert.doesNotMatch(page, /NotificationBell/);
});

test("Facility Management adds one shared AdminTopBar while preserving its section header and workflows", async () => {
  const [page, css, app] = await Promise.all([
    read("../src/pages/FacilityManagement.jsx"),
    read("../src/pages/FacilityManagement.css"),
    read("../src/App.jsx"),
  ]);

  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /function FacilityManagement\(\{ token, role, currentUserName, onLogout, onNavigate \}\)/);
  assert.match(page, /<AdminTopBar pageLabel="CƠ SỞ VẬT CHẤT" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.equal([...page.matchAll(/<AdminTopBar\b/g)].length, 1);
  assert.match(page, /<AdminTopBar[\s\S]*?<div className="facility-management-content">[\s\S]*?<AdminSectionHeader/);
  assert.match(page, /role !== "ADMIN"/);
  assert.match(page, /getFacilitiesApi\(token\)/);
  assert.match(page, /createFacilityApi\(token/);
  assert.match(page, /updateFacilityApi\(token/);
  assert.match(page, /facility-admin-table/);
  assert.match(page, /facility-sport-options/);
  assert.match(css, /\.facility-management-main\s*\{[^}]*padding:\s*0/);
  assert.match(css, /\.facility-management-content\s*\{[^}]*padding:\s*32px 48px/);
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*?\.facility-management-content\s*\{\s*padding:\s*24px 18px/);
  assert.match(app, /path="\/staff\/facilities"\s+element=\{<FacilityManagement \{\.\.\.staff\} \/>\}/);
  assert.match(app, /<Route element=\{<AdminRoute auth=\{auth\} \/>\}>[\s\S]*?path="\/staff\/facilities"/);
  assert.doesNotMatch(page, /NotificationBell/);
});

test("Top Bar rollout keeps one App-owned Bell and accepted Admin integrations", async () => {
  const [app, dashboard, reports, staff, audit, topBar] = await Promise.all([
    read("../src/App.jsx"),
    read("../src/pages/Dashboard.jsx"),
    read("../src/pages/ReportPage.jsx"),
    read("../src/pages/StaffManagement.jsx"),
    read("../src/pages/AdminAuditLogs.jsx"),
    read("../src/components/AdminTopBar.jsx"),
  ]);

  assert.equal([...app.matchAll(/<NotificationBell\b/g)].length, 1);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
  for (const source of [dashboard, reports, staff, audit]) assert.match(source, /<AdminTopBar\b/);
  assert.match(topBar, /function initialsFrom\(name\)/);
  assert.match(topBar, /displayName[\s\S]*?avatarName/);
  assert.match(reports, /className="report-admin-topbar"/);
  assert.match(await read("../src/pages/ReportPage.css"), /@media print[\s\S]*?\.report-admin-topbar\s*\{\s*display:\s*none/);
});
