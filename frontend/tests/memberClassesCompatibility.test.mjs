import assert from "node:assert/strict";
import { access, readFile } from "node:fs/promises";
import test from "node:test";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("legacy Member Classes URL redirects to Schedule with replace inside MemberRoute", async () => {
  const app = await read("../src/App.jsx");
  const guardStart = app.indexOf('<Route element={<MemberRoute auth={auth} />}>');
  const guardEnd = app.indexOf('<Route element={<CoachRoute auth={auth} />}>', guardStart);
  assert.ok(guardStart >= 0 && guardEnd > guardStart, "MemberRoute is present before CoachRoute");
  const memberRoutes = app.slice(guardStart, guardEnd);
  assert.match(memberRoutes, /<Route path="\/member\/classes" element=\{<Navigate to="\/member\/schedule" replace \/>\} \/>/);
  assert.equal((memberRoutes.match(/path="\/member\/classes"/g) || []).length, 1);
});

test("legacy demo component is no longer imported, mounted, or present", async () => {
  const app = await read("../src/App.jsx");
  assert.doesNotMatch(app, /MemberPage|member-classes/);
  await assert.rejects(access(new URL("../src/pages/MemberPage.jsx", import.meta.url)));
});

test("Member navigation keeps Courses, Schedule, and Training but adds no Classes item", async () => {
  const [sidebar, app, layout] = await Promise.all([
    read("../src/components/Sidebar.jsx"),
    read("../src/App.jsx"),
    read("../src/components/Layout.jsx"),
  ]);
  const memberLinks = sidebar.match(/const memberLinks = \[([\s\S]*?)\n  \];/)?.[1];
  assert.ok(memberLinks);
  assert.match(memberLinks, /\["member-courses",/);
  assert.match(memberLinks, /\["member-schedule",/);
  assert.match(memberLinks, /\["member-training",/);
  assert.doesNotMatch(memberLinks, /classes|member-classes/);
  assert.doesNotMatch(layout, /\/member\/classes|member-classes/);
  assert.match(app, /path="\/member\/schedule"[\s\S]*?<Schedule \{\.\.\.member\} \/>/);
  assert.match(app, /path="\/member\/courses"[\s\S]*?<MemberCourses \{\.\.\.member\} \/>/);
  assert.match(app, /path="\/member\/training"[\s\S]*?<MemberTraining \{\.\.\.member\} \/>/);
});

test("Member-only guard and unrelated role/public routes remain registered", async () => {
  const app = await read("../src/App.jsx");
  const memberGuard = app.indexOf('<Route element={<MemberRoute auth={auth} />}>');
  const legacyRoute = app.indexOf('path="/member/classes"');
  const coachGuard = app.indexOf('<Route element={<CoachRoute auth={auth} />}>', memberGuard);
  assert.ok(legacyRoute > memberGuard && legacyRoute < coachGuard);
  for (const route of ["/staff/dashboard", "/staff/consultations", "/receptionist/course-payments", "/coach/dashboard", "/", "/bo-mon"]) {
    assert.ok(app.includes(`path="${route}"`), `${route} remains registered`);
  }
  assert.match(app, /<NotificationBell\b/);
  assert.match(app, /<ThemeProvider>/);
});