import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Member Dashboard membership shortcut opens the authenticated membership page", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");

  assert.match(dashboard, /function Member\(\{ d, onNavigate \}\)/);
  assert.match(dashboard, /<Link to="\/member\/membership"><b>Gói thành viên<\/b>/);
  assert.doesNotMatch(dashboard, /<Link to="\/tuyen-sinh"><b>Gói thành viên<\/b>/);
  assert.match(app, /function MemberRoute\(\{ auth \}\)/);
  assert.match(app, /auth\.role === "MEMBER" \? \([\s\S]*?<Outlet \/>\s*\) : \([\s\S]*?<Navigate to=\{home\(auth\.role\)\} replace \/>/);
});
