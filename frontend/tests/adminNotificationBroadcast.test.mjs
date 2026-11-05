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
