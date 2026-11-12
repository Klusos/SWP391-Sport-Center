import test from "node:test";
import assert from "node:assert/strict";
import { defaultReportRevenueRange, defaultRevenueRange, formatRevenuePeriod, formatVnd, hcmDate, isValidRevenueRange, REVENUE_DATE_PRESETS, revenueDatePresetRange, REVENUE_PURPOSES } from "../src/revenueReport.js";

test("report defaults and date grouping use the Ho Chi Minh calendar", () => {
  const now = new Date("2026-10-20T17:30:00.000Z");
  assert.equal(hcmDate(now), "2026-10-21");
  assert.deepEqual(defaultRevenueRange(now), { from: "2026-03-01", to: "2026-10-21" });
  assert.deepEqual(defaultReportRevenueRange(now), { from: "2026-10-01", to: "2026-10-21" });
  assert.equal(hcmDate(new Date("2026-10-31T17:00:00.000Z")), "2026-11-01");
  assert.deepEqual(defaultReportRevenueRange(new Date("2026-10-31T17:00:00.000Z")), { from: "2026-11-01", to: "2026-11-01" });
  assert.equal(formatRevenuePeriod("2026-10-01", "MONTH"), "10/2026");
  assert.equal(formatRevenuePeriod("2026-10-21", "DAY"), "21/10");
  assert.equal(formatRevenuePeriod("2026-01-01", "YEAR"), "2026");
});

test("Vietnam-date presets include today and handle month, leap-year, and year boundaries", () => {
  assert.deepEqual(REVENUE_DATE_PRESETS.map(([key]) => key), ["TODAY", "LAST_7_DAYS", "THIS_MONTH", "LAST_30_DAYS"]);
  const leapDay = new Date("2024-02-29T05:00:00.000Z");
  assert.deepEqual(revenueDatePresetRange("TODAY", leapDay), { from: "2024-02-29", to: "2024-02-29" });
  assert.deepEqual(revenueDatePresetRange("LAST_7_DAYS", new Date("2024-03-01T05:00:00.000Z")), { from: "2024-02-24", to: "2024-03-01" });
  assert.deepEqual(revenueDatePresetRange("LAST_30_DAYS", new Date("2024-03-01T05:00:00.000Z")), { from: "2024-02-01", to: "2024-03-01" });
  assert.deepEqual(revenueDatePresetRange("THIS_MONTH", new Date("2024-02-29T05:00:00.000Z")), { from: "2024-02-01", to: "2024-02-29" });
  assert.deepEqual(revenueDatePresetRange("LAST_7_DAYS", new Date("2026-01-01T05:00:00.000Z")), { from: "2025-12-26", to: "2026-01-01" });
  assert.deepEqual(revenueDatePresetRange("LAST_30_DAYS", new Date("2026-01-01T05:00:00.000Z")), { from: "2025-12-03", to: "2026-01-01" });
});

test("manual date ranges remain valid when ordered and reject missing or reversed dates", () => {
  assert.equal(isValidRevenueRange({ from: "2026-02-28", to: "2026-02-28" }), true);
  assert.equal(isValidRevenueRange({ from: "2026-03-01", to: "2026-02-28" }), false);
  assert.equal(isValidRevenueRange({ from: "", to: "2026-03-01" }), false);
  assert.equal(isValidRevenueRange({ from: "2026-03-01", to: "" }), false);
});

test("VND display and reporting purposes use the supported payment categories", () => {
  assert.match(formatVnd(1250000), /1\.250\.000/);
  assert.deepEqual(REVENUE_PURPOSES.map(([purpose]) => purpose), [
    "MEMBERSHIP", "COURSE_REGISTRATION", "PT_BOOKING", "UPGRADE",
  ]);
});

test("Dashboard keeps the shared eight-month reporting range", async () => {
  const source = await (await import("node:fs/promises")).readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  assert.match(source, /defaultRevenueRange\(\)/);
  assert.match(source, /Tổng 8 tháng gần nhất/);
});
