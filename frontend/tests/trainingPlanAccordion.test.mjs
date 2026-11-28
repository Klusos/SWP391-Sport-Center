import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { defaultOpenTrainingWeek, formatTrainingPlanDate, trainingWeekKey } from "../src/components/trainingPlanAccordion.js";

test("read-only weekly plans open the current dated week, or the first available week", () => {
  const weeks = [
    { id: 4, weekNumber: 1, startDate: "2026-11-01", endDate: "2026-11-07" },
    { id: 5, weekNumber: 2, startDate: "2026-11-08", endDate: "2026-11-14" },
  ];
  assert.equal(defaultOpenTrainingWeek(weeks, "2026-11-11"), "5");
  assert.equal(defaultOpenTrainingWeek(weeks, "2026-12-01"), "4");
  assert.equal(defaultOpenTrainingWeek([], "2026-11-11"), null);
});

test("week identity and existing date ranges remain stable and date-only", () => {
  assert.equal(trainingWeekKey({ weekNumber: 3 }, 0), "3");
  assert.equal(formatTrainingPlanDate("2026-11-07"), "07/11/2026");
  assert.equal(formatTrainingPlanDate(null), "");
});

test("shared plan renderer exposes a single keyboard-operable accordion control per week", async () => {
  const source = await readFile(new URL("../src/components/TrainingPlanWeeksAccordion.jsx", import.meta.url), "utf8");
  assert.match(source, /type="button"/);
  assert.match(source, /aria-expanded=\{expanded\}/);
  assert.match(source, /aria-controls=\{panelId\}/);
  assert.match(source, /setSelection\(\{ signature: weekSignature, key: expanded \? null : key \}\)/);
  assert.match(source, /Bài tập chưa phân tuần/);
});
