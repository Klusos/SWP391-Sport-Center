import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { getPortalPageLabel, PORTAL_ROLE_PRESENTATION } from "../src/components/portalTopBarConfig.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("shared Portal Top Bar uses the authenticated role labels and page routes", () => {
  assert.deepEqual(PORTAL_ROLE_PRESENTATION, {
    RECEPTIONIST: { breadcrumb: "LỄ TÂN", account: "Lễ tân" },
    COACH: { breadcrumb: "HUẤN LUYỆN VIÊN", account: "Huấn luyện viên" },
    MEMBER: { breadcrumb: "THÀNH VIÊN", account: "Thành viên" },
  });
  assert.equal(getPortalPageLabel("/staff/consultations"), "YÊU CẦU TƯ VẤN");
  assert.equal(getPortalPageLabel("/receptionist/course-payments"), "THANH TOÁN KHÓA HỌC");
  assert.equal(getPortalPageLabel("/coach/pt-sessions"), "BUỔI HUẤN LUYỆN CÁ NHÂN");
  assert.equal(getPortalPageLabel("/member/courses/42"), "CHI TIẾT KHÓA HỌC");
  assert.equal(getPortalPageLabel("/member/training/42"), "KẾT QUẢ TẬP LUYỆN");
});

test("Sidebar supplies one shared Top Bar to non-Admin roles with the App identity", async () => {
  const [sidebar, app, topbar] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/App.jsx"),
    read("../src/components/AdminTopBar.jsx"),
  ]);
  assert.match(sidebar, /portalRole && <AdminTopBar/);
  assert.match(sidebar, /displayName=\{currentUserName\}/);
  assert.match(sidebar, /breadcrumbRoleLabel=\{portalRole\.breadcrumb\}/);
  assert.match(app, /<PortalIdentityProvider currentUserName=\{auth\?\.name\}>/);
  assert.equal((app.match(/<NotificationBell\b/g) || []).length, 1);
  assert.match(topbar, /breadcrumbRoleLabel = "QU\\u1ea2N TR\\u1eca"/);
  assert.match(topbar, /<span>\{breadcrumbRoleLabel\}<\/span>/);
});

test("Portal shell positions the shared header beside each role Sidebar and below it on narrow screens", async () => {
  const styles = await read("../src/styles.css");
  assert.match(styles, /\.layout:has\(> \.portal-topbar\)\s*\{\s*display: grid;/);
  assert.match(styles, /grid-template-columns: 272px minmax\(0, 1fr\)/);
  assert.match(styles, /\.layout:has\(> \.portal-topbar\) > \.portal-topbar \{ grid-column: 2; grid-row: 1;/);
  assert.match(styles, /\.layout:has\(> \.portal-topbar\) > \.main \{ grid-column: 2; grid-row: 2;/);
  assert.match(styles, /@media \(max-width: 700px\)[\s\S]*?\.layout:has\(> \.portal-topbar\) > \.portal-topbar \{ grid-column: 1; grid-row: 2;/);
});
