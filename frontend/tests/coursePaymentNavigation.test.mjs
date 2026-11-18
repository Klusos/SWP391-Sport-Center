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
  assert.match(payments, /useMemo\(\(\) => searchParams\.has\("paymentId"\)[\s\S]*parseCoursePaymentTarget\(searchParams\)/);
  assert.match(payments, /locateCoursePayment\(/);
  assert.match(payments, /setTargetLookup\(\{ key: lookupKey, message:/);
  assert.match(payments, /paymentTarget\?\.paymentId/);
  assert.doesNotMatch(payments, /setPaymentTarget/);
  const lookupEffect = payments.match(/useEffect\(\(\) => \{[\s\S]*?\}, \[paymentTarget, token\]\);/)?.[0] || "";
  assert.match(lookupEffect, /locateCoursePayment\([\s\S]*\.then\(/);
  assert.doesNotMatch(lookupEffect, /setSelectedCourseFilter|setUrgentOnly/);
  assert.match(lookupEffect, /let active = true;[\s\S]*if \(!active\) return;/);
  assert.match(payments, /onClick=\{\(\) => void controller\.current\?\.receive\(row\)\}/);
});

test("Receptionist registration renders inline step icons and polished guidance without changing Admin presentation", async () => {
  const [registration, css] = await Promise.all([
    readFile(new URL("../src/pages/ReceptionistCourseRegistration.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/ReceptionistCourseRegistration.css", import.meta.url), "utf8"),
  ]);
  assert.match(registration, /className=\{`receptionist-registration-content\$\{isAdmin \? " receptionist-registration-content--admin" : ""\}`\}/);
  assert.match(css, /\.receptionist-registration-content:not\(\.receptionist-registration-content--admin\) \.receptionist-step input,[\s\S]*?min-height: 44px;[\s\S]*?border-radius: var\(--radius-sm\);/);
  assert.match(css, /\.receptionist-registration-content:not\(\.receptionist-registration-content--admin\) \.receptionist-registration-header > button,[\s\S]*?background: var\(--blue\);[\s\S]*?font: inherit;/);
  assert.match(css, /\.receptionist-registration-content:not\(\.receptionist-registration-content--admin\)[\s\S]*?button:hover:not\(:disabled\)[\s\S]*?background: var\(--blue-dark\);/);
  assert.equal((registration.match(/receptionist-step-number">0[1-3]/g) || []).length, 3, "Receptionist workflow shows three numbered steps");
  assert.equal((registration.match(/receptionist-step-icon/g) || []).length, 3, "Each Receptionist step has one contextual icon");
  assert.equal((registration.match(/receptionist-step-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1\.8"/g) || []).length, 3, "Every decorative step icon is inline SVG with explicit visible stroke styling");
  assert.equal((registration.match(/<path d="/g) || []).length >= 5, true, "The inline SVGs contain actual path geometry");
  assert.doesNotMatch(registration, /receptionist-step-icon" aria-hidden="true"><i\b/, "Step icons do not rely on the Bootstrap icon font pseudo-element");
  assert.match(css, /\.receptionist-step-icon svg\s*\{[\s\S]*?width: 20px;[\s\S]*?height: 20px;[\s\S]*?color: inherit/);
  assert.match(registration, /<h2>Chọn khóa học và lớp<\/h2><p>Bộ môn → Khóa học → Đợt mở lớp → Lớp<\/p>/);
  assert.match(registration, /Giữ chỗ chờ thanh toán có thể hết hạn[\s\S]*?chỉ xác nhận khi đã thực sự nhận tiền[\s\S]*?REFUND_REQUIRED[\s\S]*?không được tự động khôi phục/);
  assert.match(registration, /receptionist-pending-guidance/);
  assert.match(css, /\.receptionist-step-heading\s*\{[\s\S]*?background: #f3f8fd;[\s\S]*?border-radius/);
  assert.match(css, /\.receptionist-step > label\s*\{[\s\S]*?gap: 8px;[\s\S]*?margin-top: 16px/);
  assert.match(css, /\.receptionist-pending-guidance\s*\{[\s\S]*?background: #f2f8fd/);
  assert.match(css, /@media \(max-width: 1100px\)[\s\S]*?receptionist-course-selects[\s\S]*?grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(css, /@media \(max-width: 620px\)[\s\S]*?receptionist-course-selects[\s\S]*?grid-template-columns: 1fr/);
  assert.match(registration, /isAdmin \? <h2>1\./);
  assert.match(registration, /createStaffCourseRegistrationApi\(/);
  assert.match(registration, /createPaymentApi\(/);
});

test("Course Payments gives Admin one shared Top Bar while preserving the Receptionist counter header and clock", async () => {
  const [payments, css, app] = await Promise.all([
    readFile(new URL("../src/pages/ReceptionistCoursePayments.jsx", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/ReceptionistCoursePayments.css", import.meta.url), "utf8"),
    readFile(new URL("../src/App.jsx", import.meta.url), "utf8"),
  ]);
  assert.match(payments, /isAdmin && <AdminTopBar pageLabel="THANH TOÁN KHÓA HỌC" displayName=\{currentUserName\} avatarName=\{currentUserName\} \/>/);
  assert.equal((payments.match(/<AdminTopBar\b/g) || []).length, 1);
  assert.match(payments, /isAdmin && <div className="cp-topbar cp-topbar--admin">[\s\S]*?cp-topbar-right[\s\S]*?cp-clock-pill[\s\S]*?liveClockLabel/);
  assert.match(payments, /!isAdmin && <div className="cp-clock-pill cp-header-clock"[\s\S]*?liveClockLabel/);
  assert.doesNotMatch(payments, /cp-breadcrumb/);
  assert.match(payments, /timeZone: "Asia\/Ho_Chi_Minh"[\s\S]*?\(GMT\+7\)/);
  assert.match(payments, /setInterval\(\(\) => setNow\(Date\.now\(\)\), 1000\)/);
  assert.match(payments, /canViewCoursePayments\(role\)/);
  assert.doesNotMatch(payments, /NotificationBell/);
  assert.match(css, /\.course-payment-queue--admin \.cp-topbar--admin,[\s\S]*?\.pt-payment-queue--admin \.cp-topbar--admin\s*\{\s*position: static/);
  assert.match(css, /\.course-payment-queue--admin \.cp-body--admin,[\s\S]*?\.pt-payment-queue--admin \.cp-body--admin/);
  assert.match(css, /\.cp-header-clock\s*\{[\s\S]*?white-space: nowrap/);
  assert.match(app, /path="\/receptionist\/course-payments" element=\{<ReceptionistCoursePayments \{\.\.\.staff\} userId=\{auth\?\.userId\} \/>\}/);
  assert.match(app, /currentUserName: auth\?\.name/);
});
