import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { notificationIdentityKey } from "../src/notificationIdentity.js";

test("notification identity key changes between accounts and roles without exposing identity in markup", async () => {
  const accountA = { userId: 12, role: "ADMIN", accessToken: "token-a" };
  const accountB = { userId: 34, role: "COACH", accessToken: "token-b" };
  assert.notEqual(notificationIdentityKey(accountA), notificationIdentityKey(accountB));
  assert.notEqual(notificationIdentityKey(accountA), notificationIdentityKey({ ...accountA, role: "COACH" }));
  assert.equal(
    notificationIdentityKey(accountA),
    notificationIdentityKey({ ...accountA, accessToken: "rotated-token" }),
    "token refresh for the same account does not discard that account's notification view",
  );
  assert.notEqual(
    notificationIdentityKey({ role: "ADMIN", accessToken: "token-a" }),
    notificationIdentityKey({ role: "ADMIN", accessToken: "token-b" }),
    "token is a fallback when a stable account ID is unavailable",
  );
  assert.notEqual(
    notificationIdentityKey({ userId: "", role: "ADMIN", accessToken: "token-a" }),
    notificationIdentityKey({ userId: "", role: "ADMIN", accessToken: "token-b" }),
    "an empty account ID also falls back to the token",
  );
  assert.equal(notificationIdentityKey(null), "anonymous");

  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /<NotificationBell\s+key=\{notificationIdentityKey\(auth\)\}/);
});

test("notification requests and actions remain bound to the component identity's token", async () => {
  const component = await readFile(new URL("../src/components/NotificationBell.jsx", import.meta.url), "utf8");
  assert.match(component, /getNotificationsApi\(token, nextPage, 20\)/);
  assert.match(component, /getUnreadNotificationCountApi\(token\)/);
  assert.match(component, /markNotificationReadApi\(token, notification\.id\)/);
  assert.match(component, /markAllNotificationsReadApi\(token\)/);
});
