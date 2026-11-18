import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const src = new URL("../src/", import.meta.url);
const read = (file) => readFile(new URL(file, src), "utf8");

test("Counter Course Registration shows AdminTopBar only for Admin and preserves the Receptionist header", async () => {
  const [page, app] = await Promise.all([
    read("pages/ReceptionistCourseRegistration.jsx"),
    read("App.jsx"),
  ]);

  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /currentUserName, onLogout, onNavigate/);
  assert.match(page, /const isAdmin = String\(role \|\| ""\)\.toUpperCase\(\) === "ADMIN"/);
  assert.match(page, /\{isAdmin && <AdminTopBar pageLabel="ĐĂNG KÝ KHÓA HỌC" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>\}/);
  assert.equal((page.match(/<AdminTopBar\b/g) || []).length, 1);
  assert.doesNotMatch(page, /SPORTCENTER \/ TIẾP NHẬN HỘI VIÊN/);
  assert.match(page, /<h1>Đăng ký khóa học tại quầy<\/h1>/);
  assert.doesNotMatch(page, /NotificationBell/);

  const sidebar = await read("components/Sidebar.jsx");
  assert.match(sidebar, /portalRole && <AdminTopBar/);
  assert.match(sidebar, /roleLabel=\{portalRole\.account\}/);

  const topBarIndex = page.indexOf("{isAdmin && <AdminTopBar");
  const contentIndex = page.indexOf("receptionist-registration-content");
  assert.ok(topBarIndex >= 0 && contentIndex > topBarIndex, "Top Bar precedes the padded content wrapper");
  assert.match(app, /const STAFF_ROLES = \["ADMIN", "RECEPTIONIST"\]/);
  assert.match(app, /<Route element=\{<StaffRoute auth=\{auth\} \/>\}>[\s\S]*?<Route path="\/receptionist\/course-registration" element=\{<ReceptionistCourseRegistration \{\.\.\.staff\} \/>\}/);
  assert.match(app, /currentUserName: auth\?\.name/);
});

test("Admin registration layout reset is page-scoped and has responsive content padding", async () => {
  const css = await read("pages/ReceptionistCourseRegistration.css");
  assert.match(css, /\.layout > main\.main\.receptionist-registration\.receptionist-registration--admin\s*\{[^}]*max-width:\s*none;\s*padding:\s*0;/);
  assert.match(css, /\.receptionist-registration-content--admin\s*\{[^}]*padding:\s*32px 48px 52px;/);
  assert.match(css, /@media \(max-width: 900px\)[\s\S]*?\.receptionist-registration-content--admin\s*\{[^}]*padding:\s*30px 26px 42px;/);
  assert.match(css, /@media \(max-width: 620px\)[\s\S]*?\.receptionist-registration-content--admin\s*\{[^}]*padding:\s*25px 16px 36px;/);
});
