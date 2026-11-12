import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  AUTH_STORAGE_KEY,
  clearLegacyTabAuth,
  clearSharedAuth,
  clearSharedAuthIfToken,
  readSharedAuth,
  subscribeToSharedAuth,
  withAuthTransitionLock,
  writeSharedAuth,
} from "../src/authStorage.js";

function memoryStorage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  };
}

function eventWindow(localStorage) {
  const listeners = new Map();
  return {
    localStorage,
    addEventListener(type, handler) {
      if (!listeners.has(type)) listeners.set(type, new Set());
      listeners.get(type).add(handler);
    },
    removeEventListener(type, handler) { listeners.get(type)?.delete(handler); },
    emit(type, event = { type }) { for (const handler of listeners.get(type) || []) handler(event); },
  };
}

const identity = (role, token = role.toLowerCase() + "-token") => ({
  accessToken: token,
  role,
  userId: role,
});

test("shared login identity is visible to every same-origin tab listener", () => {
  const storage = memoryStorage();
  const tabA = eventWindow(storage);
  const tabB = eventWindow(storage);
  let currentA = null;
  let currentB = null;
  subscribeToSharedAuth((auth) => { currentA = auth; }, tabA);
  subscribeToSharedAuth((auth) => { currentB = auth; }, tabB);

  const admin = identity("ADMIN");
  assert.equal(writeSharedAuth(admin, storage), true);
  tabA.emit("storage", { type: "storage", key: AUTH_STORAGE_KEY });
  tabB.emit("storage", { type: "storage", key: AUTH_STORAGE_KEY });

  assert.deepEqual(currentA, admin);
  assert.deepEqual(currentB, admin);
});

test("an account switch replaces the shared ADMIN identity with MEMBER", () => {
  const storage = memoryStorage({ [AUTH_STORAGE_KEY]: JSON.stringify(identity("ADMIN")) });
  const tab = eventWindow(storage);
  let observed = null;
  subscribeToSharedAuth((auth) => { observed = auth; }, tab);
  const member = identity("MEMBER");
  writeSharedAuth(member, storage);
  tab.emit("storage", { type: "storage", key: AUTH_STORAGE_KEY });
  assert.deepEqual(observed, member);
});

test("shared identity can switch between each supported role without role assumptions", () => {
  const storage = memoryStorage();
  const sequence = ["ADMIN", "MEMBER", "COACH", "RECEPTIONIST"];
  for (const role of sequence) {
    const auth = identity(role);
    assert.equal(writeSharedAuth(auth, storage), true);
    assert.equal(readSharedAuth(storage).role, role);
  }
});

test("logout removal propagates as a null identity", () => {
  const storage = memoryStorage({ [AUTH_STORAGE_KEY]: JSON.stringify(identity("COACH")) });
  const tab = eventWindow(storage);
  let observed = identity("COACH");
  subscribeToSharedAuth((auth) => { observed = auth; }, tab);
  clearSharedAuth(storage);
  tab.emit("storage", { type: "storage", key: AUTH_STORAGE_KEY, newValue: null });
  assert.equal(observed, null);
});

test("conditional logout cannot clear an identity published by a newer login", () => {
  const storage = memoryStorage({ [AUTH_STORAGE_KEY]: JSON.stringify(identity("MEMBER", "new-token")) });
  assert.equal(clearSharedAuthIfToken("old-token", storage), false);
  assert.equal(readSharedAuth(storage).accessToken, "new-token");
  assert.equal(clearSharedAuthIfToken("new-token", storage), true);
  assert.equal(readSharedAuth(storage), null);
});

test("authentication transitions use one exclusive cross-tab Web Lock", async () => {
  const calls = [];
  const target = { locks: { request: async (name, options, operation) => {
    calls.push([name, options.mode]);
    return operation();
  } } };
  assert.equal(await withAuthTransitionLock(async () => "done", target), "done");
  assert.deepEqual(calls, [["sportcenter-auth-transition", "exclusive"]]);
  await assert.rejects(withAuthTransitionLock(async () => {}, {}), /does not support|không hỗ trợ/i);
});

test("refresh and new tabs restore only the latest shared identity", () => {
  const latest = identity("RECEPTIONIST");
  const storage = memoryStorage({ [AUTH_STORAGE_KEY]: JSON.stringify(latest) });
  assert.deepEqual(readSharedAuth(storage), latest);
  const newTab = eventWindow(storage);
  assert.deepEqual(readSharedAuth(newTab.localStorage), latest);
});

test("legacy sessionStorage identity is explicitly removed and never restored", () => {
  const legacy = memoryStorage({ [AUTH_STORAGE_KEY]: JSON.stringify(identity("ADMIN")) });
  const shared = memoryStorage();
  clearLegacyTabAuth(legacy);
  assert.equal(legacy.getItem(AUTH_STORAGE_KEY), null);
  assert.equal(readSharedAuth(shared), null);
});

test("invalid shared records fail closed and do not authenticate", () => {
  for (const value of ["{bad json", JSON.stringify({ accessToken: "token", role: "ROOT" })]) {
    assert.equal(readSharedAuth(memoryStorage({ [AUTH_STORAGE_KEY]: value })), null);
  }
});

test("focus and page restoration recheck the authoritative shared record", () => {
  const storage = memoryStorage();
  const tab = eventWindow(storage);
  let observed = null;
  subscribeToSharedAuth((auth) => { observed = auth; }, tab);
  const member = identity("MEMBER");
  writeSharedAuth(member, storage);
  tab.emit("focus");
  assert.deepEqual(observed, member);
  clearSharedAuth(storage);
  tab.emit("pageshow");
  assert.equal(observed, null);
});

