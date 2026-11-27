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
  assert.match(page, /openRequestModal\(selectedSport\.id\)/);
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

test("Member Attendance page uses a full-width paged class overview and class detail", async () => {
  const [css, page] = await Promise.all([read("pages/MemberAttendance.css"), read("pages/MemberAttendance.jsx")]);
  assert.match(css, /\.member-attendance-main \{ min-width: 0; padding: 0; \}/);
  assert.match(css, /\.member-attendance-page\s*\{ width: 100%; max-width: none; margin: 0; padding: 28px clamp\(20px, 3vw, 36px\) 56px;/);
  assert.match(page, /value=\{yearFilter\}[\s\S]*?setYearFilter/);
  assert.match(page, /value=\{sportFilter\}[\s\S]*?setSportFilter/);
  assert.match(page, /paginateAttendance\(filteredClasses, classPage, 6\)/);
  assert.match(page, /paginateAttendance\(selectedClass\?\.sessions \|\| \[\], sessionPage, 10\)/);
  assert.doesNotMatch(page, /statusFilter|setStatusFilter/);
  assert.match(page, /member-attendance-empty/);
  assert.match(page, /getMyAttendanceOverviewApi\(token\)/);
  assert.match(page, /<strong>Huấn luyện viên:<\/strong>/);
  assert.match(css, /member-attendance-page \.member-attendance-status\.status-absent \{ background:#f8d7da; color:#842029; \}/);
  assert.match(css, /member-attendance-page \.member-attendance-status\.status-excused \{ background:#fff0bf; color:#795900; \}/);
});

test("Member Dashboard uses flexible horizontal skill cards and compact recent-attendance rows", async () => {
  const css = await read("pages/Membertheme.css");
  const dashboardCss = await read("pages/Dashboard.css");
  const dashboard = await read("pages/Dashboard.jsx");
  assert.match(css, /\.md-levels \{ display:flex; flex-wrap:wrap;/);
  assert.match(css, /\.md-levels div \{ display:flex; flex:0 0 auto; align-items:center;/);
  assert.match(css, /\.md-levels b \{ flex:0 0 auto;[^}]*white-space:nowrap/);
  assert.match(dashboardCss, /\.md-attendance-list \{ display:grid;/);
  assert.match(dashboardCss, /\.md-attendance-row \{ display:grid;[^}]*grid-template-columns:58px minmax\(0,1fr\) auto/);
  assert.match(dashboardCss, /\.coach-class-pager button \{ display:flex; align-items:center; justify-content:center;[^}]*width:34px; height:34px/);
  assert.match(dashboardCss, /\.coach-class-pager-icon \{ display:block; width:16px; height:16px/);
  assert.match(dashboard, /className="md-attendance-row"/);
  assert.match(dashboard, /className="md-attendance-info"/);
  assert.match(dashboard, /className="md-link" onClick=\{\(\) => onNavigate\("member-attendance"\)\}/);
  assert.doesNotMatch(dashboard, /md-attendance-table-wrap|md-attendance-content \.md-wide/);
});

test("Member routes share the bounded desktop shell and blue-white header rhythm", async () => {
  const css = await read("pages/Membertheme.css");
  assert.match(css, /\.layout:has\(> \.portal-topbar--member\) > \.main\s*\{[^}]*max-width: 1680px/);
  assert.match(css, /\.member-membership-header, \.member-attendance-header, \.schedule-page-header/);
  assert.match(css, /background: linear-gradient\(120deg, #edf6ff, #fff 78%\)/);
  assert.match(css, /\.pt-booking-layout \.pt-header/);
  assert.match(css, /\.support-member-content \.support-heading/);
  assert.match(css, /\.mp-header/);
  assert.match(css, /\.member-course-page-header/);
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
