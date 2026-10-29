import test from "node:test";
import assert from "node:assert/strict";
import { addTrainingExerciseApi, completeTrainingSessionApi, createIndividualTrainingPlanApi, deleteTrainingExerciseApi, evaluateProgressApi, getClassIndividualTrainingPlansApi, getIndividualTrainingPlanApi, getIndividualTrainingPlanRevisionsApi, getMyIndividualTrainingPlansApi, getMyTrainingApi, getTrainingClassesApi, getTrainingPlanApi, getTrainingProgressApi, getTrainingResultSheetApi, getTrainingSessionsApi, recordTrainingResultApi, saveTrainingPlanApi, updateIndividualTrainingPlanApi, updateTrainingExerciseApi } from "../src/api.js";

async function withFetch(run) {
  const old = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => { calls.push({ url, options }); return new Response("{}", { status: 200 }); };
  try { await run(calls); } finally { globalThis.fetch = old; }
}
test("Training API helpers match the backend route and method contract", async () => {
  await withFetch(async (calls) => {
    await getTrainingClassesApi("token");
    await getTrainingPlanApi("token", 8);
    await saveTrainingPlanApi("token", 8, { goal: "Balance" });
    await addTrainingExerciseApi("token", 8, { name: "Footwork" });
    await updateTrainingExerciseApi("token", 3, { name: "Footwork" });
    await deleteTrainingExerciseApi("token", 3);
    await getTrainingSessionsApi("token", 8);
    await completeTrainingSessionApi("token", 9);
    await getTrainingResultSheetApi("token", 9);
    await recordTrainingResultApi("token", 9, 12, { score: 8, notes: null });
    await getTrainingProgressApi("token", 8);
    await evaluateProgressApi("token", 8, 12, { rating: 4, comment: "Good" });
    await getMyTrainingApi("token");
    const individualPayload = { goal: "Mobility", description: null, weeks: [{ weekNumber: 1, exercises: [] }] };
    await createIndividualTrainingPlanApi("token", 8, 12, individualPayload);
    await getClassIndividualTrainingPlansApi("token", 8);
    await updateIndividualTrainingPlanApi("token", 31, individualPayload);
    await getMyIndividualTrainingPlansApi("token");
    await getIndividualTrainingPlanApi("token", 31);
    await getIndividualTrainingPlanRevisionsApi("token", 31);
    assert.deepEqual(calls.map(({ url, options }) => [url, options.method || "GET"]), [
      ["http://localhost:8080/api/training/classes", "GET"],
      ["http://localhost:8080/api/training/classes/8/plan", "GET"],
      ["http://localhost:8080/api/training/classes/8/plan", "PUT"],
      ["http://localhost:8080/api/training/classes/8/plan/exercises", "POST"],
      ["http://localhost:8080/api/training/exercises/3", "PUT"],
      ["http://localhost:8080/api/training/exercises/3", "DELETE"],
      ["http://localhost:8080/api/training/classes/8/sessions", "GET"],
      ["http://localhost:8080/api/training/sessions/9/complete", "POST"],
      ["http://localhost:8080/api/training/sessions/9/results", "GET"],
      ["http://localhost:8080/api/training/sessions/9/results/12", "PUT"],
      ["http://localhost:8080/api/training/classes/8/progress", "GET"],
      ["http://localhost:8080/api/training/classes/8/members/12/evaluations", "POST"],
      ["http://localhost:8080/api/training/me", "GET"],
      ["http://localhost:8080/api/training/classes/8/members/12/individual-plan", "POST"],
      ["http://localhost:8080/api/training/classes/8/individual-plans", "GET"],
      ["http://localhost:8080/api/training/individual-plans/31", "PUT"],
      ["http://localhost:8080/api/training/me/individual-plans", "GET"],
      ["http://localhost:8080/api/training/individual-plans/31", "GET"],
      ["http://localhost:8080/api/training/individual-plans/31/revisions", "GET"],
    ]);
    assert.equal(calls[2].options.headers.Authorization, "Bearer token");
    assert.deepEqual(JSON.parse(calls[2].options.body), { goal: "Balance" });
    assert.deepEqual(JSON.parse(calls[9].options.body), { score: 8, notes: null });
    assert.deepEqual(JSON.parse(calls[11].options.body), { rating: 4, comment: "Good" });
    assert.deepEqual(JSON.parse(calls[13].options.body), individualPayload);
    assert.deepEqual(JSON.parse(calls[15].options.body), individualPayload);
    assert.equal(calls[13].options.headers.Authorization, "Bearer token");
  });
});
test("Training API surfaces authorization and validation errors", async () => {
  const old = globalThis.fetch;
  globalThis.fetch = async () => new Response(JSON.stringify({ message: "Forbidden" }), { status: 403 });
  try { await assert.rejects(getMyTrainingApi("token"), (error) => error.status === 403 && error.message === "Forbidden"); }
  finally { globalThis.fetch = old; }
});
