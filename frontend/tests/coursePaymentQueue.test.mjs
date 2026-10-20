import test from "node:test";
import assert from "node:assert/strict";
import { getPendingCoursePaymentsApi, processCounterPaymentApi } from "../src/api.js";
import { canViewCoursePayments, coursePaymentExpired, coursePaymentMoney, coursePaymentTime, createCoursePaymentQueue } from "../src/coursePaymentQueue.js";

const row = { paymentId: 42, memberId: 12, memberName: "Member Example", registrationId: 31,
  courseName: "Swimming", className: "Morning class", amount: 500000, paymentStatus: "PENDING",
  registrationStatus: "PENDING_PAYMENT", paymentDeadline: "2099-10-20T10:00:00" };
const page = { content: [row], totalPages: 1, totalElements: 1, last: true };
const payment = (status) => ({ id: 42, memberId: 12, referenceId: 31, purpose: "COURSE_REGISTRATION", method: "COUNTER", status });
function setup(overrides = {}) {
  const events = { queues: [], outcomes: [], busy: [], dialogs: [], posts: [], loads: [], stored: [] };
  const controller = createCoursePaymentQueue({
    fetchQueue: async (...args) => { events.loads.push(args); return page; },
    settle: async (...args) => { events.posts.push(args); return payment("SUCCESS"); },
    fetchHistory: async () => [],
    confirm: (text) => { events.dialogs.push(text); return true; },
    onQueue: (value) => events.queues.push(value), onBusy: (value) => events.busy.push(value),
    onOutcome: (value) => events.outcomes.push(value), saveUncertain: (value) => events.stored.push(value),
    ...overrides,
  });
  return { controller, events };
}

test("queue helper sends authenticated page/size and settlement helper sends exact PATCH body", async () => {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (...args) => { calls.push(args); return new Response(JSON.stringify(page), { status: 200 }); };
  try {
    await getPendingCoursePaymentsApi("test-token", 2, 20);
    assert.equal(new URL(calls[0][0]).pathname, "/api/payments/pending-courses");
    assert.equal(new URL(calls[0][0]).search, "?page=2&size=20");
    assert.equal(calls[0][1].headers.Authorization, "Bearer test-token");
    await processCounterPaymentApi("test-token", 42, true, "Course payment received at reception");
    assert.equal(new URL(calls[1][0]).pathname, "/api/payments/42/counter");
    assert.equal(calls[1][1].method, "PATCH");
    assert.deepEqual(JSON.parse(calls[1][1].body), { success: true, note: "Course payment received at reception" });
  } finally { globalThis.fetch = original; }
});

test("queue load publishes loading then empty data without settlement", async () => {
  const { controller, events } = setup({ fetchQueue: async () => ({ ...page, content: [], totalPages: 0, totalElements: 0 }) });
  await controller.load();
  assert.equal(events.queues[0].loading, true);
  assert.equal(events.queues.at(-1).loading, false);
  assert.equal(events.queues.at(-1).data.content.length, 0);
  assert.equal(events.posts.length, 0);
  controller.dispose();
});

test("only ADMIN and RECEPTIONIST may render course-payment controls", () => {
  assert.equal(canViewCoursePayments("ADMIN"), true);
  assert.equal(canViewCoursePayments("RECEPTIONIST"), true);
  for (const role of [undefined, "MEMBER", "COACH"]) assert.equal(canViewCoursePayments(role), false);
});

test("amount uses VND and timezone-less deadlines are parsed as Vietnam time", () => {
  assert.match(coursePaymentMoney(500000), /500\.000/);
  assert.match(coursePaymentMoney(500000), /₫/);
  assert.equal(coursePaymentTime("2026-10-20T10:00:00"), coursePaymentTime("2026-10-20T03:00:00Z"));
  assert.equal(coursePaymentExpired(row, new Date("2099-10-20T03:00:00Z").getTime()), true);
});

test("confirmation presents receipt details and canceling the dialog never posts", async () => {
  const { controller, events } = setup({ confirm: (text) => { events.dialogs.push(text); return false; } });
  await controller.receive(row);
  for (const expected of [row.memberName, row.courseName, row.className, "42", "500.000", "nhận đủ tiền"]) assert.ok(events.dialogs[0].includes(expected));
  assert.equal(events.posts.length, 0);
  controller.dispose();
});

