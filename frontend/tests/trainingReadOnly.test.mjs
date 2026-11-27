import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { canManageTrainingForRole } from "../src/trainingCapabilities.js";

const src = new URL("../src/", import.meta.url);
const read = (file) => readFile(new URL(file, src), "utf8");

test("only the authenticated Coach role receives Training write capability", () => {
  assert.equal(canManageTrainingForRole("COACH"), true);
  for (const role of ["ADMIN", "MEMBER", "RECEPTIONIST", "GUEST", null, undefined]) {
    assert.equal(canManageTrainingForRole(role), false, `${role} cannot manage Coach Training`);
  }
});

test("shared Coach Training gates every class-level mutation and presents Admin as read-only", async () => {
  const source = await read("pages/CoachTraining.jsx");
  assert.match(source, /canManageTrainingForRole\(role\)/);
  for (const mutation of ["complete", "saveResult", "evaluate"]) {
    const start = source.indexOf(`async function ${mutation}(`);
    assert.notEqual(start, -1, `${mutation} handler exists`);
    const body = source.slice(start, source.indexOf("\n  }", start));
    assert.match(body, /if \(!canManageTraining\) return;/, `${mutation} is guarded`);
  }
  assert.match(source, /canManageTraining \? "coach" : "admin-readonly"/);
  assert.match(source, /Chế độ chỉ xem:/);
  const planEditor = await read("components/GeneralTrainingPlanTab.jsx");
  assert.match(planEditor, /canManageTraining \? <WeeklyPlanEditor/);
  assert.match(planEditor, /GeneralPlanReadOnly plan=\{plan\}/);
  assert.match(source, /canManageTraining && session\.canComplete/);
  assert.match(source, /canManageTraining && <form className="training-inline"/);
});

test("Admin individual-plan mode reads class plans and history without mounting editor controls", async () => {
  const [source, member] = await Promise.all([read("components/IndividualTrainingPlans.jsx"), read("pages/MemberTraining.jsx")]);
  assert.match(source, /mode === "admin-readonly"/);
  assert.match(source, /else if \(isAdminReadOnly\)[\s\S]*?getClassIndividualTrainingPlansApi/);
  assert.match(source, /if \(!canManageTraining\) return;/);
  assert.match(source, /canManageTraining && <button type="button" onClick=\{\(\) => setEditing\(true\)\}/);
  assert.match(source, /editing && canManageTraining \? <WeeklyPlanEditor/);
  assert.match(source, /Lịch sử phiên bản/);
  assert.match(member, /mode="member"/);
});

test("Admin training is discoverable and remains read-only while Coach navigation stays available", async () => {
  const [app, sidebar] = await Promise.all([read("App.jsx"), read("components/Sidebar.jsx")]);
  assert.match(app, /path="\/admin\/training" element=\{<CoachTraining \{\.\.\.staff\} \/>\}/);
  const adminGroups = sidebar.match(/const adminGroups\s*=\s*\[([\s\S]*?)\n\];/)?.[1];
  assert.ok(adminGroups);
  assert.match(adminGroups, /\["admin-training", "Giám sát huấn luyện"\]/);
  assert.equal((adminGroups.match(/\["admin-training"/g) || []).length, 1);
  assert.match(sidebar, /coach-training/);
});

test("Admin Training gets the shared account-aware Top Bar without changing Coach presentation", async () => {
  const [source, app] = await Promise.all([read("pages/CoachTraining.jsx"), read("App.jsx")]);
  assert.match(source, /import AdminTopBar from "\.\.\/components\/AdminTopBar"/);
  assert.match(source, /currentUserName/);
  assert.match(source, /isAdmin && <AdminTopBar pageLabel="GIÁM SÁT HUẤN LUYỆN" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.match(source, /training-coach-content/);
  assert.doesNotMatch(source, /NotificationBell/);
  assert.match(source, /canManageTrainingForRole\(role\)/);
  assert.match(app, /path="\/admin\/training" element=\{<CoachTraining \{\.\.\.staff\} \/>\}/);
  assert.match(app, /path="\/coach\/training" element=\{<CoachTraining \{\.\.\.coach\} \/>\}/);
});
