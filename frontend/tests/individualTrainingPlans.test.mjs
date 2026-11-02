import test from "node:test";
import assert from "node:assert/strict";
import {
  addWeekWithUnusedNumber,
  createLatestRequestGuard,
  savePlanAndRefreshOpenHistory,
  toIndividualTrainingPlanPayload,
} from "../src/components/individualTrainingPlanHelpers.js";

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
