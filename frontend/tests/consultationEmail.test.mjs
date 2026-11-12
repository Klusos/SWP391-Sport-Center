import assert from "node:assert/strict";
import test from "node:test";
import { readFile } from "node:fs/promises";
import { CONSULTATION_EMAIL_ERROR, normalizeConsultationEmail } from "../src/consultationEmail.js";

const source = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("consultation email normalization trims valid addresses and rejects blank or malformed values", () => {
  assert.equal(normalizeConsultationEmail("  member@example.test  "), "member@example.test");
  for (const value of [null, undefined, "", "   ", "missing-at.example", "name @example.test", "a".repeat(146) + "@x.test"]) {
    assert.equal(normalizeConsultationEmail(value), "");
  }
  assert.match(CONSULTATION_EMAIL_ERROR, /email/i);
});

test("public consultation form requires email and preserves fields after client validation errors", async () => {
  const component = await source("../src/public-site/components/ConsultForm.jsx");
  assert.match(component, /name="email" type="email"[^>]*required/);
  assert.match(component, /onInvalid=\{\(event\) => \{ event\.preventDefault\(\); setError\(CONSULTATION_EMAIL_ERROR\)/);
  const handler = component.match(/async function handleSubmit\(event\) \{([\s\S]*?)\n  \}/)?.[1] || "";
  assert.match(handler, /normalizeConsultationEmail\(form\.email\)/);
  assert.match(handler, /if \(!email\)[\s\S]*?return;/);
  assert.match(handler, /email,/);
  assert.ok(handler.indexOf("await createPublicConsultationRequestApi") < handler.indexOf("setForm(EMPTY_FORM)"));
  assert.ok(handler.indexOf("await createPublicConsultationRequestApi") < handler.indexOf("setSent(true)"));
});

test("all existing consultation entry points require and send normalized email", async () => {
  const [courseDetail, modal, levels] = await Promise.all([
    source("../src/public-site/pages/CourseDetail.jsx"),
    source("../src/components/CourseConsultationModal.jsx"),
    source("../src/pages/MemberLevelsAssessments.jsx"),
  ]);
  assert.match(courseDetail, /normalizeConsultationEmail\(consultation\.email\)/);
  assert.match(courseDetail, /type="email" required/);
  assert.match(modal, /type="email"[^>]*required/);
  assert.match(levels, /normalizeConsultationEmail\(consultForm\.email\)/);
  assert.match(levels, /type="email"\s+required/);
  assert.doesNotMatch(courseDetail + levels, /email: [^\n]*\|\| null/);
});
