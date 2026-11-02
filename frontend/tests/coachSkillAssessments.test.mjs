import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { coachAssessmentStatusSummary } from "../src/coachAssessmentView.js";

test("assessment summary counts only backend pending and processed statuses", () => {
  assert.deepEqual(coachAssessmentStatusSummary([
    { status: "PENDING" },
    { status: "COMPLETED" },
    { status: "CANCELLED" },
    { status: "UNKNOWN" },
  ]), { pending: 1, processed: 2 });
  assert.deepEqual(coachAssessmentStatusSummary(null), { pending: 0, processed: 0 });
});

test("assessment UI retains assigned APIs, guarded completion, and error handling", async () => {
  const source = await readFile(new URL("../src/pages/CoachSkillAssessments.jsx", import.meta.url), "utf8");
  assert.match(source, /getMySkillAssessmentAssignmentsApi\(token\)/);
  assert.match(source, /getAssessmentMemberLevelApi\(token, selectedId\)/);
  assert.match(source, /completeSkillAssessmentApi\(token, selected\.id/);
  assert.match(source, /selected\.status !== "PENDING" \|\| submitting/);
  assert.match(source, /requestError\.status === 403/);
  assert.match(source, /requestError\.status === 409/);
});

test("assessment page uses compact pending/processed counts and the two-panel list/detail hierarchy", async () => {
  const [source, css] = await Promise.all([
    readFile(new URL("../src/pages/CoachSkillAssessments.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/CoachSkillAssessments.css", import.meta.url), "utf8"),
  ]);
  assert.match(source, /coach-assessment-counts/);
  assert.doesNotMatch(source, /coach-assessment-summary/);
  assert.match(source, /coach-assessment-browser/);
  assert.match(source, /coach-assessment-detail/);
  assert.match(css, /grid-template-columns: minmax\(0, 5fr\) minmax\(0, 7fr\)/);
  assert.match(css, /@media \(max-width: 1000px\)/);
});
