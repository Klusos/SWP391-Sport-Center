import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

test("weekly plan editor uses a focused local draft workspace with copy, preview, delete confirmation, and sticky actions", async () => {
  const source = await readFile(new URL("../src/components/WeeklyPlanEditor.jsx", import.meta.url), "utf8");
  assert.match(source, /weekly-plan-sidebar/);
  assert.match(source, /setActiveKey\(week\._key\)/);
  assert.match(source, /id: undefined/);
  assert.match(source, /window\.confirm\(/);
  assert.match(source, /setPreview\(true\)/);
  assert.match(source, /weekly-plan-sticky-actions/);
  assert.match(source, /className="btn-primary" disabled=\{busy\}/);
  assert.ok(source.indexOf('onClick={() => setPreview(true)}') < source.indexOf('onClick={cancel}', source.indexOf('weekly-plan-sticky-actions-right')));
  assert.ok(source.indexOf('onClick={cancel}', source.indexOf('weekly-plan-sticky-actions-right')) < source.indexOf('type="submit" className="btn-primary"'));
  assert.match(source, /beforeunload/);
  assert.match(source, /maxWeekNumber = 52/);
});

test("general plan save uses its atomic weekly endpoint and retains the legacy exercise collection", async () => {
  const editor = await readFile(new URL("../src/components/GeneralTrainingPlanTab.jsx", import.meta.url), "utf8");
  const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
  assert.match(editor, /saveWeeklyTrainingPlanApi/);
  assert.match(editor, /expectedUpdatedAt: plan\?\.updatedAt \?\? null/);
  assert.match(editor, /unassignedExerciseIds/);
  assert.match(editor, /includeLegacy showWeekGoal/);
  assert.match(api, /\/plan\/weekly/);
});
