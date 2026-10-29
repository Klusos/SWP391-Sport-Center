import test from "node:test";
import assert from "node:assert/strict";
import { hasOverlappingEffectiveMemberships, isMembershipEffective, membershipSections, todayInVietnam } from "../src/membershipDates.js";

const active = { status: "ACTIVE", startDate: "2026-10-25", endDate: "2026-10-26" };
test("Membership effective period includes start and excludes end date", () => {
  assert.equal(isMembershipEffective(active, "2026-10-25"), true);
  assert.equal(isMembershipEffective(active, "2026-10-26"), false);
  assert.equal(isMembershipEffective(active, "2026-10-24"), false);
});
test("status alone does not make a Membership effective", () => {
  assert.equal(isMembershipEffective({ status: "ACTIVE" }, "2026-10-25"), false);
  assert.equal(isMembershipEffective({ ...active, status: "EXPIRED" }, "2026-10-25"), false);
});
test("today uses Vietnam-local calendar date", () => {
  assert.equal(todayInVietnam(new Date("2026-10-24T18:00:00.000Z")), "2026-10-25");
});

test("membership sections separate current, queued, expired, cancelled and superseded history without duplication", () => {
  const rows = [
    { id: 1, status: "ACTIVE", startDate: "2026-10-01", endDate: "2026-11-01" },
    { id: 2, status: "ACTIVE", startDate: "2026-11-01", endDate: "2026-12-01" },
    { id: 3, status: "ACTIVE", startDate: "2026-09-01", endDate: "2026-10-25" },
    { id: 4, status: "CANCELLED", startDate: "2026-11-01", endDate: "2026-12-01" },
    { id: 5, status: "UPGRADED", startDate: "2026-08-01", endDate: "2026-09-01" },
  ];
  const sections = membershipSections(rows, "2026-10-25");

  assert.deepEqual(sections.current.map((row) => row.id), [1]);
  assert.deepEqual(sections.upcoming.map((row) => row.id), [2]);
  assert.deepEqual(sections.history.map((row) => row.id), [4, 3, 5]);
  assert.equal(new Set(Object.values(sections).flat().map((row) => row.id)).size, rows.length);
});

test("overlapping effective Memberships are reported without choosing or removing one", () => {
  const memberships = [
    { id: 11, status: "ACTIVE", startDate: "2026-10-01", endDate: "2026-11-01" },
    { id: 12, status: "ACTIVE", startDate: "2026-10-20", endDate: "2026-11-20" },
  ];
  const sections = membershipSections(memberships, "2026-10-25");

  assert.equal(hasOverlappingEffectiveMemberships(sections), true);
  assert.deepEqual(sections.current.map((row) => row.id), [11, 12]);
  assert.equal(memberships.length, 2);
});

test("membership sections have independent empty states", () => {
  const sections = membershipSections([], "2026-10-25");
  assert.deepEqual(sections, { current: [], upcoming: [], history: [] });
  assert.equal(hasOverlappingEffectiveMemberships(sections), false);
});
