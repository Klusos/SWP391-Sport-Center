import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { shouldShowNotificationBell } from "../src/notificationVisibility.js";

test("authenticated identities do not mount notifications on the Landing homepage", () => {
  for (const role of ["ADMIN", "COACH", "RECEPTIONIST", "MEMBER"]) {
    assert.equal(shouldShowNotificationBell("/", { role, accessToken: `token-${role}` }), false);
  }
  assert.equal(shouldShowNotificationBell("/", null), false);
});

test("authenticated notification access remains enabled outside the Landing homepage", () => {
  for (const role of ["ADMIN", "COACH", "RECEPTIONIST", "MEMBER"]) {
    for (const pathname of ["/staff/dashboard", "/coach/dashboard", "/member/dashboard"]) {
      assert.equal(shouldShowNotificationBell(pathname, { role, accessToken: `token-${role}` }), true);
    }
  }
  assert.equal(shouldShowNotificationBell("/staff/dashboard", null), false);
});

test("App retains route-aware notification visibility and S3.1 identity remounting", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /shouldShowNotificationBell\(location\.pathname, auth\) && \(\s*<NotificationBell/);
  assert.match(app, /key=\{notificationIdentityKey\(auth\)\}/);
});
