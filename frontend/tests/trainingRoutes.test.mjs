import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
const src = new URL("../src/", import.meta.url);
const read = (file) => readFile(new URL(file, src), "utf8");
test("Training routes use the existing role guards and role-specific navigation", async () => {
  const app = await read("App.jsx");
  const sidebar = await read("components/Sidebar.jsx");
  for (const [guard, route] of [["MemberRoute", "/member/training"], ["CoachRoute", "/coach/training"], ["AdminRoute", "/admin/training"]]) {
    const guardMarker = '<Route element={<' + guard + ' auth={auth} />}>';
    const routeMarker = 'path="' + route + '"';
    assert.ok(app.includes(guardMarker), guard + " is registered");
    assert.ok(app.includes(routeMarker), route + " exists");
    assert.ok(app.indexOf(guardMarker) < app.indexOf(routeMarker), route + " is below " + guard);
  }
  assert.ok(sidebar.includes("member-training"));
  assert.ok(sidebar.includes("coach-training"));
  const adminGroups = sidebar.match(/const adminGroups\s*=\s*\[([\s\S]*?)\n\];/)?.[1];
  assert.ok(adminGroups);
  assert.match(adminGroups, /\["admin-training", "Giám sát huấn luyện"\]/);
  assert.equal((adminGroups.match(/\["admin-training"/g) || []).length, 1);
  assert.match(app, /path="\/admin\/training" element=\{<CoachTraining \{\.\.\.staff\} \/>\}/);
  const layout = await read("components/Layout.jsx");
  assert.match(layout, /"\/admin\/training": "admin-training"/);
});
