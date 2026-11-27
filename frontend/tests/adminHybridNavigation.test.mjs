import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const src = new URL("../src/", import.meta.url);
const read = (file) => readFile(new URL(file, src), "utf8");

test("Admin operational support navigation exposes only existing staff-supported workflows", async () => {
  const [sidebar, app] = await Promise.all([read("components/Sidebar.jsx"), read("App.jsx")]);
  const groups = sidebar.match(/const adminGroups\s*=\s*\[([\s\S]*?)\n\];/)?.[1];
  const operational = groups?.match(/id: "operational-support",[\s\S]*?pages:\s*\[([\s\S]*?)\n    \],/)?.[1];
  assert.ok(operational, "Admin operational support group exists");

  for (const [page, path] of [
    ["consultations", "/staff/consultations"],
    ["support-tickets", "/staff/support-tickets"],
    ["course-registration", "/receptionist/course-registration"],
    ["admin-membership-sales", "/admin/membership-sales"],
    ["course-payments", "/receptionist/course-payments"],
  ]) {
    assert.match(operational, new RegExp(`\\["${page}"`), `${page} appears once in Admin operational support`);
    assert.equal((operational.match(new RegExp(`\\["${page}"`, "g")) || []).length, 1);
    const routeKey = /^[a-z]+$/.test(page) ? `${page}:` : `"${page}":`;
    assert.ok(app.includes(`${routeKey} "${path}"`), `${page} retains its route destination`);
  }

  assert.equal((operational.match(/\["course-payments"/g) || []).length, 1, "Admin has one unified payment navigation item");
  assert.match(operational, /\["course-payments",\s*"Thanh to\\u00e1n kh\\u00f3a h\\u1ecdc & PT"\]/, "Admin uses the established Receptionist label");
  assert.doesNotMatch(operational, /\["pt-payments"/, "PT Payments does not have a separate Admin Sidebar entry");
  assert.ok(app.includes('"pt-payments": "/receptionist/pt-payments"'), "PT Payments route remains available");
  assert.match(sidebar, /const navigationActivePage = activePage === "pt-payments" \? "course-payments" : activePage;/, "Admin PT route highlights the unified payment item");
  assert.match(sidebar, /const activeAdminGroup = adminPageByGroup\.get\(navigationActivePage\)/, "the operational group stays active on both payment routes");

  assert.match(app, /<Route element={<StaffRoute auth=\{auth\} \/>}>[\s\S]*?<Route path="\/receptionist\/course-registration"/, "staff routes remain behind the existing StaffRoute");
});

test("Admin operational support additions do not alter Receptionist, Coach, or Member navigation", async () => {
  const sidebar = await read("components/Sidebar.jsx");
  const list = (name) => sidebar.match(new RegExp(`const ${name}\\s*=\\s*\\[([\\s\\S]*?)\\n  \\];`))?.[1];
  const receptionist = list("receptionistLinks");
  const coach = list("coachLinks");
  const member = list("memberLinks");
  assert.ok(receptionist && coach && member);
  assert.match(receptionist, /\["course-payments",/);
  assert.match(receptionist, /\["consultations",/);
  assert.match(receptionist, /\["support-tickets",/);
  assert.match(coach, /\["coach-training",/);
  assert.match(member, /\["member-training",/);
  assert.doesNotMatch(receptionist, /\["admin-training"/);
  assert.doesNotMatch(coach, /\["admin-training"/);
  assert.doesNotMatch(member, /\["admin-training"/);
});

test("Admin overview matches the group heading typography while retaining grouped item sizing", async () => {
  const styles = await read("styles.css");
  const overviewRule = styles.match(/\.layout \.sidebar nav\.admin-navigation > \.nav-button\s*\{([^}]*)\}/)?.[1];
  const groupedRule = styles.match(/\.layout \.sidebar \.admin-nav-group-items \.nav-button\s*\{([^}]*)\}/)?.[1];
  const groupHeadingRule = styles.match(/\.layout \.sidebar \.admin-nav-group-toggle\s*\{([^}]*)\}/)?.[1];
  assert.ok(overviewRule, "only the direct Admin navigation item receives the sizing override");
  assert.ok(groupedRule, "grouped navigation sizing remains defined");
  assert.ok(groupHeadingRule, "Admin group heading remains defined");
  for (const declaration of ["font-size: 11px", "font-weight: 750", "letter-spacing: .08em", "text-transform: uppercase"]) {
    assert.ok(overviewRule.includes(declaration));
    assert.ok(groupHeadingRule.includes(declaration));
  }
  for (const declaration of ["padding: 9px 12px", "font-size: 13px"]) {
    assert.ok(groupedRule.includes(declaration));
  }
  assert.match(styles, /\.layout \.sidebar \.nav-button:hover,[\s\S]*?\.nav-button:focus-visible/);
  assert.match(styles, /\.layout \.sidebar \.nav-button\.active,[\s\S]*?\.nav-button\.active:hover/);
  assert.doesNotMatch(styles, /\.layout \.sidebar nav > \.nav-button\s*\{/);
});
