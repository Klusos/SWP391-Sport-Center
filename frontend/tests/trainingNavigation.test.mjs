import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (file) => readFile(new URL("../src/" + file, import.meta.url), "utf8");

// Each training page must sit inside the guard for its role, i.e. after that guard opens and before the next one.
test("training routes are registered under the matching role guard", async () => {
  const app = await read("App.jsx");
  const guards = ["StaffRoute", "AdminRoute", "MemberRoute", "CoachRoute"]
    .map((name) => ({ name, at: app.indexOf("<Route element={<" + name + " auth={auth} />}>") }));
  for (const guard of guards) assert.ok(guard.at >= 0, guard.name + " is used");

  for (const [guardName, path] of [["AdminRoute", "/admin/training"], ["MemberRoute", "/member/training"], ["CoachRoute", "/coach/training"]]) {
    const routeAt = app.indexOf('path="' + path + '"');
    assert.ok(routeAt >= 0, path + " is routed");
    const enclosing = guards.filter((guard) => guard.at < routeAt).sort((a, b) => b.at - a.at)[0];
    assert.equal(enclosing.name, guardName, path + " is guarded by " + guardName);
  }
});

test("each role can navigate to its training page", async () => {
  const [app, sidebar, layout] = await Promise.all([read("App.jsx"), read("components/Sidebar.jsx"), read("components/Layout.jsx")]);
  for (const [key, path] of [["admin-training", "/admin/training"], ["member-training", "/member/training"], ["coach-training", "/coach/training"]]) {
    assert.ok(sidebar.includes('["' + key + '"'), key + " is in the sidebar");
    assert.ok(app.includes('"' + key + '": "' + path + '"'), key + " navigates to " + path);
    assert.ok(layout.includes('"' + path + '": "' + key + '"'), path + " highlights " + key);
  }
});
