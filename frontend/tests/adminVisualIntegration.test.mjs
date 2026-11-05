import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Admin dashboard keeps real revenue data and uses scoped presentation", async () => {
  const source = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8");
  assert.match(source, /function Admin\(\{ d, token \}\)/);
  assert.match(source, /className="admin-dashboard"/);
  assert.match(source, /getAdminRevenueReportApi\(token/);
  assert.match(source, /AdminRevenueCard token=\{token\}/);
  assert.doesNotMatch(source, /Dữ liệu mẫu|months\.map\(/);
  assert.match(css, /\.admin-dashboard \.dash-stat/);
  assert.match(css, /\.admin-dashboard \.dash-grid\.wide/);
});

test("Admin staff management retains its scoped design and real staff actions", async () => {
  const source = await readFile(new URL("../src/pages/StaffManagement.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/StaffManagement.css", import.meta.url), "utf8");
  assert.match(source, /<main className="main staff-page">/);
  assert.match(source, /getUsersApi\(token\)/);
  assert.match(source, /createStaffCoachApi\(token/);
  assert.match(source, /createStaffReceptionistApi\(token/);
  assert.match(source, /updateStaffStatusApi\(token/);
  assert.match(source, /admin = role === "ADMIN"/);
  assert.match(css, /\.staff-page \.queue-table/);
  assert.match(css, /\.staff-page \.member-detail/);
});
