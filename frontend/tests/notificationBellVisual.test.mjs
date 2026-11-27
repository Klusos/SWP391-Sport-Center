import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Notification Bell remains keyboard accessible and above dashboard content", async () => {
  const source = await readFile(new URL("../src/components/NotificationBell.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/components/NotificationBell.css", import.meta.url), "utf8");
  assert.match(source, /type="button" className="notification-bell" aria-label=/);
  assert.match(source, /<svg className="notification-bell-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">/);
  assert.match(source, /<path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9Z" \/>/);
  assert.doesNotMatch(source, /className="bi bi-bell"/);
  assert.match(source, /aria-expanded=\{open\}/);
  assert.match(source, /event\.key !== "Escape"/);
  assert.match(css, /\.notification-widget\s*\{[^}]*position: fixed;[^}]*z-index: var\(--sc-layer-notification,\s*1200\)/);
  assert.match(css, /\.notification-panel\s*\{[^}]*width: min\(390px, calc\(100vw - 24px\)\)/);
  assert.match(css, /\.notification-item:focus-visible/);
  assert.match(css, /\.notification-bell-icon\s*\{[^}]*stroke: currentColor;[^}]*stroke-linecap: round;/);
  assert.match(css, /\.notification-bell:hover \.notification-bell-icon/);
  assert.match(css, /@media \(max-width: 600px\)/);
  assert.match(css, /right: 0;/);
});
