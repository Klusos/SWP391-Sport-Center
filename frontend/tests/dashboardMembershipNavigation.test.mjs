import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Member Dashboard membership shortcut opens the authenticated membership page", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");

  assert.match(dashboard, /function Member/);
  // The fe2 member dashboard reaches the membership page through onNavigate, never the public admission page.
  assert.match(dashboard, /onNavigate\("member-membership"\)/);
  assert.doesNotMatch(dashboard, /to="\/tuyen-sinh"/);
  assert.match(app, /"member-membership": "\/member\/membership"/);
  assert.match(app, /function MemberRoute\(\{ auth \}\)/);
  assert.match(app, /auth\.role === "MEMBER" \? \([\s\S]*?<Outlet \/>\s*\) : \([\s\S]*?<Navigate to=\{home\(auth\.role\)\} replace \/>/);
});


test("redesigned dashboard uses effective VN date membership and avoids the mutating registration endpoint", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  assert.match(dashboard, /isMembershipEffective\(membership, todayInVietnam\(\)\)/);
  assert.match(dashboard, /getMyClassSessionsApi/);
  assert.doesNotMatch(dashboard, /getMyRegistrationsApi/);
  assert.match(dashboard, /scheduledClassIds\.size/);
  assert.match(dashboard, /errors\.profile \?/);
  assert.match(dashboard, /onNavigate\("member-membership"\)/);
  assert.doesNotMatch(dashboard, /member-payments/);
  assert.match(dashboard, /getAdminRevenueReportApi/);
});