test("auth changes remount protected route state and retain existing route guards", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /<Routes key=\{auth\?\.accessToken \|\| "anonymous"\}>/);
  for (const guard of ["AdminRoute", "MemberRoute", "CoachRoute", "ReceptionistRoute"]) {
    assert.match(app, new RegExp("function " + guard + "\\("));
  }
  assert.match(app, /subscribeToSharedAuth/);
});

test("App does not restore per-tab session identity and clears the legacy key", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /clearLegacyTabAuth\(\)/);
  assert.match(app, /readSharedAuth\(\)/);
  assert.doesNotMatch(app, /sessionStorage\.getItem\(AUTH_STORAGE_KEY\)/);
  assert.doesNotMatch(app, /sessionStorage\.setItem\(AUTH_STORAGE_KEY/);
});

test("login and logout revoke with a serialized transition before publishing or clearing identity", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const login = app.slice(app.indexOf("async function handleLogin(email, password)"), app.indexOf("async function handleLogout"));
  const logout = app.slice(app.indexOf("async function handleLogout"), app.indexOf("const staff ="));
  assert.match(login, /withAuthTransitionLock/);
  assert.match(login, /await logoutApi\(previousAuth\.accessToken\)/);
  assert.ok(login.indexOf("await logoutApi(previousAuth.accessToken)") < login.indexOf("writeSharedAuth(data)"));
  assert.match(login, /writeSharedAuth\(data\)/);
  assert.match(logout, /withAuthTransitionLock/);
  assert.match(logout, /await logoutApi\(token\)/);
  assert.match(logout, /clearSharedAuthIfToken\(token\)/);
  assert.match(logout, /error\.status !== 401/);
  assert.doesNotMatch(logout, /clearSharedAuth\(\)/);
});

test("login form delegates credentials to App so account switching can be serialized", async () => {
  const login = await readFile(new URL("../src/pages/Login.jsx", import.meta.url), "utf8");
  assert.match(login, /await onLogin\(email, password\)/);
  assert.doesNotMatch(login, /loginApi/);
});

test("App logout uses exact backend endpoint and non-sensitive failure feedback", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
  assert.match(api, /request\("\/api\/auth\/logout"/);
  assert.match(api, /allowAuthGenerationChange: true, notifyUnauthorized: false/);
  assert.match(app, /Không thể xác nhận đăng xuất với máy chủ/);
  assert.match(app, /Phiên hiện tại vẫn được giữ/);
});

test("cross-tab synchronization does not write back and create an event loop", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const sync = app.slice(app.indexOf("subscribeToSharedAuth((nextAuth)"), app.indexOf("}), []);", app.indexOf("subscribeToSharedAuth((nextAuth)")));
  assert.match(sync, /setAuth\(nextAuth\)/);
  assert.doesNotMatch(sync, /writeSharedAuth|clearSharedAuth/);
});

test("API rejects stale-token requests and discards responses from an old identity", async () => {
  const api = await import("../src/api.js");
  const calls = [];
  let resolveFetch;
  globalThis.fetch = (...args) => {
    calls.push(args);
    return new Promise((resolve) => { resolveFetch = resolve; });
  };
  try {
    api.setActiveAuthToken("old-token");
    const oldRequest = api.getCurrentUserApi("old-token");
    api.setActiveAuthToken("new-token");
    resolveFetch(new Response(JSON.stringify({ id: "old-user" }), { status: 200 }));
    await assert.rejects(oldRequest, /Authentication changed while this request was in progress/);

    await assert.rejects(api.getCurrentUserApi("old-token"), /Authentication changed before this request could be sent/);
    assert.equal(calls.length, 1);

    let unauthorizedCalls = 0;
    api.setUnauthorizedHandler(() => { unauthorizedCalls += 1; });
    api.setActiveAuthToken("request-token");
    const pendingUnauthorized = api.getCurrentUserApi("request-token");
    api.setActiveAuthToken("newer-token");
    resolveFetch(new Response("{}", { status: 401 }));
    await assert.rejects(pendingUnauthorized, /Authentication changed while this request was in progress/);
    assert.equal(unauthorizedCalls, 0);
  } finally {
    delete globalThis.fetch;
    api.setActiveAuthToken(null);
    api.setUnauthorizedHandler(null);
  }
});

test("logout API sends only the captured bearer token and surfaces revocation failures", async () => {
  const api = await import("../src/api.js");
  const calls = [];
  let status = 204;
  globalThis.fetch = async (...args) => {
    calls.push(args);
    return new Response(status === 204 ? null : "", { status });
  };
  try {
    api.setActiveAuthToken("captured-token");
    await api.logoutApi("captured-token");
    assert.equal(calls[0][0], "http://localhost:8080/api/auth/logout");
    assert.equal(calls[0][1].method, "POST");
    assert.equal(calls[0][1].headers.Authorization, "Bearer captured-token");
    assert.equal(calls[0][1].body, undefined);

    status = 503;
    await assert.rejects(api.logoutApi("captured-token"), (error) => error.status === 503);
    status = 401;
    let unauthorizedCalls = 0;
    api.setUnauthorizedHandler(() => { unauthorizedCalls += 1; });
    await assert.rejects(api.logoutApi("captured-token"), (error) => error.status === 401);
    assert.equal(unauthorizedCalls, 0);
  } finally {
    delete globalThis.fetch;
    api.setActiveAuthToken(null);
    api.setUnauthorizedHandler(null);
  }
});
