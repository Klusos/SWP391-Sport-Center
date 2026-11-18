import assert from "node:assert/strict";
import test from "node:test";
import { applyMemberDetailRequestIfCurrent } from "../src/memberDetailRequests.js";

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

test("a late member response cannot replace the currently selected member", async () => {
  let latestRequest = 1;
  const state = { selected: null, loading: true };
  const older = deferred();
  const first = applyMemberDetailRequestIfCurrent(older.promise, {
    isCurrent: () => latestRequest === 1,
    onSuccess: (member) => { state.selected = member; },
    onSettled: () => { state.loading = false; },
  });

  latestRequest = 2;
  const current = deferred();
  const second = applyMemberDetailRequestIfCurrent(current.promise, {
    isCurrent: () => latestRequest === 2,
    onSuccess: (member) => { state.selected = member; },
    onSettled: () => { state.loading = false; },
  });
  current.resolve({ id: "B" });
  await second;
  older.resolve({ id: "A" });
  await first;

  assert.deepEqual(state, { selected: { id: "B" }, loading: false });
});

test("a late member error cannot replace the current request state", async () => {
  let latestRequest = 1;
  const state = { error: "", loading: true };
  const older = deferred();
  const first = applyMemberDetailRequestIfCurrent(older.promise, {
    isCurrent: () => latestRequest === 1,
    onError: (error) => { state.error = error.message; },
    onSettled: () => { state.loading = false; },
  });

  latestRequest = 2;
  state.loading = true;
  const current = deferred();
  const second = applyMemberDetailRequestIfCurrent(current.promise, {
    isCurrent: () => latestRequest === 2,
    onSuccess: () => { state.error = ""; },
    onSettled: () => { state.loading = false; },
  });
  current.resolve({ id: "B" });
  await second;
  older.reject(new Error("Old member failed"));
  await first;

  assert.deepEqual(state, { error: "", loading: false });
});
