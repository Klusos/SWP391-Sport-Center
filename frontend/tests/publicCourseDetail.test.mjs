import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { formatPublicCourseAgeRange } from "../src/public-site/pages/publicCourseDetailPresentation.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Public course information follows the requested hierarchy and stays API-backed", async () => {
  const source = await read("../src/public-site/pages/CourseDetail.jsx");
  const title = source.indexOf('{!memberRoute && <h1>{course.name}</h1>}');
  const sport = source.indexOf('{!memberRoute && <p className="course-public-sport">');
  const description = source.indexOf('{!memberRoute && course.description &&');
  const facts = source.indexOf('{!memberRoute && <dl className="course-public-facts">');
  const outcome = source.indexOf('{!memberRoute && course.learningOutcome &&');
  const actions = source.indexOf('{!memberRoute && <div className="course-public-actions">');
  assert.ok(title >= 0 && title < sport && sport < description && description < facts && facts < outcome && outcome < actions);
  assert.match(source, /getPublicCourseApi\(courseId\)/);
  assert.match(source, /to=\{course \? "\/bo-mon\/" \+ course\.sportId/);
  assert.match(source, /course\.learningOutcome/);
  assert.doesNotMatch(source, /course\.price|course\.schedule|course\.capacity/);
});

test("Public age facts omit an unspecified range and retain the API policy label for partial bounds", async () => {
  const source = await read("../src/public-site/pages/CourseDetail.jsx");
  assert.match(source, /\(course\.targetAgeMin != null \|\| course\.targetAgeMax != null\) && <div>[\s\S]*?courseAgePolicyLabel\(course\)/);
  assert.match(source, /formatPublicCourseAgeRange\(course\)/);
  assert.equal(formatPublicCourseAgeRange({ targetAgeMin: 12, targetAgeMax: 16 }), "12–16 tuổi");
  assert.equal(formatPublicCourseAgeRange({ targetAgeMin: 16 }), "Từ 16 tuổi");
  assert.equal(formatPublicCourseAgeRange({ targetAgeMax: 65 }), "Đến 65 tuổi");
  assert.equal(formatPublicCourseAgeRange({}), "");
});

test("Guest consultation and login actions retain their separate destinations and handlers", async () => {
  const source = await read("../src/public-site/pages/CourseDetail.jsx");
  assert.match(source, /<Link to="\/#public-consultation" className="btn btn-cyan course-public-action-primary">Đăng ký tư vấn<\/Link>/);
  assert.match(source, /!isMember && !auth && <button[^>]*onClick=\{goToLogin\}>Đăng nhập để đăng ký/);
  assert.match(source, /navigate\("\/login", \{ state: \{ from: location\.pathname \} \}\)/);
});

test("new presentation rules are scoped to Public Course Detail and preserve focus on actions", async () => {
  const css = await read("../src/public-site/pages/CourseDetail.css");
  assert.match(css, /\.course-public-detail \{ width: 100%; max-width: 900px; margin-right: auto; margin-left: auto; \}/);
  assert.match(css, /\.course-public-detail \.course-public-actions/);
  assert.match(css, /\.course-public-detail \.course-public-actions \.btn:focus-visible/);
  assert.match(css, /@media \(max-width: 650px\)[\s\S]*?\.course-public-detail \.course-public-actions \.btn \{ width: 100%/);
  assert.match(css, /\.course-public-detail \.course-public-facts/);
});
