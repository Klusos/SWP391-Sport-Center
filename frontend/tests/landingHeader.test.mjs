import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Landing restores the compact manual Light/Dark toggle and keeps Login behavior", async () => {
  const [landing, app] = await Promise.all([read("../src/landing/LandingPage.jsx"), read("../src/App.jsx")]);
  const start = landing.indexOf("function Navbar(");
  const end = landing.indexOf("\nfunction Hero()", start);
  const navbar = landing.slice(start, end);
  assert.match(navbar, /function Navbar\(\{ theme, onToggleTheme, active \}\)/);
  assert.match(navbar, /className="lp-icon-btn"/);
  assert.match(navbar, /onClick=\{onToggleTheme\}/);
  assert.match(navbar, /theme === "dark" \? Sun : Moon/);
  assert.doesNotMatch(navbar, /Theo hệ thống|value="system"/);
  assert.match(navbar, /const accountPath = "\/login"/);
  assert.match(navbar, /const accountLabel =/);
  assert.equal((navbar.match(/<Link to=\{accountPath\}/g) || []).length, 2);
  assert.doesNotMatch(navbar, /dashboardPath|\bauth\b|\brole\b/);
  assert.match(app, /path="\/login"[\s\S]*?auth \?[\s\S]*?<Navigate to=\{home\(auth\.role\)\} replace \/>[\s\S]*?:\s*\([\s\S]*?<Login/);
});
