import test from "node:test";
import assert from "node:assert/strict";
import { buildCoursePayload, filterCourses } from "../src/courseManagementModel.js";

const courses = [
  { id: 1, name: "Khóa Cờ Vua U16", sportId: 2, sportName: "Cờ Vua", status: "ACTIVE" },
  { id: 2, name: "Cầu lông cơ bản", sportId: 1, sportName: "Cầu lông", status: "INACTIVE" },
];

test("course list shows all sports by default and filters only the displayed results", () => {
  assert.deepEqual(filterCourses(courses), courses);
  assert.deepEqual(filterCourses(courses, { search: "cờ vua" }).map((course) => course.id), [1]);
  assert.deepEqual(filterCourses(courses, { sportId: "1" }).map((course) => course.id), [2]);
  assert.deepEqual(filterCourses(courses, { status: "active" }).map((course) => course.id), [1]);
});

test("new course payload requires and submits the explicit form Sport selection", () => {
  const form = { sportId: "2", name: "Khóa Cờ Vua U16", level: "BEGINNER", targetAgeMin: "8", targetAgeMax: "16" };
  assert.equal(buildCoursePayload(form).sportId, 2);
  assert.throws(() => buildCoursePayload({ ...form, sportId: "" }), /chọn Sport/i);
});

test("course update payload preserves existing update contract without changing persisted Sport", () => {
  const payload = buildCoursePayload({ sportId: "2", name: "Updated", level: "ADVANCED", status: "INACTIVE" }, true);
  assert.equal("sportId" in payload, false);
  assert.equal(payload.status, "INACTIVE");
});
