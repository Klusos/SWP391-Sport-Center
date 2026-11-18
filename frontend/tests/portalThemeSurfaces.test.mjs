import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("authenticated Portal exposes no theme selector or dark presentation boundary", async () => {
  const [sidebar, app, topbar, topbarCss, dashboard, dashboardCss, globalCss, bellCss, tokens] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/App.jsx"),
    read("../src/components/AdminTopBar.jsx"),
    read("../src/components/AdminTopBar.css"),
    read("../src/pages/Dashboard.jsx"),
    read("../src/pages/Dashboard.css"),
    read("../src/styles.css"),
    read("../src/components/NotificationBell.css"),
    read("../src/tokens/theme-foundation.css"),
  ]);
  for (const source of [sidebar, app, topbar, topbarCss, dashboard, dashboardCss, globalCss, bellCss]) {
    assert.doesNotMatch(source, /data-portal-theme|portal-theme-toggle|showThemeToggle/);
  }
  assert.doesNotMatch(tokens, /--sc-portal-/);
  assert.doesNotMatch(globalCss, /:root\[data-theme="dark"\].*\.sidebar/);
  assert.doesNotMatch(topbarCss, /data-theme="dark"/);
  assert.doesNotMatch(bellCss, /data-theme="dark"/);
  assert.doesNotMatch(dashboardCss, /data-theme="dark"/);
});

test("Dashboard retains its existing Light layout and authenticated Top Bar identity", async () => {
  const [dashboard, styles, topbar] = await Promise.all([
    read("../src/pages/Dashboard.jsx"),
    read("../src/pages/Dashboard.css"),
    read("../src/components/AdminTopBar.jsx"),
  ]);
  assert.match(dashboard, /return <div className="layout">[\s\S]*?<main className="main rc-main dash-main">/);
  assert.match(dashboard, /pageLabel="TỔNG QUAN"/);
  assert.match(dashboard, /displayName=\{currentUserName \|\| roleName\}/);
  assert.match(dashboard, /avatarName=\{currentUserName\}/);
  assert.match(styles, /\.admin-dashboard \.dash-stat-admin > \.dash-stat-admin-label/);
  assert.match(styles, /\.admin-dashboard \.dash-stat-admin\s*\{/);
  assert.doesNotMatch(styles, /data-portal-theme|admin-dashboard-page/);
  assert.doesNotMatch(topbar, /PortalThemeToggle|showThemeToggle/);
});

test("Public theme compatibility remains global and the Bell remains App-owned", async () => {
  const [app, bell, provider, preference, publicCss] = await Promise.all([
    read("../src/App.jsx"),
    read("../src/components/NotificationBell.jsx"),
    read("../src/public-site/PublicThemeContext.jsx"),
    read("../src/themePreference.js"),
    read("../src/public-site/publicTheme.css"),
  ]);
  assert.match(app, /<ThemeProvider>/);
  assert.equal((app.match(/<NotificationBell\b/g) || []).length, 1);
  assert.match(provider, /preference,[\s\S]*?effectiveTheme: theme,[\s\S]*?setPreference/);
  assert.match(provider, /resolvePublicTheme\(preference\)/);
  assert.match(preference, /globalPreference === "dark" \? "dark" : "light"/);
  assert.match(publicCss, /\.lp\[data-theme="dark"\]/);
  assert.match(publicCss, /\.public-site\[data-theme="dark"\]/);
  assert.doesNotMatch(bell, /ThemeProvider|useTheme/);
});