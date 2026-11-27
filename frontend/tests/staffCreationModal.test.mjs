import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("staff creation is presented in a modal without changing the page flow", async () => {
  const page = await read("../src/pages/StaffManagement.jsx");

  assert.match(page, /staff-toolbar-create" aria-haspopup="dialog" aria-expanded=\{creating\} onClick=\{openCreateModal\}/);
  assert.match(page, /creating && createPortal\(<div className="staff-create-backdrop">[\s\S]*?role="dialog"[\s\S]*?aria-modal="true"/);
  assert.match(page, /T&#7841;o t&#224;i kho&#7843;n nh&#226;n s&#7921;/);
  assert.match(page, /className="queue-table-wrap"/);
  assert.doesNotMatch(page, /creating && <section className="form-card"/);
  assert.match(page, /onClick=\{closeCreateModal\} disabled=\{saving\}/);
  assert.match(page, /className="staff-create-cancel" onClick=\{closeCreateModal\}/);
  assert.match(page, /event\.key === "Escape" && !savingRef\.current/);
  assert.match(page, /if \(event\.key !== "Tab"\) return[\s\S]*?last\.focus\(\)[\s\S]*?first\.focus\(\)/);
  assert.match(page, /document\.body\.style\.overflow = "hidden"/);
  assert.match(page, /appRoot\.inert = true/);
  assert.match(page, /if \(appRoot\) appRoot\.inert = previousInert/);
  assert.match(page, /if \(previousFocus\?\.isConnected\) previousFocus\.focus\(\)/);
  assert.doesNotMatch(page, /<div className="staff-create-backdrop"[^>]*onClick=/);
});

test("the modal retains staff fields, Coach options, validation, and existing creation APIs", async () => {
  const page = await read("../src/pages/StaffManagement.jsx");

  for (const field of ["Lo&#7841;i t&#224;i kho&#7843;n", "H&#7885; t&#234;n", "Email", "&#272;i&#7879;n tho&#7841;i", "Gi&#7899;i t&#237;nh", "M&#7853;t kh&#7849;u kh&#7903;i t&#7841;o"]) {
    assert.ok(page.includes(field), `Expected creation field ${field}`);
  }
  assert.match(page, /createRole === "COACH" && <fieldset>/);
  assert.match(page, /createSpecializations\.map/);
  assert.match(page, /required maxLength=\{100\}/);
  assert.match(page, /required type="email" maxLength=\{150\}/);
  assert.match(page, /required type="password" minLength=\{8\}/);
  assert.match(page, /if \(saving\) return/);
  assert.match(page, /createStaffCoachApi\(token, \{/);
  assert.match(page, /createStaffReceptionistApi\(token, \{/);
  assert.match(page, /catch \(requestError\) \{\s*setCreateError/);
  assert.match(page, /setCreating\(false\);[\s\S]*?await load\(\);[\s\S]*?setNotice\(/);
  assert.match(page, /\{createError && <p id="staff-create-error"[^>]*role="alert">\{createError\}<\/p>\}/);
});

test("Audit Logs removes only the duplicate local eyebrow and retains its page content", async () => {
  const page = await read("../src/pages/AdminAuditLogs.jsx");

  assert.doesNotMatch(page, /admin-audit-eyebrow|SPORTCENTER \/ ADMIN/);
  assert.match(page, /<AdminTopBar[\s\S]*?pageLabel=/);
  assert.match(page, /<h1>Nhật ký hoạt động<\/h1>/);
  assert.match(page, /<p className="subtitle">/);
  assert.match(page, /onClick=\{refresh\}/);
  assert.match(page, /className="admin-audit-filters"/);
  assert.match(page, /className="admin-audit-table"/);
  assert.match(page, /admin-audit-pagination/);
});
