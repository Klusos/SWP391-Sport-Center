import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  assignSupportTicketToMeApi, createSupportTicketApi, getMySupportTicketsApi,
  getStaffSupportTicketsApi, getSupportTicketApi, replySupportTicketApi,
  updateSupportTicketStatusApi,
} from "../src/api.js";
import { supportTicketDateTime } from "../src/supportTicketPresentation.js";

test("support ticket API uses member and staff endpoints with authenticated paging and filters", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (...args) => { calls.push(args); return new Response("{}", { status: 200 }); };
  try {
    await getMySupportTicketsApi("member-token", { page: 1, size: 10 });
    assert.equal(new URL(calls[0][0]).pathname, "/api/support-tickets/me");
    assert.equal(new URL(calls[0][0]).search, "?page=1&size=10");
    assert.equal(calls[0][1].headers.Authorization, "Bearer member-token");
    await getStaffSupportTicketsApi("staff-token", { page: 2, size: 20, status: "OPEN", category: "PAYMENT", search: "  fee " });
    assert.equal(new URL(calls[1][0]).search, "?page=2&size=20&status=OPEN&category=PAYMENT&search=fee");
    assert.equal(calls[1][1].headers.Authorization, "Bearer staff-token");
    await getSupportTicketApi("member-token", 34);
    assert.equal(new URL(calls[2][0]).pathname, "/api/support-tickets/34");
  } finally { globalThis.fetch = originalFetch; }
});

test("support ticket writes send only ticket fields, reply text, or requested staff status", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (...args) => { calls.push(args); return new Response("{}", { status: 200 }); };
  try {
    await createSupportTicketApi("member-token", { subject: "Payment", category: "PAYMENT", description: "Need help" });
    assert.equal(new URL(calls[0][0]).pathname, "/api/support-tickets");
    assert.equal(calls[0][1].method, "POST");
    assert.deepEqual(JSON.parse(calls[0][1].body), { subject: "Payment", category: "PAYMENT", description: "Need help" });
    assert.equal("memberId" in JSON.parse(calls[0][1].body), false);
    await replySupportTicketApi("member-token", 34, "More details");
    assert.equal(new URL(calls[1][0]).pathname, "/api/support-tickets/34/messages");
    assert.deepEqual(JSON.parse(calls[1][1].body), { message: "More details" });
    await updateSupportTicketStatusApi("staff-token", 34, "RESOLVED");
    assert.equal(calls[2][1].method, "PATCH");
    assert.deepEqual(JSON.parse(calls[2][1].body), { status: "RESOLVED" });
    await assignSupportTicketToMeApi("staff-token", 34);
    assert.equal(new URL(calls[3][0]).pathname, "/api/support-tickets/34/assign-to-me");
  } finally { globalThis.fetch = originalFetch; }
});

test("timezone-less ticket timestamps are interpreted as Vietnam local time", () => {
  assert.equal(
    supportTicketDateTime("2026-10-20T10:00:00"),
    supportTicketDateTime("2026-10-20T03:00:00Z"),
  );
});

test("Support Tickets adds an Admin-only Top Bar without changing other role presentation or actions", async () => {
  const [page, css, app] = await Promise.all([
    readFile(new URL("../src/pages/SupportTickets.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/SupportTickets.css", import.meta.url), "utf8"),
    readFile(new URL("../src/App.jsx", import.meta.url), "utf8"),
  ]);
  assert.match(page, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(page, /isAdmin && <AdminTopBar pageLabel="YÊU CẦU HỖ TRỢ" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.match(page, /support-nonadmin-content/);
  assert.doesNotMatch(page, /NotificationBell/);
  assert.match(page, /updateSupportTicketStatusApi\(token, selectedId, next\)/);
  assert.match(page, /replySupportTicketApi\(token, selectedId, reply\.trim\(\)\)/);
  assert.match(page, /assignSupportTicketToMeApi\(token, selectedId\)/);
  assert.match(page, /createSupportTicketApi\(token/);
  assert.match(css, /support-admin-main \{ padding: 0; \}/);
  assert.match(css, /support-nonadmin-content \{ box-sizing: border-box; width: 100%; max-width: 1440px; margin: 0 auto; \}/);
  assert.match(css, /support-heading > \.btn-primary \{ width: auto; flex: 0 0 auto; white-space: nowrap; \}/);
  assert.match(app, /path="\/staff\/support-tickets" element=\{<SupportTickets \{\.\.\.staff\} \/>\}/);
  assert.match(app, /<Route element=\{<StaffRoute auth=\{auth\} \/>\}>[\s\S]*?path="\/staff\/support-tickets"/);
});
