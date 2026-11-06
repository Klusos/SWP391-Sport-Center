import test from "node:test";
import assert from "node:assert/strict";
import {
  addWeekWithUnusedNumber,
  canCreateIndividualPlan,
  createLatestRequestGuard,
  individualPlanEligibilityMessage,
  savePlanAndRefreshOpenHistory,
  toIndividualTrainingPlanPayload,
} from "../src/components/individualTrainingPlanHelpers.js";
import { readFile } from "node:fs/promises";

const week = (weekNumber, exercise = `Week ${weekNumber}`) => ({
  weekNumber, title: `Week ${weekNumber}`, description: "", exercises: [{
    name: exercise, sets: "3", reps: "10", durationMinutes: "", notes: "",
  }],
});

test("adding after removing a middle week assigns a unique number and builds a sorted API payload", () => {
  const threeWeeks = [week(1), week(2), week(3)];
  const afterRemoval = threeWeeks.filter((item) => item.weekNumber !== 2);
  const afterAdd = addWeekWithUnusedNumber(afterRemoval);

  assert.deepEqual(afterAdd.map((item) => item.weekNumber), [1, 2, 3]);
  assert.equal(new Set(afterAdd.map((item) => item.weekNumber)).size, afterAdd.length);
  assert.deepEqual(toIndividualTrainingPlanPayload({ goal: "Strength", description: "", weeks: afterAdd }), {
    goal: "Strength",
    description: null,
    weeks: [1, 2, 3].map((number) => ({
      weekNumber: number,
      title: number === 2 ? null : `Week ${number}`,
      description: null,
      exercises: number === 2 ? [] : [{ name: `Week ${number}`, sets: 3, reps: 10, durationMinutes: null, notes: null, sortOrder: 0 }],
    })),
  });
});

test("week allocation fills the lowest gap and does not exceed the backend 52-week limit", () => {
  assert.deepEqual(addWeekWithUnusedNumber([week(1), week(3)]).map((item) => item.weekNumber), [1, 2, 3]);
  const allWeeks = Array.from({ length: 52 }, (_, index) => week(index + 1));
  assert.equal(addWeekWithUnusedNumber(allWeeks), allWeeks);
});

test("saving an edit refreshes open history and keeps older revisions visible", async () => {
  let history = [{ revisionNumber: 1, goal: "Original" }];
  const calls = [];
  const result = await savePlanAndRefreshOpenHistory(async () => {
    calls.push("save");
    return { id: 9 };
  }, {
    planId: 9,
    historyIsOpen: true,
    refreshHistory: async (planId) => {
      calls.push(`history:${planId}`);
      history = [{ revisionNumber: 2, goal: "Updated" }, ...history];
    },
  });

  assert.deepEqual(result, { id: 9 });
  assert.deepEqual(calls, ["save", "history:9"]);
  assert.deepEqual(history.map((revision) => revision.revisionNumber), [2, 1]);
  assert.deepEqual(history.map((revision) => revision.goal), ["Updated", "Original"]);
});

test("failed saves leave open revision history unchanged and do not request a refresh", async () => {
  const history = [{ revisionNumber: 1, goal: "Original" }];
  let refreshCalls = 0;

  await assert.rejects(savePlanAndRefreshOpenHistory(async () => {
    throw new Error("save failed");
  }, {
    planId: 9,
    historyIsOpen: true,
    refreshHistory: async () => { refreshCalls += 1; },
  }), /save failed/);

  assert.equal(refreshCalls, 0);
  assert.deepEqual(history, [{ revisionNumber: 1, goal: "Original" }]);
});

test("only the latest revision-history request may update the view", () => {
  const requests = createLatestRequestGuard();
  const oldRequest = requests.start();
  const currentRequest = requests.start();
  assert.equal(requests.isCurrent(oldRequest), false);
  assert.equal(requests.isCurrent(currentRequest), true);
  requests.invalidate();
  assert.equal(requests.isCurrent(currentRequest), false);
});

test("individual plan creation stays disabled until the backend attendance eligibility is loaded", () => {
  const eligible = { memberId: 12, hasQualifyingCompletedAttendance: true };
  const ineligible = { memberId: 13, hasQualifyingCompletedAttendance: false };
  assert.equal(canCreateIndividualPlan(eligible, false), false);
  assert.equal(canCreateIndividualPlan(eligible, true), true);
  assert.equal(canCreateIndividualPlan(ineligible, true), false);
  assert.equal(canCreateIndividualPlan({ memberId: 14 }, true), false);
});

test("eligibility states explain missing attendance and failed progress requests", () => {
  assert.match(individualPlanEligibilityMessage({ hasQualifyingCompletedAttendance: false }, true), /sau khi học viên tham gia ít nhất một buổi học đã hoàn thành/);
  assert.match(individualPlanEligibilityMessage({ hasQualifyingCompletedAttendance: true }, true), /^$/);
  assert.match(individualPlanEligibilityMessage({ hasQualifyingCompletedAttendance: true }, false), /Không thể xác minh điều kiện điểm danh/);
  assert.match(individualPlanEligibilityMessage(null, true), /sau khi học viên tham gia/);
});

test("Coach/Admin UI keeps existing plans available and refreshes eligibility after a 409", async () => {
  const component = await readFile(new URL("../src/components/IndividualTrainingPlans.jsx", import.meta.url), "utf8");
  assert.match(component, /plan \? <button type="button" className="secondary" onClick=\{\(\) => void openPlan\(plan\)\}>/);
  assert.match(component, /disabled=\{!canCreate\}/);
  assert.match(component, /requestError\?\.status === 409\) await load\(\)/);
  assert.match(component, /Promise\.allSettled/);
});
