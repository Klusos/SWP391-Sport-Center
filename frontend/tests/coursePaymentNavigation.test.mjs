import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { locateCoursePayment, parseCoursePaymentTarget } from "../src/coursePaymentNavigation.js";

test("navigation parameters require a valid payment ID and validate the optional registration ID", () => {
  assert.deepEqual(parseCoursePaymentTarget(new URLSearchParams("paymentId=12&registrationId=34")), { valid: true, paymentId: 12, registrationId: 34 });
  assert.deepEqual(parseCoursePaymentTarget(new URLSearchParams("paymentId=12")), { valid: true, paymentId: 12, registrationId: null });
  assert.deepEqual(parseCoursePaymentTarget(new URLSearchParams("paymentId=abc")), { valid: false });
  assert.deepEqual(parseCoursePaymentTarget(new URLSearchParams("paymentId=12&registrationId=0")), { valid: false });
});

test("the exact payment is searched across pages and registration mismatch never selects another row", async () => {
  const pages = [
    { totalPages: 2, content: [{ paymentId: 3, registrationId: 8 }] },
    { totalPages: 2, content: [{ paymentId: 12, registrationId: 34 }] },
  ];
  const fetches = [];
  const fetchPage = async (page, size) => { fetches.push([page, size]); return pages[page]; };
  const found = await locateCoursePayment(fetchPage, { valid: true, paymentId: 12, registrationId: 34 });
  assert.deepEqual(found, { found: true, page: 1, row: pages[1].content[0] });
  assert.deepEqual(fetches, [[0, 20], [1, 20]]);
  assert.deepEqual(await locateCoursePayment(async () => pages[1], { valid: true, paymentId: 12, registrationId: 99 }), { found: false, reason: "not-found" });
});

test("receptionist registration navigates instead of confirming; payment page highlights only exact queued records", async () => {
  const [registration, payments] = await Promise.all([
    readFile(new URL("../src/pages/ReceptionistCourseRegistration.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/ReceptionistCoursePayments.jsx", import.meta.url), "utf8"),
  ]);
  assert.match(registration, /navigate\(`\/receptionist\/course-payments\?paymentId=\$\{encodeURIComponent\(payment\.id\)\}&registrationId=\$\{encodeURIComponent\(registration\.id\)\}`\)/);
  assert.doesNotMatch(registration, /processCounterPaymentApi|confirmReceipt/);
  assert.match(registration, /createOrReusePayment\(registration\)/);
  assert.match(payments, /parseCoursePaymentTarget\(searchParams\)/);
  assert.match(payments, /locateCoursePayment\(/);
  assert.match(payments, /setTargetMessage\(`[^`]*#\$\{target\.paymentId\}[^`]*`\)/);
  assert.match(payments, /paymentTarget\?\.paymentId === Number\(row\.paymentId\)/);
  assert.match(payments, /onClick=\{\(\) => void controller\.current\?\.receive\(row\)\}/);
});
