import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { applyAttendanceRequestIfCurrent } from "../src/attendanceRequests.js";

const staffPageUrl = new URL("../src/pages/StaffAttendance.jsx", import.meta.url);
const coachPageUrl = new URL("../src/pages/CoachAttendance.jsx", import.meta.url);

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

test("switching sessions keeps the latest roster when requests resolve out of order", async () => {
  let selectedSession = "A";
  let latestRequest = 1;
  let roster = null;
  const requestA = deferred();
  const loadA = applyAttendanceRequestIfCurrent(requestA.promise, {
    isCurrent: () => latestRequest === 1 && selectedSession === "A",
    onSuccess: (value) => { roster = value; },
  });

  selectedSession = "B";
  latestRequest = 2;
  const requestB = deferred();
  const loadB = applyAttendanceRequestIfCurrent(requestB.promise, {
    isCurrent: () => latestRequest === 2 && selectedSession === "B",
    onSuccess: (value) => { roster = value; },
  });

  requestB.resolve({ session: "B" });
  await loadB;
  requestA.resolve({ session: "A" });
  await loadA;
  assert.deepEqual(roster, { session: "B" });
});

test("a delayed roster failure cannot replace the selected session error or loading state", async () => {
  let selectedSession = "A";
  let latestRequest = 1;
  const state = { error: "", loading: true };
  const requestA = deferred();
  const loadA = applyAttendanceRequestIfCurrent(requestA.promise, {
    isCurrent: () => latestRequest === 1 && selectedSession === "A",
    onError: (error) => { state.error = error.message; },
    onSettled: () => { state.loading = false; },
  });

  selectedSession = "B";
  latestRequest = 2;
  state.error = "";
  state.loading = true;
  const requestB = deferred();
  const loadB = applyAttendanceRequestIfCurrent(requestB.promise, {
    isCurrent: () => latestRequest === 2 && selectedSession === "B",
    onSuccess: () => { state.error = ""; },
    onSettled: () => { state.loading = false; },
  });

  requestB.resolve({ session: "B" });
  await loadB;
  requestA.reject(new Error("Session A failed"));
  await loadA;
  assert.deepEqual(state, { error: "", loading: false });
});

test("out-of-order history responses leave the latest member history visible", async () => {
  let selectedSession = "S1";
  let selectedMember = "A";
  let latestRequest = 1;
  let history = [];
  const requestA = deferred();
  const loadA = applyAttendanceRequestIfCurrent(requestA.promise, {
    isCurrent: () => latestRequest === 1 && selectedSession === "S1" && selectedMember === "A",
    onSuccess: (value) => { history = value; },
  });

  selectedMember = "B";
  latestRequest = 2;
  const requestB = deferred();
  const loadB = applyAttendanceRequestIfCurrent(requestB.promise, {
    isCurrent: () => latestRequest === 2 && selectedSession === "S1" && selectedMember === "B",
    onSuccess: (value) => { history = value; },
  });

  requestB.resolve([{ member: "B" }]);
  await loadB;
  requestA.resolve([{ member: "A" }]);
  await loadA;
  assert.deepEqual(history, [{ member: "B" }]);
});

test("closing history invalidates a pending success or failure and its loading update", async () => {
  let latestRequest = 1;
  let open = true;
  const state = { history: [], error: "", loading: true };
  const request = deferred();
  const loading = applyAttendanceRequestIfCurrent(request.promise, {
    isCurrent: () => latestRequest === 1 && open,
    onSuccess: (value) => { state.history = value; },
    onError: (error) => { state.error = error.message; },
    onSettled: () => { state.loading = false; },
  });

  latestRequest += 1;
  open = false;
  state.loading = false;
  request.reject(new Error("late history failure"));
  await loading;
  assert.deepEqual(state, { history: [], error: "", loading: false });
});

test("both Attendance pages route roster/history state updates through the shared async guard", async () => {
  const [staff, coach] = await Promise.all([
    readFile(staffPageUrl, "utf8"),
    readFile(coachPageUrl, "utf8"),
  ]);
  assert.match(staff, /catch \(error\) \{ if \(requestId === rosterRequestRef\.current && String\(selectedIdRef\.current\) === String\(sessionId\)\) setRosterError/s);
  assert.match(staff, /await applyAttendanceRequestIfCurrent\(getAttendanceHistoryApi/);
  assert.match(staff, /historyRequestRef\.current \+= 1/);
  assert.match(coach, /await applyAttendanceRequestIfCurrent\(getAttendanceHistoryApi/);
  assert.match(coach, /historyRequestRef\.current \+= 1/);
});
