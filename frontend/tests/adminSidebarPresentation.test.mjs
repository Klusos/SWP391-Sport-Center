import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Admin navigation keeps grouped routes and pairs each visible item with a Bootstrap icon", async () => {
  const [sidebar, icons] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../node_modules/bootstrap-icons/font/bootstrap-icons.css"),
  ]);
  const adminGroups = sidebar.match(/const adminGroups\s*=\s*\[([\s\S]*?)\n\];/)?.[1];
  assert.ok(adminGroups);
  for (const group of ["operational-support", "catalog-management", "administration"]) {
    assert.ok(adminGroups.includes(`id: "${group}"`));
  }
  assert.match(sidebar, /function AdminNavigation\(/);
  assert.doesNotMatch(sidebar, /adminNavigationIcons\.dashboard|speedometer/);
  assert.match(sidebar, /icon=\{adminNavigationIcons\[page\]\}/);
  assert.match(sidebar, /aria-hidden="true"/);
  const iconNames = [...sidebar.matchAll(/:\s*"(bi-[a-z0-9-]+)"/g)].map((match) => match[1]);
  assert.ok(iconNames.length >= 15);
  for (const iconName of iconNames) {
    assert.ok(icons.includes(`.${iconName}::before`), `${iconName} exists in the installed Bootstrap Icons font`);
  }
  assert.match(adminGroups, /\["course-payments",\s*"Thanh to\\u00e1n kh\\u00f3a h\\u1ecdc & PT"\]/);
  assert.doesNotMatch(adminGroups, /\["pt-payments"/);
});

test("Admin group indicators are state-aware chevrons and retain disclosure semantics", async () => {
  const [sidebar, styles] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/styles.css"),
  ]);
  assert.match(sidebar, /aria-expanded=\{expanded\}/);
  assert.match(sidebar, /aria-controls=\{`admin-nav-\$\{group\.id\}`\}/);
  assert.match(sidebar, /expanded \? "bi-chevron-down" : "bi-chevron-right"/);
  assert.doesNotMatch(sidebar, /expanded \? "[+−-]"/);
  assert.match(sidebar, /id=\{`admin-nav-\$\{group\.id\}`\}[\s\S]*?hidden=\{!expanded\}/);
  assert.match(sidebar, /className=\{role === "ADMIN" \? "admin-navigation" : undefined\}/);
  assert.match(styles, /\.layout \.sidebar nav\.admin-navigation \.admin-nav-icon/);
  assert.match(styles, /\.layout \.sidebar \.admin-nav-chevron\s*\{[^}]*width: 16px;[^}]*flex: 0 0 16px;[^}]*place-items: center;/);
  assert.match(styles, /\.layout \.sidebar button:focus-visible/);
});

test("Admin Dashboard navigation is text-only and matches the group heading typography", async () => {
  const [sidebar, styles, app] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/styles.css"),
    read("../src/App.jsx"),
  ]);
  assert.match(sidebar, /page="dashboard" activePage=\{navigationActivePage\} onNavigate=\{onNavigate\}>/);
  assert.doesNotMatch(sidebar, /adminNavigationIcons\.dashboard|speedometer|admin-dashboard-icon/);
  const item = sidebar.match(/function Item\([\s\S]*?\n\}/)?.[0] || "";
  assert.doesNotMatch(item, /admin-dashboard-icon|iconAfter/);
  assert.match(item, /aria-current=\{activePage === page \? "page" : undefined\}/);
  assert.match(styles, /nav\.admin-navigation > \.nav-button\s*\{[^}]*font-size: 11px;[^}]*font-weight: 750;[^}]*letter-spacing: \.08em;[^}]*text-transform: uppercase;/);
  assert.match(styles, /nav\.admin-navigation > \.nav-button\.active[\s\S]*?background: rgba\(255, 255, 255, \.08\);/);
  assert.match(styles, /\.admin-nav-group-toggle\s*\{[^}]*font-size: 11px;[^}]*font-weight: 750;[^}]*letter-spacing: \.08em;[^}]*text-transform: uppercase;/);
  assert.match(app, /path="\/staff\/dashboard"\s+element=\{<Dashboard \{\.\.\.staff\} \/>\}/);
});
test("Bootstrap icon presentation is restricted to Admin navigation", async () => {
  const [sidebar, styles] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/styles.css"),
  ]);
  assert.match(sidebar, /links\.map\(\(\[page, text\]\) => \([\s\S]*?<Item key=\{page\} page=\{page\} activePage=\{activePage\} onNavigate=\{onNavigate\}>/);
  assert.match(styles, /\.layout \.sidebar nav\.admin-navigation \.admin-nav-item/);
  assert.doesNotMatch(styles, /admin-dashboard-nav-item|admin-dashboard-icon/);
  assert.doesNotMatch(styles, /\.layout \.sidebar \.nav-item\s*\{[^}]*admin-nav-icon/);
});

test("Membership Plans inherits the portal font token without changing public theme styles", async () => {
  const [plans, styles, publicTheme, app] = await Promise.all([
    read("../src/pages/MembershipPlans.css"),
    read("../src/styles.css"),
    read("../src/public-site/PublicThemeContext.jsx"),
    read("../src/App.jsx"),
  ]);
  assert.match(plans, /font-family:\s*var\(--font-sans\)/);
  assert.match(styles, /--font-sans:\s*"Plus Jakarta Sans"/);
  assert.doesNotMatch(plans, /font-family:\s*Inter,/);
  assert.match(publicTheme, /readThemePreference/);
  assert.match(app, /<ThemeProvider>/);
  assert.doesNotMatch(styles, /:root\[data-theme="dark"\]\s+(?:\.main|\.card|\.table|\.form|\.button)/);
  assert.doesNotMatch(styles, /data-portal-theme|data-theme="dark"/);
});
