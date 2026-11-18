import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Register Member keeps its API and required-field rules with Vietnamese admin copy", async () => {
  const source = await read("../src/pages/RegisterMember.jsx");
  const css = await read("../src/pages/RegisterMember.css");

  assert.match(source, /createMemberApi\(token, form\)/);
  for (const field of ["name", "email", "dob", "password"]) {
    assert.match(source, new RegExp(`htmlFor="${field}"[\\s\\S]*?required`));
  }
  assert.match(source, /minLength=\{8\}/);
  assert.match(source, /function handleInvalid\(event\)/);
  assert.match(source, /Vui lòng nhập địa chỉ email hợp lệ/);
  assert.match(source, /Vui lòng điền đầy đủ các trường bắt buộc/);
  assert.match(source, /onNavigate\("members"\)/);
  assert.match(source, /Đăng ký thành viên/);
  assert.match(source, /Họ và tên/);
  assert.match(source, /Ngày sinh/);
  assert.match(source, /Mật khẩu tạm thời/);
  assert.match(source, /Đang đăng ký…/);
  assert.match(css, /grid-template-columns: minmax\(0, 1fr\)/);
  assert.match(css, /:focus-visible/);
});

test("Sport Management retains create-mode behavior and centers its empty image fallback", async () => {
  const source = await read("../src/pages/SportManagement.jsx");
  const css = await read("../src/pages/SportManagement.css");

  assert.match(source, /function beginCreate\(\)/);
  assert.match(source, /selectedSport && <button type="button" className="btn-secondary sport-admin-cancel" onClick=\{beginCreate\}[^>]*>Hủy chỉnh sửa<\/button>/);
  assert.match(source, /sport-admin-edit" onClick=\{\(\) => selectSport\(sport\)\} aria-label=\{`Chỉnh sửa \$\{sport\.name\}`\} title=/);
  assert.match(source, /<svg className="sport-admin-edit-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">/);
  assert.match(source, /M12 20h9/);
  assert.doesNotMatch(source, /bi-pencil/);
  assert.match(source, /<th scope="col" aria-label="Thao tác" \/>/);
  assert.doesNotMatch(source, /className="btn-primary" onClick=\{beginCreate\}[^>]*>Tạo bộ môn mới/);
  assert.match(source, /createSportApi\(token/);
  assert.match(source, /uploadSportImageApi\(token/);
  assert.match(source, /Chưa có ảnh xem trước/);
  assert.match(css, /\.sport-admin-editor \.sport-admin-preview\.img-fallback/);
  assert.match(css, /align-items: center;[\s\S]*justify-content: center/);
  assert.match(css, /\.sport-admin-edit-icon\s*\{[^}]*stroke: currentColor/);
  assert.match(css, /\.sport-admin-table \.btn-secondary\.sport-admin-edit\s*\{[^}]*width: 44px;[^}]*border: 1px solid transparent;[^}]*background: transparent/);
  assert.match(css, /\.sport-admin-table \.sport-admin-edit:hover:not\(:disabled\)\s*\{[^}]*background: #edf5ff/);
  assert.match(css, /\.sport-admin-table \.sport-admin-edit:focus-visible/);
  assert.match(css, /\.sport-admin-table th:last-child\s*\{[^}]*padding-right: 22px;[^}]*text-align: right/);
  assert.match(css, /\.sport-admin-table td:last-child\s*\{[^}]*padding-right: 22px;[^}]*text-align: right/);
  assert.match(css, /\.sport-admin-actions\s*\{\s*justify-content: flex-end/);
});

test("Member List continues to expose complete contact values on truncated cells", async () => {
  const source = await read("../src/pages/MemberList.jsx");
  assert.match(source, /className="member-contact-cell" title=\{member\.email \|\| undefined\}/);
});

test("Admin Member Registration uses the shared Top Bar without duplicate page context", async () => {
  const source = await read("../src/pages/RegisterMember.jsx");
  const app = await read("../src/App.jsx");

  assert.match(source, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(source, /<AdminTopBar[\s\S]*?pageLabel="ĐĂNG KÝ THÀNH VIÊN"[\s\S]*?displayName=\{currentUserName\}[\s\S]*?avatarName=\{currentUserName\}/);
  assert.match(source, /<AdminTopBar[\s\S]*?<div className="register-member-content">[\s\S]*?<h1>Đăng ký thành viên<\/h1>/);
  assert.match(source, /Tạo tài khoản thành viên mới trong hệ thống SportCenter/);
  assert.match(source, /onNavigate\("members"\)/);
  assert.doesNotMatch(source, /SPORTCENTER \/ QUẢN TRỊ \/ THÀNH VIÊN|register-member-eyebrow|NotificationBell/);
  assert.match(app, /path="\/staff\/register-member"\s+element=\{auth\?\.role === "ADMIN" \? <RegisterMember \{\.\.\.staff\} \/> : <Navigate to="\/staff\/consultations" replace \/>\}/);
  assert.match(app, /currentUserName: auth\?\.name/);
});