test("settlement cannot start before dialog acceptance and duplicate clicks post once", async () => {
  let release;
  const { controller, events } = setup({
    confirm: () => { assert.equal(events.posts.length, 0); return true; },
    settle: (...args) => { events.posts.push(args); return new Promise((resolve) => { release = resolve; }); },
  });
  const first = controller.receive(row);
  await Promise.resolve();
  await controller.receive(row);
  assert.equal(events.posts.length, 1);
  assert.deepEqual(events.posts[0], [42, true, "Course payment received at reception"]);
  release(payment("SUCCESS")); await first;
  assert.equal(events.loads.length, 1);
  assert.equal(events.outcomes.at(-1).kind, "success");
  assert.match(events.outcomes.at(-1).text, /không suy ra đăng ký CONFIRMED/);
  controller.dispose();
});

test("REFUND_REQUIRED remains an exception, refreshes queue, and never shows receipt success", async () => {
  const { controller, events } = setup({ settle: async () => payment("REFUND_REQUIRED") });
  await controller.receive(row);
  assert.equal(events.outcomes.at(-1).kind, "exception");
  assert.match(events.outcomes.at(-1).text, /hoàn tiền/);
  assert.equal(events.loads.length, 1);
  controller.dispose();
});

test("a deadline expiring in the dialog prevents a POST and refreshes eligibility", async () => {
  const { controller, events } = setup();
  await controller.receive({ ...row, paymentDeadline: "2020-01-01T10:00:00" });
  assert.equal(events.posts.length, 0);
  assert.equal(events.loads.length, 1);
  assert.equal(events.outcomes.at(-1).kind, "warning");
  controller.dispose();
});

test("another staff member's 409 is reconciled using owned member history", async () => {
  const { controller, events } = setup({ settle: async () => { throw Object.assign(new Error("Already processed"), { status: 409 }); }, fetchHistory: async (id) => { assert.equal(id, 12); return [payment("SUCCESS")]; } });
  await controller.receive(row);
  assert.match(events.outcomes.at(-1).text, /nhân viên khác/);
  assert.equal(events.loads.length, 1);
  controller.dispose();
});

test("missing payment remains unresolved and does not retry settlement", async () => {
  const { controller, events } = setup({ settle: async () => { events.posts.push(42); throw Object.assign(new Error("Not found"), { status: 404 }); } });
  await controller.receive(row);
  await controller.receive(row);
  assert.equal(events.posts.length, 1);
  assert.match(events.outcomes.at(-1).text, /Không tìm thấy/);
  controller.dispose();
});

test("timeout refreshes and checks history but never repeats an ambiguous POST", async () => {
  let timeout;
  const { controller, events } = setup({ settle: () => { events.posts.push(42); return new Promise(() => {}); },
    fetchHistory: async () => [payment("PENDING")], setTimer: (callback) => { timeout = callback; }, clearTimer: () => {} });
  const first = controller.receive(row);
  await Promise.resolve(); timeout(); await first;
  await controller.receive(row);
  assert.equal(events.posts.length, 1);
  assert.equal(events.loads.length, 1);
  assert.equal(events.outcomes.at(-1).kind, "warning");
  assert.match(events.outcomes.at(-1).text, /Không gửi lại/);
  assert.equal(events.stored.at(-1)[0].paymentId, 42);
  controller.dispose();
});

test("persisted uncertain receipt is blocked after reload until terminal reconciliation", async () => {
  const { controller, events } = setup({ initialUncertain: [row], fetchHistory: async () => [payment("REFUND_REQUIRED")] });
  await controller.receive(row);
  assert.equal(events.posts.length, 0);
  await controller.check(row);
  assert.equal(events.outcomes.at(-1).kind, "exception");
  assert.deepEqual(events.stored.at(-1), []);
  controller.dispose();
});

test("empty final page moves back using server pagination and load errors are retryable", async () => {
  const { controller, events } = setup({ fetchQueue: async (index) => { events.loads.push(index); return index === 2 ? { ...page, content: [], totalPages: 2 } : page; } });
  await controller.load(2);
  assert.deepEqual(events.loads, [2, 1]);
  assert.equal(events.queues.at(-1).page, 1);
  controller.dispose();
  let failed = true;
  const second = setup({ fetchQueue: async () => { if (failed) throw Object.assign(new Error("Forbidden"), { status: 403 }); return page; } });
  await second.controller.load();
  assert.match(second.events.queues.at(-1).error, /quyền/);
  failed = false; await second.controller.refresh();
  assert.equal(second.events.queues.at(-1).data.content.length, 1);
  second.controller.dispose();
});

test("late queue responses after unmount cannot update state", async () => {
  let resolve;
  const { controller, events } = setup({ fetchQueue: () => new Promise((done) => { resolve = done; }) });
  const read = controller.load(); controller.dispose(); resolve(page); await read;
  assert.equal(events.queues.length, 1);
});
