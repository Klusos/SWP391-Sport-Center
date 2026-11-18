import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Sport and Facility Management share the compact Admin section header", async () => {
  const sport = await read("../src/pages/SportManagement.jsx");
  const facility = await read("../src/pages/FacilityManagement.jsx");
  const header = await read("../src/components/AdminSectionHeader.jsx");
  const css = await read("../src/components/AdminSectionHeader.css");

  assert.equal((sport.match(/<AdminSectionHeader/g) || []).length, 1);
  assert.equal((facility.match(/<AdminSectionHeader/g) || []).length, 1);
  assert.match(header, /QUẢN TRỊ NỘI DUNG/);
  assert.match(header, /admin-section-meta/);
  assert.match(header, /admin-section-summary/);
  assert.match(css, /border-top: 1px solid #dce7f2/);
  assert.match(css, /\.admin-section-chip\s*\{/);
  assert.match(css, /@media \(max-width: 640px\)/);
  assert.match(css, /:focus-visible/);
});

test("Admin header pilot aliases are portal-scoped and preserve the exact existing values", async () => {
  const tokens = await read("../src/tokens/admin-pilot.css");
  const css = await read("../src/components/AdminSectionHeader.css");
  const main = await read("../src/main.jsx");

  assert.match(tokens, /\.layout\s*\{/);
  assert.match(tokens, /--sc-space-2:\s*var\(--space-2,\s*8px\)/);
  assert.match(tokens, /--sc-space-3:\s*var\(--space-3,\s*12px\)/);
  assert.match(tokens, /--sc-space-4:\s*var\(--space-4,\s*16px\)/);
  assert.match(tokens, /--sc-color-action:\s*var\(--blue,\s*#1769d2\)/);
  assert.match(tokens, /--sc-color-text-primary:\s*var\(--navy,\s*#0b2745\)/);
  assert.match(tokens, /--sc-color-text-secondary:\s*var\(--text-sub,\s*#5b6e82\)/);
  assert.match(css, /gap:\s*var\(--sc-space-2\)/);
  assert.match(css, /color:\s*var\(--sc-color-action\)/);
  assert.match(css, /color:\s*var\(--sc-color-text-primary\)/);
  assert.match(css, /color:\s*var\(--sc-color-text-secondary\)/);
  assert.match(main, /import "\.\/styles\.css";\s*import "\.\/tokens\/admin-pilot\.css";/);
  assert.doesNotMatch(tokens, /(^|\n)\s*(body|button|h1|\.lp|\.public-site)\s*\{/);
});

test("Sport header uses its live count and only offers cancel while editing", async () => {
  const source = await read("../src/pages/SportManagement.jsx");
  assert.match(source, /summary=\{<span className="admin-section-chip"><strong>\{sports\.length\}<\/strong> bộ môn<\/span>\}/);
  assert.match(source, /actions=\{selectedSport && <button[^>]*className="btn-secondary sport-admin-cancel" onClick=\{beginCreate\}/);
  assert.match(source, /Hủy chỉnh sửa/);
  assert.match(source, /getSportsApi\(token\)/);
});

test("Facility header keeps live summaries and reload while replacing redundant create with edit cancel", async () => {
  const source = await read("../src/pages/FacilityManagement.jsx");
  assert.match(source, /<strong>\{facilities\.length\}<\/strong> cơ sở/);
  assert.match(source, /<strong>\{sports\.length\}<\/strong> bộ môn đang hoạt động/);
  assert.match(source, /aria-label="Tải lại danh sách cơ sở" title="Tải lại danh sách cơ sở"/);
  assert.match(source, /onClick=\{\(\) => void load\(selectedId\)\}/);
  assert.match(source, /selectedFacility && <button[^>]*className="btn-secondary facility-cancel-edit" onClick=\{beginCreate\}/);
  assert.doesNotMatch(source, /Thêm cơ sở/);
  assert.match(source, /createFacilityApi\(token/);
  assert.match(source, /updateFacilityApi\(token/);
});
