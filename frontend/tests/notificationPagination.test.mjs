import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { shouldShowNotificationPagination } from "../src/notificationPagination.js";

test("notification pagination is hidden when there are zero or one pages", () => {
  assert.equal(shouldShowNotificationPagination(null), false);
  assert.equal(shouldShowNotificationPagination({ totalPages: 0 }), false);
  assert.equal(shouldShowNotificationPagination({ totalPages: 1 }), false);
});

test("notification pagination is shown when multiple pages exist", () => {
  assert.equal(shouldShowNotificationPagination({ totalPages: 2 }), true);
  assert.equal(shouldShowNotificationPagination({ totalPages: 5 }), true);
});

test("pagination retains backend navigation and unread actions", async () => {
  const component = await readFile(new URL("../src/components/NotificationBell.jsx", import.meta.url), "utf8");
  assert.match(component, /shouldShowNotificationPagination\(page\)/);
  assert.match(component, /loadPage\(pageNumber - 1\)/);
  assert.match(component, /loadPage\(pageNumber \+ 1\)/);
  assert.match(component, /markNotificationReadApi/);
  assert.match(component, /markAllNotificationsReadApi/);
  assert.match(component, /refreshCount\(true\)/);
});

test("Admin Classes redirects under AdminRoute and real course routes remain registered", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const adminGuard = app.indexOf('<Route element={<AdminRoute auth={auth} />}');
  const adminClasses = app.indexOf('path="/admin/classes"');
  const staffCourses = app.indexOf('path="/staff/courses"');
  assert.ok(adminGuard >= 0 && adminGuard < adminClasses, "Admin Classes remains under AdminRoute");
  assert.match(app.slice(adminClasses, adminClasses + 180), /Navigate to="\/staff\/courses" replace/);
  assert.ok(staffCourses >= 0, "real course list route remains registered");
  assert.match(app, /path="\/staff\/courses\/:courseId"/);
});
