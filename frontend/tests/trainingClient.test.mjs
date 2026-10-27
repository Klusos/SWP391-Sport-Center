import test from "node:test";
import assert from "node:assert/strict";
import {
  addTrainingExerciseApi,
  completeTrainingSessionApi,
  deleteTrainingExerciseApi,
  evaluateProgressApi,
  getMyTrainingApi,
  getTrainingClassesApi,
  getTrainingPlanApi,
  getTrainingProgressApi,
  getTrainingResultSheetApi,
  getTrainingSessionsApi,
  recordTrainingResultApi,
  saveTrainingPlanApi,
  updateTrainingExerciseApi,
} from "../src/api.js";

const BASE = "http://localhost:8080";

async function captureFetch(respond, run) {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, options = {}) => {
    calls.push({ url, options });
    return respond();
  };
  try {
    await run(calls);
  } finally {
    globalThis.fetch = original;
  }
}

const ok = () => new Response("{}", { status: 200 });

test("training helpers call the backend paths with the expected methods and bearer token", async () => {
  await captureFetch(ok, async (calls) => {
    await getTrainingClassesApi("t");
    await getTrainingPlanApi("t", 4);
    await saveTrainingPlanApi("t", 4, { goal: "Serve" });
    await addTrainingExerciseApi("t", 4, { name: "Footwork" });
    await updateTrainingExerciseApi("t", 7, { name: "Footwork" });
    await deleteTrainingExerciseApi("t", 7);
    await getTrainingSessionsApi("t", 4);
    await completeTrainingSessionApi("t", 11);
    await getTrainingResultSheetApi("t", 11);
    await recordTrainingResultApi("t", 11, 20, { score: 8, notes: null });
    await getTrainingProgressApi("t", 4);
    await evaluateProgressApi("t", 4, 20, { rating: 5, comment: "Great" });
    await getMyTrainingApi("t");

    assert.deepEqual(calls.map(({ url, options }) => [options.method || "GET", url.slice(BASE.length)]), [
      ["GET", "/api/training/classes"],
      ["GET", "/api/training/classes/4/plan"],
      ["PUT", "/api/training/classes/4/plan"],
      ["POST", "/api/training/classes/4/plan/exercises"],
      ["PUT", "/api/training/exercises/7"],
      ["DELETE", "/api/training/exercises/7"],
      ["GET", "/api/training/classes/4/sessions"],
      ["POST", "/api/training/sessions/11/complete"],
      ["GET", "/api/training/sessions/11/results"],
      ["PUT", "/api/training/sessions/11/results/20"],
      ["GET", "/api/training/classes/4/progress"],
      ["POST", "/api/training/classes/4/members/20/evaluations"],
      ["GET", "/api/training/me"],
    ]);
    for (const { options } of calls) assert.equal(options.headers.Authorization, "Bearer t");
  });
});

test("training writes send JSON bodies", async () => {
  await captureFetch(ok, async (calls) => {
    await recordTrainingResultApi("t", 11, 20, { score: 9, notes: "Steady" });
    const [{ options }] = calls;
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.deepEqual(JSON.parse(options.body), { score: 9, notes: "Steady" });
  });
});

test("training helpers reject with the backend status and message", async () => {
  const forbidden = () => new Response(JSON.stringify({ message: "Only the assigned Coach or ADMIN can manage training for this class" }), { status: 403 });
  await captureFetch(forbidden, async () => {
    await assert.rejects(saveTrainingPlanApi("t", 4, { goal: "x" }), (error) => {
      assert.equal(error.status, 403);
      assert.match(error.message, /assigned Coach/);
      return true;
    });
  });
});
