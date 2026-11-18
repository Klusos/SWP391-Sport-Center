import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Admin registration is removed from Sidebar while its route and Member Management entry remain", async () => {
  const [sidebar, members, app] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/pages/MemberList.jsx"),
    read("../src/App.jsx"),
  ]);

  assert.doesNotMatch(sidebar, /\["register",/);
  assert.match(app, /register: "\/staff\/register-member"/);
  assert.match(app, /path="\/staff\/register-member"\s+element=\{auth\?\.role === "ADMIN" \? <RegisterMember \{\.\.\.staff\} \/>/);
  assert.match(members, /isAdmin && <button[^>]*onClick=\{\(\) => onNavigate\("register"\)\}/);
  assert.match(members, /isAdmin && <AdminTopBar pageLabel="DANH SÁCH THÀNH VIÊN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.doesNotMatch(members, /SPORTCENTER \/ THÀNH VIÊN/);
  assert.match(members, /<h1>Danh sách thành viên<\/h1>/);
});

test("Membership Plans adds AdminTopBar without replacing existing plan controls or data flows", async () => {
  const [page, app, css] = await Promise.all([
    read("../src/pages/MembershipPlans.jsx"),
    read("../src/App.jsx"),
    read("../src/pages/MembershipPlans.css"),
  ]);

  assert.match(app, /path="\/staff\/membership-plans"[\s\S]*?<MembershipPlans \{\.\.\.staff\} \/>/);
  assert.match(page, /admin && <AdminTopBar pageLabel="GÓI HỘI VIÊN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.equal([...page.matchAll(/<AdminTopBar\b/g)].length, 1);
  for (const marker of ["startCreate", "startEdit", "changeStatus", "getMembershipPlansApi(token)", "createMembershipPlanApi(token", "updateMembershipPlanApi(token"]) {
    assert.ok(page.includes(marker), `${marker} remains`);
  }
  assert.match(css, /\.membership-main \.membership-content\s*\{\s*padding:/);
});

test("Admin Attendance adds an Admin-only Top Bar and preserves existing attendance operations", async () => {
  const [page, app, css] = await Promise.all([
    read("../src/pages/StaffAttendance.jsx"),
    read("../src/App.jsx"),
    read("../src/pages/StaffAttendance.css"),
  ]);

  assert.match(app, /path="\/admin\/attendance"[\s\S]*?<StaffAttendance \{\.\.\.staff\} \/>/);
  assert.match(page, /role === "ADMIN" && <AdminTopBar pageLabel=/);
  assert.match(page, /loadSessions\(\)/);
  assert.match(page, /saveAll\(\)/);
  assert.match(page, /markAttendanceApi\(token/);
  assert.match(page, /openHistory\(attendanceId\)/);
  assert.match(page, /exportCsv\(\)/);
  assert.match(css, /\.staff-attendance-main\s*\{\s*padding:\s*0/);
  assert.match(css, /\.staff-attendance-content\s*\{\s*min-width:\s*0;\s*padding:/);
});

test("new page integrations reuse the App-owned Bell and leave existing AdminTopBar implementation untouched", async () => {
  const [app, topBar, members, plans, attendance] = await Promise.all([
    read("../src/App.jsx"),
    read("../src/components/AdminTopBar.jsx"),
    read("../src/pages/MemberList.jsx"),
    read("../src/pages/MembershipPlans.jsx"),
    read("../src/pages/StaffAttendance.jsx"),
  ]);

  assert.equal([...app.matchAll(/<NotificationBell\b/g)].length, 1);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
  for (const page of [members, plans, attendance]) assert.doesNotMatch(page, /NotificationBell/);
  assert.match(topBar, /function initialsFrom\(name\)/);
});
