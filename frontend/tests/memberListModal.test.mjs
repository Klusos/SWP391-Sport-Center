import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Member details use an accessible portal modal and leave the table layout independent", async () => {
  const page = await read("../src/pages/MemberList.jsx");
  const css = await read("../src/pages/MemberList.css");

  assert.match(page, /detailOpen && createPortal\(/);
  assert.match(page, /role="dialog"[\s\S]*?aria-modal="true"[\s\S]*?aria-labelledby="member-detail-dialog-title"/);
  assert.match(page, /className="member-list-workspace"/);
  assert.doesNotMatch(page, /member-list-detail-column|has-selection|is-editing/);
  assert.match(css, /\.member-list-modal-dialog \{[^}]*width:min\(1080px,100%\)[^}]*max-height:[^}]*overflow-y:auto/);
  assert.match(css, /\.member-list-modal \{[^}]*background:rgb\(5 18 33 \/ 58%\)/);
  assert.match(css, /\.member-list-modal \.member-edit-grid \{[^}]*repeat\(2,minmax\(0,1fr\)\)/);
  assert.match(css, /\.member-list-modal \.member-edit-grid input,.member-list-modal \.member-edit-grid textarea,.member-list-modal \.member-edit-grid select \{[^}]*min-height:42px[^}]*border-radius:9px/);
  assert.match(css, /\.member-list-modal \.member-edit-grid input:focus-visible/);
  assert.match(css, /\.member-list-modal \.member-edit-grid button\[type="submit"\]:disabled/);
  assert.match(css, /input:user-invalid/);
  assert.match(css, /@media \(max-width:900px\)[\s\S]*?\.member-list-modal \.member-edit-grid \{ grid-template-columns:minmax\(0,1fr\); \}/);
  assert.match(css, /\.member-list-main \.member-table td:first-child \{[^}]*white-space:nowrap;[^}]*overflow-wrap:normal/);
});

test("modal locks background scroll and interaction, traps and restores focus, and closes safely", async () => {
  const page = await read("../src/pages/MemberList.jsx");

  assert.match(page, /document\.body\.style\.setProperty\("overflow", "hidden"\)/);
  assert.match(page, /scrollbarWidth = window\.innerWidth - document\.documentElement\.clientWidth/);
  assert.match(page, /document\.body\.style\.setProperty\("padding-right", `\$\{currentPaddingRight \+ scrollbarWidth\}px`\)/);
  assert.match(page, /if \(previousPaddingRight\) document\.body\.style\.setProperty\("padding-right", previousPaddingRight\)/);
  assert.match(page, /if \(appRoot\) appRoot\.inert = true/);
  assert.match(page, /if \(appRoot\) appRoot\.inert = previousInert/);
  assert.match(page, /detailCloseButtonRef\.current\?\.focus\(\)/);
  assert.match(page, /event\.key === "Escape" && !detailBusyRef\.current/);
  assert.match(page, /event\.key !== "Tab"/);
  assert.match(page, /last\.focus\(\)[\s\S]*?first\.focus\(\)/);
  assert.match(page, /if \(focusTarget\?\.isConnected\) focusTarget\.focus\(\)/);
  assert.match(page, /detailDirty && !window\.confirm\(/);
  assert.match(page, /if \(detailBusy\) return false/);
});

test("modal retains separate Account and Member Profile save handlers and validations", async () => {
  const page = await read("../src/pages/MemberList.jsx");

  assert.match(page, /<form className="form-card" onSubmit=\{saveAccount\}>/);
  assert.match(page, /<form className="form-card" onSubmit=\{saveProfile\}>/);
  assert.match(page, /required maxLength=\{100\}/);
  assert.match(page, /type="email" required maxLength=\{150\}/);
  assert.match(page, /min="50" max="250" step="0\.01" required/);
  assert.match(page, /min="10" max="500" step="0\.01" required/);
  assert.match(page, /disabled=\{accountSaving\}/);
  assert.match(page, /disabled=\{profileSaving\}/);
  assert.match(page, /setNotice\(/);
  assert.match(page, /setDetailError\(/);
});
