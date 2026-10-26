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
  for (const key of ["member-training", "coach-training", "admin-training"]) assert.ok(sidebar.includes(key));
});
