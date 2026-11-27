import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("Admin and Coach Training keep role-scoped course-to-class selection before the existing workspace", async () => {
  const page = await readFile(new URL("../src/pages/CoachTraining.jsx", import.meta.url), "utf8");
  const styles = await readFile(new URL("../src/pages/Training.css", import.meta.url), "utf8");
  assert.match(page, /getTrainingClassesApi\(token\)/);
  assert.match(page, /const visibleCourses = courses\.filter/);
  assert.match(page, /const visibleClasses = classes\.filter/);
  assert.match(page, /setSelectedCourseId\(String\(item\.courseId\)\)/);
  assert.match(page, /item\.endDate >= selectionFrom/);
  assert.match(page, /item\.startDate <= selectionTo/);
  assert.match(page, /1\. Giáo án & bài tập/);
  assert.match(page, /2\. Buổi học & kết quả/);
  assert.match(page, /3\. Đánh giá tiến độ/);
  assert.match(page, /4\. Giáo án cá nhân/);
  assert.match(page, /Giám sát huấn luyện cá nhân/);
  assert.match(styles, /\.training-selection-list/);
  assert.match(page, /training-selection training-content-frame/);
  assert.match(page, /training-workspace-frame training-content-frame/);
  assert.match(page, /training-selection-heading[\s\S]*?training-selection-back-row/);
  assert.match(page, /training-workspace-heading[\s\S]*?training-workspace-back-row/);
  assert.match(styles, /\.training-selection-back-row,\.training-workspace-back-row \{ display:flex; justify-content:flex-start;/);
  assert.match(styles, /\.training-selection-heading,\.training-workspace-heading \{ padding-bottom:14px; border-bottom:1px solid #e1eaf4; margin-bottom:0; \}/);
  assert.match(styles, /\.training-selection-filters\.has-class-filters \{ grid-template-columns:minmax\(0,2fr\) repeat\(2,minmax\(0,1fr\)\); \}/);
});
