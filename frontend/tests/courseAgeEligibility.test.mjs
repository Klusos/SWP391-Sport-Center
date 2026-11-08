import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { ageOnVietnamBusinessDate, courseAgePolicyLabel, evaluateCourseAgeEligibility } from "../src/courseAgeEligibility.js";

const dobAtAge = (age, today = "2026-11-05") => `${Number(today.slice(0, 4)) - age}-01-01`;
const evaluate = (age, course) => evaluateCourseAgeEligibility(course, dobAtAge(age), "2026-11-05").decision;

test("RECOMMENDED allows adults outside range, warns below minimum, and does not warn above maximum", () => {
  const course = { agePolicy: "RECOMMENDED", targetAgeMin: 12, targetAgeMax: 16 };
  assert.equal(evaluate(25, course), "ALLOWED");
  assert.equal(evaluate(14, course), "ALLOWED");
  assert.equal(evaluate(10, { ...course, targetAgeMin: 12 }), "ALLOWED_WITH_WARNING");
  assert.equal(evaluate(25, { agePolicy: "RECOMMENDED" }), "ALLOWED");
});

test("under-10 safety rule applies to every policy and requires complete in-range bounds", () => {
  assert.equal(evaluate(8, { agePolicy: "RECOMMENDED", targetAgeMin: 12, targetAgeMax: 16 }), "BLOCKED");
  assert.equal(evaluate(8, { agePolicy: "RECOMMENDED", targetAgeMin: 5, targetAgeMax: 9 }), "ALLOWED");
  assert.equal(evaluate(8, { agePolicy: "REQUIRED", targetAgeMin: 5, targetAgeMax: 9 }), "ALLOWED");
  assert.equal(evaluate(8, { agePolicy: "RECOMMENDED", targetAgeMin: 5 }), "BLOCKED");
  assert.equal(evaluate(10, { agePolicy: "RECOMMENDED", targetAgeMin: 12, targetAgeMax: 16 }), "ALLOWED_WITH_WARNING");
});

test("REQUIRED boundaries are inclusive and outside or incomplete ranges are blocked", () => {
  const course = { agePolicy: "REQUIRED", targetAgeMin: 5, targetAgeMax: 9 };
  assert.equal(evaluate(5, course), "ALLOWED");
  assert.equal(evaluate(9, course), "ALLOWED");
  assert.equal(evaluate(25, course), "BLOCKED");
  assert.equal(evaluate(25, { agePolicy: "REQUIRED", targetAgeMin: 5 }), "BLOCKED");
  assert.equal(evaluate(25, { agePolicy: "REQUIRED", targetAgeMin: 12, targetAgeMax: 8 }), "BLOCKED");
});

test("missing, malformed, future DOB and malformed business date fail closed", () => {
  assert.equal(evaluateCourseAgeEligibility({ agePolicy: "RECOMMENDED" }, null, "2026-11-05").decision, "BLOCKED");
  assert.equal(ageOnVietnamBusinessDate("2027-01-01", "2026-11-05"), null);
  assert.equal(ageOnVietnamBusinessDate("2015-02-30", "2026-11-05"), null);
  assert.equal(ageOnVietnamBusinessDate("2016-11-06", "2026-11-05"), 9);
});

test("policy labels default backward compatibly and distinguish REQUIRED", () => {
  assert.equal(courseAgePolicyLabel({}), "Độ tuổi khuyến nghị");
  assert.equal(courseAgePolicyLabel({ agePolicy: "REQUIRED" }), "Độ tuổi bắt buộc");
});

test("Member Course Detail presents blocked and advisory states while keeping public policy text", async () => {
  const detail = await readFile(new URL("../src/public-site/pages/CourseDetail.jsx", import.meta.url), "utf8");
  assert.match(detail, /ageEligibility\?\.decision !== "BLOCKED"/);
  assert.match(detail, /member-course-age-blocked/);
  assert.match(detail, /member-course-age-warning/);
  assert.match(detail, /courseAgePolicyLabel\(course\)/);
  assert.match(detail, /getCurrentUserApi\(auth\.accessToken\)/);
  assert.match(detail, /profileLoaded && !profile\?\.dateOfBirth/);
  assert.match(detail, /!memberRoute && <p><strong>\{courseAgePolicyLabel\(course\)\}/);
});
