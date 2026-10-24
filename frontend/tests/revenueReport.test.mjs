import test from "node:test";
import assert from "node:assert/strict";
import { defaultRevenueRange, formatRevenuePeriod, formatVnd, hcmDate, REVENUE_PURPOSES } from "../src/revenueReport.js";

test("report defaults and date grouping use the Ho Chi Minh calendar", () => {
  const now = new Date("2026-10-20T17:30:00.000Z");
  assert.equal(hcmDate(now), "2026-10-21");
  assert.deepEqual(defaultRevenueRange(now), { from: "2026-03-01", to: "2026-10-21" });
  assert.equal(formatRevenuePeriod("2026-10-01", "MONTH"), "10/2026");
  assert.equal(formatRevenuePeriod("2026-10-21", "DAY"), "21/10");
  assert.equal(formatRevenuePeriod("2026-01-01", "YEAR"), "2026");
});

test("VND display and reporting purposes use the supported payment categories", () => {
  assert.match(formatVnd(1250000), /1\.250\.000/);
  assert.deepEqual(REVENUE_PURPOSES.map(([purpose]) => purpose), [
    "MEMBERSHIP", "COURSE_REGISTRATION", "PT_BOOKING",
  ]);
});
