import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { isValidVietnamesePhone, PHONE_VALIDATION_ERROR } from "../src/phoneValidation.js";

const source = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Vietnamese phone validation trims outer whitespace and preserves optional values", () => {
  assert.equal(isValidVietnamesePhone(" 0901234567 "), true);
  assert.equal(isValidVietnamesePhone(null), true);
  assert.equal(isValidVietnamesePhone("   "), true);
  assert.equal(isValidVietnamesePhone("   ", { required: true }), false);
  for (const phone of ["09012abc67", "abcdefghij", "090123456", "09012345678", "1901234567",
    "090-123-456", "090 123456", "+84901234567", "٠٩٠١٢٣٤٥٦٧"]) {
    assert.equal(isValidVietnamesePhone(phone), false, phone);
  }
  assert.equal(PHONE_VALIDATION_ERROR, "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
});

test("phone validation is wired to Consultation and account forms without sanitizing input", async () => {
  const files = await Promise.all([
    source("../src/public-site/components/ConsultForm.jsx"),
    source("../src/public-site/pages/CourseDetail.jsx"),
    source("../src/pages/MemberLevelsAssessments.jsx"),
    source("../src/pages/RegisterMember.jsx"),
    source("../src/pages/MemberList.jsx"),
    source("../src/pages/StaffManagement.jsx"),
  ]);
  for (const text of files) assert.match(text, /isValidVietnamesePhone/);
  assert.match(files.join("\n"), /type="tel"/);
  assert.doesNotMatch(files.join("\n"), /phone\.replace\(|replace\([^\n]*phone/);
});
