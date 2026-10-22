import test from "node:test";
import assert from "node:assert/strict";
import { cancelMembershipAutoRenewApi, enableMembershipAutoRenewApi } from "../src/api.js";
import { autoRenewEnableReason, canCancelMembershipAutoRenew, canEnableMembershipAutoRenew, membershipSections } from "../src/membershipAutoRenew.js";

const today = "2026-10-22";
const current = { id: 5, status: "ACTIVE", startDate: "2026-01-01", endDate: "2026-11-01", autoRenew: false, paymentId: 50 };
const older = { id: 4, status: "ACTIVE", startDate: "2025-01-01", endDate: "2026-01-01", autoRenew: false, paymentId: 40 };
const paidHistory = [{ id: 50, status: "SUCCESS" }, { id: 40, status: "SUCCESS" }];

test("Membership Auto Renew eligibility comes from persisted Membership preference and paid final period", () => {
  assert.equal(canEnableMembershipAutoRenew(current, [current, older], paidHistory, today), true);
  assert.equal(canCancelMembershipAutoRenew({ ...current, autoRenew: true }), true);
  assert.equal(canEnableMembershipAutoRenew({ ...current, autoRenew: true }, [current], paidHistory, today), false);
  assert.equal(canEnableMembershipAutoRenew({ ...current, status: "EXPIRED" }, [current], paidHistory, today), false);
  assert.equal(canEnableMembershipAutoRenew({ ...current, endDate: today }, [current], paidHistory, today), false);
  assert.equal(canEnableMembershipAutoRenew(current, [current], [{ id: 50, status: "PENDING" }], today), false);
  assert.equal(canEnableMembershipAutoRenew(current, [current, { ...older, startDate: "2026-02-01" }], paidHistory, today), false);
});

test("a terminal renewal request blocks re-enabling according to the existing backend policy", () => {
  const terminal = { id: 80, isRenewal: true, renewalSourceMembershipId: current.id, status: "CANCELLED" };
  assert.equal(canEnableMembershipAutoRenew(current, [current], paidHistory, today), true);
  assert.equal(canEnableMembershipAutoRenew(current, [current], [...paidHistory, terminal], today), false);
});

test("Memberships are separated by effective business-date boundaries without hiding overlaps", () => {
  const effective = { ...current, startDate: today, endDate: "2026-10-23" };
  const future = { ...current, id: 6, startDate: "2026-10-23", endDate: "2026-11-23" };
  const expired = { ...older, id: 7, status: "EXPIRED", startDate: "2026-09-01", endDate: today };
  const historical = { ...older, id: 8, status: "REPLACED", startDate: "2026-01-01", endDate: "2026-02-01" };
  const sections = membershipSections([effective, future, expired, historical], today);
  assert.deepEqual(sections.current.map((item) => item.id), [effective.id]);
  assert.deepEqual(sections.future.map((item) => item.id), [future.id]);
  assert.deepEqual(sections.history.map((item) => item.id), [expired.id, historical.id]);
  assert.equal(sections.hasOverlap, false);
  assert.equal(membershipSections([{ ...effective, id: 9 }, effective], today).hasOverlap, true);
});

test("Auto Renew explanatory state follows safe frontend eligibility checks", () => {
  assert.match(autoRenewEnableReason(current, [current, older], paidHistory, today), /đủ điều kiện/i);
  assert.match(autoRenewEnableReason({ ...current, startDate: "2026-10-23" }, [current], paidHistory, today), /đã bắt đầu/i);
  assert.match(autoRenewEnableReason({ ...current, endDate: today }, [current], paidHistory, today), /hết hạn/i);
  assert.match(autoRenewEnableReason(current, [current], [{ id: 50, status: "PENDING" }], today), /chưa có thanh toán thành công/i);
  const terminal = { id: 80, isRenewal: true, renewalSourceMembershipId: current.id, status: "CANCELLED" };
  assert.match(autoRenewEnableReason(current, [current], [...paidHistory, terminal], today), /trạng thái kết thúc/i);
});

test("Membership Auto Renew API uses existing authenticated endpoints without changing payment APIs", async () => {
  const originalFetch = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, options });
    return new Response("{}", { status: 200 });
  };
  try {
    await enableMembershipAutoRenewApi("member-token", 5);
    await cancelMembershipAutoRenewApi("member-token", 5);
    assert.match(calls[0].url, /\/api\/memberships\/5\/enable-auto-renew$/);
    assert.equal(calls[0].options.method, "PATCH");
    assert.equal(calls[0].options.headers.Authorization, "Bearer member-token");
    assert.match(calls[1].url, /\/api\/memberships\/5\/cancel-renewal$/);
    assert.equal(calls[1].options.method, "PATCH");
  } finally { globalThis.fetch = originalFetch; }
});
