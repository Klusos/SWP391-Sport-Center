import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("the shared Sidebar renders one keyboard-operable Logout footer after navigation for every role", async () => {
  const [sidebar, layout, app] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/components/Layout.jsx"),
    read("../src/App.jsx"),
  ]);

  assert.equal((sidebar.match(/className="nav-item clickable logout-button"/g) || []).length, 1);
  assert.match(
    sidebar,
    /<aside className="sidebar">[\s\S]*?<nav className=\{role === "ADMIN" \? "admin-navigation" : undefined\} aria-label=\{`Điều hướng \$\{label\}`\}>[\s\S]*?<\/nav>\s*<div className="nav-footer">\s*<button className="nav-item clickable logout-button" type="button" onClick=\{\(\) => onLogout\?\.\(\)\}>\s*Đăng xuất\s*<\/button>\s*<\/div>/,
  );
  assert.doesNotMatch(sidebar, /role\s*===\s*"(?:ADMIN|RECEPTIONIST|COACH|MEMBER)"[\s\S]{0,180}logout-button/);
  assert.match(layout, /<Sidebar[\s\S]*?onLogout=\{onLogout\}/);

  for (const name of ["staff", "coach", "member"]) {
    const roleProps = app.match(new RegExp(`const ${name} = \\{([\\s\\S]*?)\\n  \\};`))?.[1];
    assert.ok(roleProps, `${name} authenticated routes have a shared prop object`);
    assert.match(roleProps, /onLogout:\s*handleLogout/);
  }
  assert.match(app, /const staff = \{[\s\S]*?role: auth\?\.role,[\s\S]*?onLogout: handleLogout/);
  assert.match(app, /const coach = \{[\s\S]*?role: auth\?\.role,[\s\S]*?onLogout: handleLogout/);
  assert.match(app, /const member = \{[\s\S]*?role: auth\?\.role,[\s\S]*?onLogout: handleLogout/);
  assert.match(sidebar, /const memberLinks = \[/);
  assert.match(sidebar, /const coachLinks = \[/);
  assert.match(sidebar, /const receptionistLinks = \[/);
  assert.match(sidebar, /function AdminNavigation\(/);
  assert.match(sidebar, /role === "MEMBER"[\s\S]*?role === "COACH"[\s\S]*?role === "RECEPTIONIST"/);
});

test("desktop Sidebar keeps Logout in a fixed footer while navigation scrolls independently", async () => {
  const styles = await read("../src/styles.css");
  const sidebar = styles.match(/\.layout \.sidebar\s*\{\s*position:\s*sticky;([^}]+)\}/)?.[0] || "";
  const nav = styles.match(/\.layout \.sidebar nav\s*\{([^}]+)\}/)?.[1] || "";
  const navItem = styles.match(/\.layout \.sidebar \.nav-item\s*\{([^}]+)\}/)?.[1] || "";
  const footer = styles.match(/\.layout \.sidebar \.nav-footer\s*\{([^}]+)\}/g)?.find((rule) => rule.includes("flex: 0 0 auto")) || "";
  const portalSidebar = styles.match(/\.layout:has\(> \.portal-topbar\) > \.sidebar\s*\{([^}]+)\}/)?.[1] || "";

  assert.match(sidebar, /height:\s*100vh/);
  assert.match(sidebar, /min-height:\s*0/);
  assert.match(sidebar, /overflow:\s*hidden/);
  assert.match(nav, /flex:\s*1 1 auto/);
  assert.match(nav, /align-content:\s*start/);
  assert.match(nav, /min-height:\s*0/);
  assert.match(nav, /overflow-y:\s*auto/);
  assert.match(navItem, /display:\s*block/);
  assert.match(navItem, /padding:\s*11px 14px/);
  assert.doesNotMatch(navItem, /flex(?:-grow)?\s*:\s*1|height:\s*100%|align-self:\s*stretch/);
  assert.doesNotMatch(nav, /justify-content:\s*space-between/);
  assert.match(footer, /flex:\s*0 0 auto/);
  assert.match(styles, /\.layout \.nav-footer\s*\{[^}]*margin-top:\s*auto/);
  assert.match(portalSidebar, /height:\s*100vh/);
  assert.match(portalSidebar, /min-height:\s*0/);

  const mobileRules = styles.match(/@media \(max-width: 700px\)\s*\{([\s\S]*?)\n\}/)?.[1] || "";
  assert.match(mobileRules, /\.layout \.sidebar nav\s*\{\s*flex:\s*0 0 auto;\s*min-height:\s*auto;\s*overflow:\s*visible;/);
});
