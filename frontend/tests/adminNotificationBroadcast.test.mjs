import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Admin broadcast preserves audience, validation, confirmation, and API payload", async () => {
  const component = await readFile(new URL("../src/pages/AdminNotificationBroadcast.jsx", import.meta.url), "utf8");
  const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
  assert.match(component, /TITLE_LIMIT = 150/);
  assert.match(component, /MESSAGE_LIMIT = 5000/);
  assert.match(component, /!title\.trim\(\) \|\| !message\.trim\(\)/);
  assert.match(component, /window\.confirm\(/);
  assert.match(component, /broadcastNotificationsApi\(token, \{\s*title: title\.trim\(\),\s*message: message\.trim\(\),?\s*\}\)/);
  assert.match(api, /request\("\/api\/admin\/notifications\/broadcast", \{\s*method: "POST"/);
  assert.match(api, /body: JSON\.stringify\(payload\)/);
});

test("Admin broadcast keeps pending locks, accessible feedback, and guarded route", async () => {
  const component = await readFile(new URL("../src/pages/AdminNotificationBroadcast.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/AdminNotificationBroadcast.css", import.meta.url), "utf8");
  const adminGuard = app.indexOf('<Route element={<AdminRoute auth={auth} />}');
  const broadcastRoute = app.indexOf('path="/admin/notifications/broadcast"');
  assert.ok(adminGuard >= 0 && adminGuard < broadcastRoute);
  assert.match(component, /disabled=\{pending\}/);
  assert.match(component, /pending \|\| !title\.trim\(\) \|\| !message\.trim\(\)/);
  assert.match(component, /role="alert"/);
  assert.match(component, /role="status"/);
  assert.match(component, /catch \(requestError\)/);
  assert.match(css, /\.notification-broadcast-main \.nb-page/);
  assert.match(css, /@media \(max-width: 600px\)/);
  assert.match(css, /prefers-reduced-motion: reduce/);
});

test("Admin broadcast uses the shared top bar without taking notification ownership", async () => {
  const component = await readFile(new URL("../src/pages/AdminNotificationBroadcast.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const topBar = await readFile(new URL("../src/components/AdminTopBar.jsx", import.meta.url), "utf8");

  assert.match(component, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(component, /<AdminTopBar[\s\S]*?pageLabel="THÔNG BÁO"[\s\S]*?displayName=\{currentUserName\}[\s\S]*?avatarName=\{currentUserName\}/);
  assert.match(component, /<AdminTopBar[\s\S]*?<div className="nb-page">[\s\S]*?<h1>Gửi thông báo toàn trung tâm<\/h1>/);
  assert.doesNotMatch(component, /NOTIFICATION CENTER|nb-eyebrow|NotificationBell/);
  assert.match(topBar, /className="admin-topbar-account"/);
  assert.match(app, /currentUserName: auth\?\.name/);
  assert.match(app, /path="\/admin\/notifications\/broadcast"\s+element=\{<AdminNotificationBroadcast \{\.\.\.staff\} \/>\}/);
  assert.equal([...app.matchAll(/<NotificationBell\b/g)].length, 1);
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
});

test("Broadcast keeps its existing audience description and labeled message fields", async () => {
  const component = await readFile(new URL("../src/pages/AdminNotificationBroadcast.jsx", import.meta.url), "utf8");

  assert.match(component, /<strong>Đối tượng nhận thông báo<\/strong>/);
  for (const role of ["ADMIN", "RECEPTIONIST", "COACH", "MEMBER"]) {
    assert.match(component, new RegExp(`<span>${role}<\\/span>`));
  }
  assert.match(component, /<label htmlFor="broadcast-title">[\s\S]*?Tiêu đề/);
  assert.match(component, /<input[\s\S]*?id="broadcast-title"[\s\S]*?maxLength=\{TITLE_LIMIT\}/);
  assert.match(component, /<label htmlFor="broadcast-message">[\s\S]*?Nội dung thông báo/);
  assert.match(component, /<textarea[\s\S]*?id="broadcast-message"[\s\S]*?maxLength=\{MESSAGE_LIMIT\}/);
  assert.match(component, /onSubmit=\{submit\}/);
});
