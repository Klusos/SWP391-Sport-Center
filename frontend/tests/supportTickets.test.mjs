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
    await getMySupportTicketsApi("member-token", { page: 1, size: 5 });
    assert.equal(new URL(calls[0][0]).pathname, "/api/support-tickets/me");
    assert.equal(new URL(calls[0][0]).search, "?page=1&size=5");
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

test("Member Support uses an inbox layout and accessible create modal without changing ticket APIs", async () => {
  const [page, css] = await Promise.all([
    readFile(new URL("../src/pages/SupportTickets.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/SupportTickets.css", import.meta.url), "utf8"),
  ]);
  assert.match(page, /support-member-columns/);
  assert.match(page, /role="dialog" aria-modal="true" aria-labelledby="support-create-title"/);
  assert.match(page, /event\.key === "Escape"/);
  assert.match(page, /createDialogRef/);
  assert.match(page, /if \(previousFocus\?\.isConnected\) previousFocus\.focus\(\)/);
  assert.match(page, /setSubject\(""\); setDescription\(""\); setCategory\("GENERAL"\)/);
  assert.match(page, /detail\.status !== "CLOSED"/);
  assert.match(css, /\.support-member-content \.support-heading/);
  assert.match(css, /\.support-create-dialog/);
  assert.match(page, /getCurrentUserApi\(token\)/);
  assert.match(page, /Number\(message\.senderId\) === Number\(viewerId\)/);
  assert.match(page, /Number\(detail\.memberId\) === Number\(viewerId\)/);
  assert.match(page, /detail\?\.assignedStaffId != null && viewerId != null/);
  assert.match(page, /Number\.isFinite\(Number\(detail\.assignedStaffId\)\)/);
  assert.match(page, /className="support-conversation-actions"/);
  assert.match(page, /canReply && <button className="btn-primary" type="submit"/);
  assert.match(page, /window\.confirm\("Đánh dấu yêu cầu hỗ trợ này là đã giải quyết\?"\)/);
  assert.match(css, /\.support-message\.is-viewer \{ justify-self:end/);
  assert.match(css, /\.support-management-actions/);
  assert.match(css, /\.support-dialog-close \{[^}]*border:0/);
  assert.match(css, /\.support-dialog-close:focus-visible/);
  const sharedStyles = await readFile(new URL("../src/styles.css", import.meta.url), "utf8");
  const notificationCss = await readFile(new URL("../src/components/NotificationBell.css", import.meta.url), "utf8");
  assert.match(sharedStyles, /--sc-layer-notification:\s*1200/);
  assert.match(sharedStyles, /--sc-layer-modal:\s*1500/);
  assert.match(notificationCss, /z-index:\s*var\(--sc-layer-notification/);
  assert.match(css, /z-index:var\(--sc-layer-modal/);
  assert.match(page, /role === "RECEPTIONIST" \? " support-receptionist-heading"/);
  assert.match(page, /if \(next === "CLOSED" && !window\.confirm/);
  assert.match(page, /support-destructive/);
  assert.match(page, /getMySupportTicketsApi\(token, \{ page, size: 5 \}/);
  assert.match(page, /getStaffSupportTicketsApi\(token, \{ \.\.\.appliedFilters, page, size: 5 \}/);
  assert.match(page, /!member && canClaim && <button className="btn-primary"/);
  assert.match(page, /aria-label="Làm mới yêu cầu"/);
  assert.match(page, /<svg viewBox="0 0 24 24" width="20" height="20"/);
  assert.match(css, /\.support-detail \{ display:grid; grid-template-rows:auto auto minmax\(120px,1fr\) auto auto/);
  assert.match(css, /\.support-thread \{ min-height:0; max-height:none; overflow-y:auto/);
  assert.match(css, /\.support-dialog-close \{ position:absolute; top:14px; right:14px/);
});

test("Support keeps message bubbles content-sized and gates Receptionist replies on assignment", async () => {
  const [page, css] = await Promise.all([
    readFile(new URL("../src/pages/SupportTickets.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/SupportTickets.css", import.meta.url), "utf8"),
  ]);
  assert.match(page, /const canReply = member \|\| \(isAdmin && detail\?\.assignedStaffId != null\) \|\| assignedToViewer/);
  assert.match(page, /RECEPTIONIST: "Lễ tân", ADMIN: "Quản trị viên"/);
  assert.match(page, /detail\.status !== "CLOSED" && \(canReply \|\|/);
  assert.match(page, /feedbackArea === "reply" && actionError/);
  assert.match(page, /feedbackArea === "management" && actionError/);
  assert.match(page, /feedbackArea === "create" && actionError/);
  assert.doesNotMatch(page, /className="support-management-label"/);
  assert.match(css, /\.support-heading-actions > button \{ flex:0 0 auto; min-height:42px; white-space:nowrap; \}/);
  assert.match(css, /\.support-thread \{[^}]*align-content:start; align-items:start; grid-auto-rows:max-content;/);
  assert.match(css, /\.support-thread \.support-message \{ box-sizing:border-box; width:fit-content; max-width:min\(90%, 46rem\); min-width:0; \}/);
  assert.match(css, /\.support-conversation-actions button \{[^}]*width:176px/);
});
