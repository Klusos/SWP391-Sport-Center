import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const source = await readFile(new URL("../src/pages/MembershipPlans.jsx", import.meta.url), "utf8");

test("Membership Plans switches between card and list presentations of the same loaded plans", () => {
  assert.match(source, /const \[viewMode, setViewMode\] = useState\("cards"\)/);
  assert.match(source, /aria-pressed=\{viewMode === "cards"\}[\s\S]*?setViewMode\("cards"\)/);
  assert.match(source, /aria-pressed=\{viewMode === "list"\}[\s\S]*?setViewMode\("list"\)/);
  assert.match(source, /viewMode === "cards" \? <div className="membership-plan-grid">[\s\S]*?plans\.map\(\(plan\)/);
  assert.match(source, /: <div className="queue-table-wrap">[\s\S]*?plans\.map\(\(plan\)/);
  assert.doesNotMatch(source, /displayedPlans|viewMode === "empty"/);
});

test("both presentations expose real plan details and status without derived promotional values", () => {
  for (const field of [
    "plan.name",
    "plan.description",
    "plan.price",
    "plan.durationMonths",
    "plan.courseDiscountPercent",
    "plan.personalTrainingDiscountPercent",
    "plan.allowPersonalTrainingPlan",
    "plan.allowOneOnOneCoaching",
    "plan.status",
  ]) {
    assert.ok(source.includes(field), `expected real backend field ${field}`);
  }
  assert.doesNotMatch(source, /getPlanCode|0\.8\)|STANDARD|PREMIUM|PLATINUM/);
});

test("management actions remain Admin-only and share the existing handlers in both views", () => {
  assert.equal((source.match(/<PlanActions plan=/g) || []).length, 2);
  assert.match(source, /\{admin && <PlanActions plan=/);
  assert.match(source, /\{admin && <td><PlanActions plan=/);
  assert.match(source, /function startEdit\(plan\)/);
  assert.match(source, /async function changeStatus\(plan\)/);
  assert.match(source, /updateMembershipPlanStatusApi\(token, plan\.id, nextStatus\)/);
  assert.match(source, /createMembershipPlanApi\(token, payload\)/);
  assert.match(source, /updateMembershipPlanApi\(token, editingId/);
});

test("loading and empty states are shared regardless of selected presentation", () => {
  assert.match(source, /loading \? <p role="status">/);
  assert.match(source, /plans\.length === 0 \? <p className="membership-empty-state">/);
  assert.match(source, /viewMode === "cards" \? <div className="membership-plan-grid">/);
  assert.match(source, /disabled=\{loading\}/);
});
