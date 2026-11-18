import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = (path) => readFile(new URL(`../src/${path}`, import.meta.url), "utf8");

test("Levels & Assessments removes only the process indicator and retains assessment interactions", async () => {
  const [page, css] = await Promise.all([
    read("pages/MemberLevelsAssessments.jsx"),
    read("pages/MemberLevelsAssessments.css"),
  ]);
  assert.doesNotMatch(page, /mla-stepper|mla-step-tag-row|mla-step1-header|Quy trình 2 bước|Chọn môn|Xem hồ sơ &amp; Lịch sử/);
  assert.doesNotMatch(css, /mla-stepper|mla-step-tag-row|mla-step1-header|mla-stepper-pill/);
  assert.match(page, /mla-content-header-copy[\s\S]*?<h1>/);
  assert.match(page, /value=\{searchQuery\}[\s\S]*?setSearchQuery/);
  assert.match(page, /openRequestModal\(selectedSport\?\.id\)/);
  assert.match(page, /handleSelectSport\(sport\.id\)/);
  assert.match(page, /mla-filter-tab/);
  assert.match(page, /mla-kpi-grid/);
});

test("Member Training list and result detail use the available content width with bounded responsive cards", async () => {
  const css = await read("pages/MemberTraining.css");
  assert.match(css, /\.member-training-shell\s*\{[^}]*width: 100%; max-width: none; margin: 0;[^}]*padding: 28px clamp\(20px, 3vw, 36px\)/);
  assert.match(css, /\.training-detail-shell\s*\{[^}]*width: 100%; max-width: none;[^}]*padding: 28px clamp\(20px, 3vw, 36px\)/);
  assert.match(css, /\.member-training-class-list\s*\{[^}]*360px/);
  assert.match(css, /@media \(max-width: 760px\)[\s\S]*?\.member-training-class-list \{ grid-template-columns: minmax\(0, 1fr\); \}/);
});

test("Member Attendance page uses the padded content width without changing its filtering states", async () => {
  const [css, page] = await Promise.all([read("pages/MemberAttendance.css"), read("pages/MemberAttendance.jsx")]);
  assert.match(css, /\.member-attendance-main \{ min-width: 0; padding: 0; \}/);
  assert.match(css, /\.member-attendance-page\s*\{ width: 100%; max-width: none; margin: 0; padding: 28px clamp\(20px, 3vw, 36px\) 56px;/);
  assert.match(page, /value=\{statusFilter\}[\s\S]*?setStatusFilter/);
  assert.match(page, /member-attendance-empty/);
  assert.match(page, /getMyAttendanceApi\(token\)/);
});

test("Member PT Booking uses full content width and Vietnamese copy without changing API values", async () => {
  const [css, page] = await Promise.all([read("pages/PtBooking.css"), read("pages/PtBooking.jsx")]);
  assert.match(css, /\.pt-booking-layout \.pt-main\s*\{ padding: 0; \}/);
  assert.match(css, /\.pt-booking-layout \.pt-page\s*\{[^}]*max-width: none;[^}]*padding: 28px clamp\(20px, 3vw, 36px\) 56px/);
  for (const label of ["Đặt buổi huấn luyện cá nhân", "Mỗi buổi tập kéo dài 90 phút", "Chọn bộ môn và thời gian bắt đầu", "Bộ môn", "Chọn bộ môn", "Thời gian bắt đầu", "Tìm huấn luyện viên phù hợp"]) {
    assert.ok(page.includes(label), `Missing Vietnamese label: ${label}`);
  }
  assert.match(page, /getPtEligibleCoachesApi\(token, sportId, startTime\)/);
  assert.match(page, /createPtBookingApi\(token, \{ coachId: choice\.coachId, sportId: Number\(sportId\), startTime \}\)/);
  assert.match(page, /value="ONLINE"|"ONLINE"/);
  assert.match(page, /value="COUNTER"|"COUNTER"/);
  assert.match(page, /paymentExpiresAt/);
});
