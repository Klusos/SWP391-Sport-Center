import test from "node:test";
import assert from "node:assert/strict";
import { isMembershipEffective, todayInVietnam } from "../src/membershipDates.js";

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
