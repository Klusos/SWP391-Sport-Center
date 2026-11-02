import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  formatClassScheduleSummary,
  formatTargetAgeRange,
  toggleExpandedClass,
} from "../src/memberCoursePresentation.js";

const read = (path) => readFile(new URL(path, import.meta.url), "utf8");

test("Member course cards use the existing Member detail route and shared CourseDetail component", async () => {
  const [courses, app] = await Promise.all([
    read("../src/pages/MemberCourses.jsx"),
    read("../src/App.jsx"),
  ]);
  assert.match(courses, /to=\{`\/member\/courses\/\$\{course\.id\}`\}/);
  assert.match(app, /path="\/member\/courses\/:courseId"[\s\S]*?<CourseDetail auth=\{auth\} memberRoute/);
  assert.doesNotMatch(app, /<MemberCourseDetail/);
});

test("Member course cards display only catalog-provided level and target-age badges", async () => {
  const courses = await read("../src/pages/MemberCourses.jsx");
  const css = await read("../src/pages/MemberCourses.css");
  assert.match(courses, /course\.level && <span className="member-course-card-meta-badge">\{course\.level\}<\/span>/);
  assert.match(courses, /formatTargetAgeRange\(course\.targetAgeMin, course\.targetAgeMax\)/);
  assert.match(courses, /member-course-card-meta-badge age/);
  assert.ok(css.includes(".member-course-card-meta-badge"));
  assert.equal(formatTargetAgeRange(8, 14), "8–14 tuổi");
  assert.equal(formatTargetAgeRange(8, null), "Từ 8 tuổi");
  assert.equal(formatTargetAgeRange(null, 14), "Đến 14 tuổi");
  assert.equal(formatTargetAgeRange(null, undefined), "");
});

