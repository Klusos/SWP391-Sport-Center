import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

test("staff account creation treats a submitted Consultation DOB as read-only", async () => {
  const source = await readFile(new URL("../src/pages/ConsultationQueue.jsx", import.meta.url), "utf8");
  assert.match(source, /detail\.dateOfBirth\s*\?\s*\(/);
  assert.match(source, /consultation-member-dob-readonly/);
  assert.doesNotMatch(source, /kh\\u00f4ng th\\u1ec3 thay/);
  assert.match(source, /consultation-member-dob-readonly/);
  assert.match(source, /detail\.dateOfBirth/);
  assert.match(source, /value=\{memberDateOfBirth \|\| ""\}/);
  assert.match(source, /onChange=\{\(event\) => setMemberDateOfBirth\(event\.target\.value\)\}/);
  assert.match(source, /role === "ADMIN" \|\| \(role === "RECEPTIONIST"/);
});
