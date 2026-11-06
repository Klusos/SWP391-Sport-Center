import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
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
  assert.equal(buildCoursePayload(form).agePolicy, "RECOMMENDED");
  assert.throws(() => buildCoursePayload({ ...form, sportId: "" }), /chọn Sport/i);
});

test("course age policy payload defaults to RECOMMENDED and validates REQUIRED ranges", () => {
  const base = { sportId: "2", name: "Course", level: "BEGINNER" };
  assert.equal(buildCoursePayload(base).agePolicy, "RECOMMENDED");
  assert.throws(() => buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "8" }), /cả tuổi tối thiểu và tối đa/i);
  assert.throws(() => buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "12", targetAgeMax: "8" }), /không được lớn hơn/i);
  assert.throws(() => buildCoursePayload({ ...base, targetAgeMin: "-1" }), /số nguyên không âm/i);
  assert.deepEqual(
    { ...buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "5", targetAgeMax: "9" }) },
    { name: "Course", description: "", targetAgeMin: 5, targetAgeMax: 9, agePolicy: "REQUIRED", level: "BEGINNER", learningOutcome: "", sportId: 2 },
  );
});

test("course update payload preserves existing update contract without changing persisted Sport", () => {
  const payload = buildCoursePayload({ sportId: "2", name: "Updated", level: "ADVANCED", status: "INACTIVE" }, true);
  assert.equal("sportId" in payload, false);
  assert.equal(payload.status, "INACTIVE");
});

test("Admin Course Form exposes policy, age safety guidance, and disables invalid REQUIRED ranges", async () => {
  const form = await readFile(new URL("../src/pages/CourseForm.jsx", import.meta.url), "utf8");
  assert.match(form, /name="agePolicy" value=\{form\.agePolicy\}/);
  assert.match(form, /value="RECOMMENDED">Độ tuổi khuyến nghị/);
  assert.match(form, /value="REQUIRED">Độ tuổi bắt buộc/);
  assert.match(form, /form\.agePolicy === "REQUIRED" && \(ageMin == null \|\| ageMax == null\)/);
  assert.match(form, /trẻ dưới 10 tuổi/);
  assert.match(form, /disabled=\{busy \|\| ageRangeInvalid\}/);
});