test("catalog registration badges reuse loaded member history and stay hidden while unavailable", async () => {
  const courses = await read("../src/pages/MemberCourses.jsx");
  const css = await read("../src/pages/MemberCourses.css");
  assert.match(courses, /courseRegistrationStatus\(registrations, course\.id\)/);
  assert.match(courses, /!registrationsLoading && !registrationsError/);
  assert.match(courses, /registrationStatus === "PENDING_PAYMENT" \? "Chờ thanh toán" : "Đã đăng ký"/);
  assert.match(courses, /member-course-registration-badge/);
  assert.match(courses, /<div className="member-course-title-row"><h3>\{course\.name\}<\/h3>[\s\S]*?member-course-registration-badge[\s\S]*?<\/div>\s*\{\(course\.level/);
  assert.match(css, /\.member-course-title-row \{ display: flex; flex-wrap: wrap;[^}]*gap:/);
  assert.match(css, /\.member-course-card-body h3 \{ flex: 1 1 180px; min-width: 0;[^}]*overflow-wrap: anywhere/);
  assert.match(css, /\.member-course-card \{[^}]*min-height: 245px/);
  assert.match(courses, /getMyRegistrationsApi\(token\)/);
});

test("Member course grid uses a fluid shell and responsive one, two, and three-column layouts", async () => {
  const css = await read("../src/pages/MemberCourses.css");
  assert.match(css, /width: min\(1560px, 100%\)/);
  assert.match(css, /\.member-course-grid \{ display: grid; grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
  assert.match(css, /@media \(min-width: 1750px\)[\s\S]*?grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(css, /@media \(max-width: 700px\)[\s\S]*?\.member-course-grid \{ grid-template-columns: 1fr; \}/);
  assert.match(css, /\.member-course-card \{[^}]*max-width: 480px/);
});

test("Member course detail selects real offerings and classes and displays authoritative catalog values", async () => {
  const detail = await read("../src/public-site/pages/CourseDetail.jsx");
  assert.match(detail, /offerings\.map\(\(item\)/);
  assert.match(detail, /aria-selected=\{String\(item\.id\) === selectedOfferingId\}/);
  assert.match(detail, /setSelectedOfferingId\(String\(item\.id\)\)/);
  assert.match(detail, /formatPrice\(item\.price\)/);
  assert.match(detail, /formatPrice\(selectedOffering\.price\)/);
  assert.match(detail, /classes\.map\(\(item\)/);
  assert.match(detail, /checked=\{selectedClassId === String\(item\.id\)\}/);
  assert.match(detail, /item\.coachName && <span><b>Coach<\/b> \{item\.coachName\}<\/span>/);
  assert.match(detail, /item\.capacity \?\?/);
  assert.match(detail, /selectedOffering\.registrationDeadline/);
  assert.doesNotMatch(detail, /remainingSeats|capacity\s*-\s*.*registration/);
});

test("class schedule expansion is separate from radio selection and renders actual session details", async () => {
  const [detail, css] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/public-site/pages/CourseDetail.css"),
  ]);
  assert.match(detail, /<label className=\{`member-class-card/);
  assert.match(detail, /checked=\{selectedClassId === String\(item\.id\)\}/);
  assert.match(detail, /onChange=\{\(\) => \{ setSelectedClassId\(String\(item\.id\)\)/);
  assert.match(detail, /<button type="button" className="member-class-schedule-toggle" aria-expanded=\{expanded\} aria-controls=\{scheduleId\}/);
  assert.match(detail, /setExpandedScheduleClassId\(\(current\) => toggleExpandedClass\(current, item\.id\)\)/);
  assert.match(detail, /\{schedule\.map\(\(session\) => <li key=\{session\.id\}>[\s\S]*?formatLocalDateTime\(session\.startTime\)[\s\S]*?formatLocalDateTime\(session\.endTime\)[\s\S]*?session\.status/);
  assert.match(detail, /item\.capacity \?\? "—"/);
  assert.ok(css.includes(".member-class-schedule-toggle"));
  assert.ok(css.includes(".member-class-schedule-details"));
  assert.equal(toggleExpandedClass(null, 12), 12);
  assert.equal(toggleExpandedClass(12, 12), null);
  assert.equal(toggleExpandedClass(12, 13), 13);
  assert.equal(formatClassScheduleSummary([], () => "unused"), "Lịch chưa được công bố");
  assert.equal(formatClassScheduleSummary([{ startTime: "2026-11-02T09:00:00" }], (value) => value), "1 buổi được công bố · 2026-11-02T09:00:00");
  assert.equal(formatClassScheduleSummary([{ startTime: "x" }, { startTime: "y" }], () => "—"), "2 buổi được công bố");
});

test("registration remains PENDING_PAYMENT and keeps checkout, deadline, cancellation, and refresh behavior", async () => {
  const [detail, courses] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/pages/MemberCourses.jsx"),
  ]);
  assert.match(detail, /registration\.status === "PENDING_PAYMENT"[\s\S]*?\/member\/course-checkout/);
  assert.match(detail, /registration\.status === "CONFIRMED"/);
  assert.match(detail, /registration\.status === "EXPIRED"/);
  assert.match(detail, /formatPrice\(registration\.originalPrice\)/);
  assert.match(detail, /formatPrice\(registration\.membershipDiscount\)/);
  assert.match(detail, /formatPrice\(registration\.finalPrice\)/);
  assert.match(courses, /item\.status === "PENDING_PAYMENT"/);
  assert.match(courses, /item\.status === "CONFIRMED"/);
  assert.match(courses, /item\.status === "EXPIRED"/);
  assert.match(courses, /item\.paymentDeadline/);
  assert.match(courses, /cancelCourseRegistrationApi\(token, id\)/);
  assert.match(courses, /getMyRegistrationsApi\(token\)/);
  assert.match(courses, /window\.setInterval\(refreshRegistrations, 60_000\)/);
  assert.match(courses, /member\/course-checkout\/\$\{item\.id\}/);
});

test("insufficient verified sport level keeps the consultation path instead of registering", async () => {
  const detail = await read("../src/public-site/pages/CourseDetail.jsx");
  assert.match(detail, /consultationAvailable/);
  assert.match(detail, /Yêu cầu tư vấn \/ đánh giá trình độ/);
  assert.match(detail, /\/member\/levels-assessments/);
  assert.match(detail, /isOfferingOpenNow\(selectedOffering\)/);
  assert.match(detail, /setConfirmingRegistration\(true\)/);
  assert.match(detail, /consultationAvailable && <div className="member-course-level-help"/);
  assert.match(detail, /onClick=\{openConsultation\}>Yêu cầu tư vấn<\/button>/);
});

test("Member course back control is above the hero and returns to the course list", async () => {
  const [detail, css] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/public-site/pages/CourseDetail.css"),
  ]);
  assert.match(detail, /<main className="main member-course-detail"><div className="member-course-detail-shell">\s*<p className="member-course-back"><Link to="\/member\/courses"><span aria-hidden="true">←<\/span> Quay lại<\/Link><\/p>\s*<header className="member-course-page-header">/);
  assert.ok(css.includes(".member-course-detail .member-course-back"));
  assert.match(detail, /!memberRoute && <p className="course-detail-back"/);
});

test("Member consultation uses an accessible modal and retains submission, prefill, and success behavior", async () => {
  const [detail, modal, modalCss] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/components/CourseConsultationModal.jsx"),
    read("../src/components/CourseConsultationModal.css"),
  ]);
  assert.match(detail, /member-course-consultation-card" aria-labelledby="member-course-consultation-title"/);
  assert.match(detail, /<h2 id="member-course-consultation-title">Cần tư vấn trước khi đăng ký\?<\/h2>/);
  assert.match(detail, /memberRoute && consultationOpen && <CourseConsultationModal/);
  assert.match(detail, /createMemberConsultationRequestApi\(auth\.accessToken,\s*\{/);
  assert.match(detail, /name: consultation\.name\.trim\(\)/);
  assert.match(detail, /phone: consultation\.phone\.trim\(\)/);
  assert.match(detail, /setConsultationSuccess\(result\);\s*setConsultationOpen\(false\)/);
  assert.match(detail, /member-course-consultation-success" role="status"/);
  assert.match(detail, /getCurrentUserApi\(auth\.accessToken\)/);
  assert.match(detail, /consultationSubmitLock\.current/);
  assert.match(modal, /role="dialog"/);
  assert.match(modal, /aria-modal="true"/);
  assert.match(modal, /event\.key === "Escape"/);
  assert.match(modal, /event\.key !== "Tab"/);
  assert.match(modal, /course-consult-modal-close.*onClick=\{onClose\}/);
  assert.match(modal, /onClick=\{onClose\} disabled=\{submitting\}>Hủy/);
  assert.match(modal, /<form className="course-consult-modal-form" onSubmit=\{onSubmit\}>/);
  assert.ok(modalCss.includes("overflow-y: auto"));
  assert.ok(modalCss.includes("grid-template-columns: repeat(2"));
  assert.ok(modalCss.includes("grid-template-columns: minmax(0, 1fr)"));
  assert.match(detail, /!memberRoute && consultationOpen && <form className="course-member-consult-form"/);
});

test("Member-specific hero styling is scoped and public Course Detail remains on its public presentation", async () => {
  const [detail, css] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/public-site/pages/CourseDetail.css"),
  ]);
  assert.match(detail, /if \(memberRoute\) return <div className="layout">/);
  assert.match(detail, /<PageHero title=\{course\?\.name[\s\S]*?course-public-detail/);
  assert.ok(css.includes(".member-course-detail .member-course-page-header"));
  assert.ok(css.includes(".course-public-detail .course-detail-card"));
  assert.ok(css.includes(".member-course-detail .member-course-detail-badges"));
  assert.doesNotMatch(css, /(^|\n)\.member-course-detail-(?:badges|badge)\s*\{/);
});

test("Member Course Detail uses the same fluid max-width strategy without widening Public Course Detail", async () => {
  const [detail, css, legacyCss] = await Promise.all([
    read("../src/public-site/pages/CourseDetail.jsx"),
    read("../src/public-site/pages/CourseDetail.css"),
    read("../src/pages/MemberCourseDetail.css"),
  ]);
  assert.match(detail, /<main className="main member-course-detail"><div className="member-course-detail-shell">/);
  assert.match(css, /\.member-course-detail \{ width: 100%; max-width: none; min-width: 0; box-sizing: border-box; \}/);
  assert.match(css, /\.member-course-detail \.member-course-detail-shell \{ width: min\(1560px, 100%\)/);
  assert.match(css, /\.member-course-detail \.member-course-page-header \{[\s\S]*?max-width: none/);
  assert.match(css, /\.member-course-detail \.member-course-overview,[\s\S]*?\.member-course-detail \.course-registration-panel \{ width: 100%; max-width: none/);
  assert.match(css, /\.member-course-detail \.member-course-consultation-card \{ display: grid; width: 100%; max-width: none/);
  assert.match(css, /\.member-course-detail \.member-class-list \{ width: min\(1240px, 100%\)/);
  assert.match(css, /\.member-course-overview > p,[\s\S]*?max-width: 920px/);
  assert.match(css, /\.course-public-detail \{\s*max-width: 1120px;/);
  assert.match(detail, /return <><PageHero title=\{course\?\.name[\s\S]*?course-public-detail/);
  assert.match(legacyCss, /\.member-course-detail \{ width: 100%; max-width: 1280px; \}/);
});
