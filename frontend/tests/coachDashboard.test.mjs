import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  coachAssessmentSummary,
  confirmedEnrollmentCount,
  pendingCoachAssessments,
  upcomingTeachingSessions,
} from "../src/coachDashboard.js";
import { isMemberDashboardPreviewSession, memberLevelLabel, memberRecentAttendancePreview, paginateItems, sortMemberSchedule } from "../src/memberDashboard.js";
import { filterMemberTrainingRows, progressLevelLabel, trainingStartYears } from "../src/trainingPresentation.js";

test("Member dashboard preview is bounded to the current and next Vietnam-local week", () => {
  const now = new Date("2026-11-26T10:00:00+07:00"); // Thursday
  assert.equal(isMemberDashboardPreviewSession("2026-11-23T09:00:00", now), false);
  assert.equal(isMemberDashboardPreviewSession("2026-11-27T09:00:00", now), true);
  assert.equal(isMemberDashboardPreviewSession("2026-12-06T23:59:00", now), true);
  assert.equal(isMemberDashboardPreviewSession("2026-12-07T00:00:00", now), false);
  assert.deepEqual(paginateItems([1, 2, 3, 4, 5, 6, 7], 2, 6), { items: [7], page: 2, pages: 2, total: 7 });
  assert.equal(memberLevelLabel("ADVANCED"), "Nâng cao");
  assert.equal(progressLevelLabel(1), "Chưa đạt");
  assert.equal(progressLevelLabel(5), "Vượt mục tiêu");
});

test("Member training filters use Offering start year and reliable end dates for lifecycle", () => {
  const rows = [
    { courseClass: { classId: 1, startDate: "2026-12-10", endDate: "2027-02-10", sportId: 2 } },
    { courseClass: { classId: 2, startDate: "2027-01-10", endDate: "2027-02-10", sportId: 2 } },
    { courseClass: { classId: 3, startDate: "2026-06-01", endDate: "2026-09-01", sportId: 1 } },
    { courseClass: { classId: 4, startDate: "2026-05-01", endDate: null, sportId: 1 } },
  ];
  assert.deepEqual(trainingStartYears(rows), ["2027", "2026"]);
  assert.deepEqual(filterMemberTrainingRows(rows, { year: "2026", today: "2026-11-26" }).map((row) => row.courseClass.classId), [1, 3, 4]);
  assert.deepEqual(filterMemberTrainingRows(rows, { lifecycle: "NOT_ENDED", today: "2026-11-26" }).map((row) => row.courseClass.classId), [1, 2]);
  assert.deepEqual(filterMemberTrainingRows(rows, { lifecycle: "COMPLETED", today: "2026-11-26" }).map((row) => row.courseClass.classId), [3]);
  assert.deepEqual(filterMemberTrainingRows(rows, { sportId: "2", year: "2026", today: "2026-11-26" }).map((row) => row.courseClass.classId), [1]);
});

test("Member schedule preview sorts chronologically and paginates a complete eligible set in groups of five", () => {
  const now = new Date("2026-11-26T10:00:00+07:00");
  const dates = Array.from({ length: 8 }, (_, index) => new Date(Date.UTC(2026, 10, 27 + index)).toISOString().slice(0, 10));
  const all = sortMemberSchedule(dates.map((date, index) => ({ id: 7 - index, start: `${date}T10:00:00` })));
  const eligible = all.filter((item) => isMemberDashboardPreviewSession(item.start, now));
  assert.deepEqual(eligible.map((item) => item.id), [7, 6, 5, 4, 3, 2, 1, 0]);
  assert.deepEqual(paginateItems(eligible, 1, 5).items.map((item) => item.id), [7, 6, 5, 4, 3]);
  assert.deepEqual(paginateItems(eligible, 2, 5).items.map((item) => item.id), [2, 1, 0]);
  assert.equal(paginateItems(eligible, 2, 5).pages, 2);
});

test("Member Dashboard recent attendance shows four latest valid recorded sessions without an age cutoff", async () => {
  const now = new Date("2026-11-26T11:00:00+07:00");
  const rows = [
    { id: 1, startTime: "2025-01-01T08:00:00", markedAt: "2026-11-26T10:00:00", status: "PRESENT", validForStatistics: true },
    { id: 2, startTime: "2026-11-23T08:00:00", status: "PRESENT", validForStatistics: true },
    { id: 3, startTime: "2026-11-24T08:00:00", status: "PRESENT", validForStatistics: true },
    { id: 4, startTime: "2026-11-25T08:00:00", status: "ABSENT", validForStatistics: true },
    { id: 5, startTime: "2026-11-26T08:00:00", status: "EXCUSED", validForStatistics: true },
    { id: 6, startTime: "2026-11-26T09:00:00", status: "PRESENT", validForStatistics: true, sessionStatus: "CANCELLED" },
    { id: 7, startTime: "2026-11-26T10:00:00", status: "PRESENT", validForStatistics: false },
    { id: 8, startTime: "2026-11-26T10:30:00", status: "PENDING", validForStatistics: true },
  ];
  assert.deepEqual(memberRecentAttendancePreview(rows, now).map((item) => item.id), [5, 4, 3, 2]);
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  assert.match(dashboard, /<Card title="Lớp được phân công"\s+actions=/);
  assert.match(dashboard, /<Card title="Lịch dạy sắp tới">/);
  assert.doesNotMatch(dashboard, /<Card title="Lớp được phân công" note=/);
  assert.match(dashboard, /memberRecentAttendancePreview\(attendanceRecords, now\)/);
  assert.match(dashboard, /attendanceRecords\.filter\(\(a\) => a\.when[\s\S]*new Date\(a\.when\)\.getMonth\(\) === now\.getMonth\(\)/);
  assert.match(dashboard, /md-attendance-card[\s\S]*?className="md-link" onClick=\{\(\) => onNavigate\("member-attendance"\)\}/);
  assert.match(dashboard, /className="md-attendance-row"/);
  assert.match(dashboard, /a\.coachName/);
  assert.match(dashboard, /coach-class-pager-icon/);
  assert.match(dashboard, /schedulePageData\.items\.map/);
  assert.match(dashboard, /schedule\.length > 5/);
  assert.match(dashboard, /Xem tất cả lịch/);
});

test("Member training overview presents human names and filterable paginated data", async () => {
  const component = await readFile(new URL("../src/pages/MemberTraining.jsx", import.meta.url), "utf8");
  const styles = await readFile(new URL("../src/pages/MemberTraining.css", import.meta.url), "utf8");
  assert.match(component, /trainingStartYears\(trainingRows\)/);
  assert.match(component, /filterMemberTrainingRows\(trainingRows/);
  assert.match(component, /info\.courseName \|\| "Khóa học"/);
  assert.match(component, /info\.courseOfferingName/);
  assert.match(component, /filteredTrainingRows\.slice\(\(currentPage - 1\) \* 10, currentPage \* 10\)/);
  assert.match(component, /<label>Năm khai giảng/);
  assert.match(component, /<label>Bộ môn/);
  assert.match(styles, /\.member-training-pager/);
  assert.match(styles, /\.member-training-filters/);
  assert.match(component, /member-training-count" aria-live="polite"/);
  assert.match(component, /role="progressbar" aria-label=\{`Tiến độ buổi học/);
  assert.match(styles, /\.member-training-divider/);
  assert.match(styles, /\.member-training-progress-track/);
});

test("Coach summaries total confirmed class enrollments and upcoming sessions are sorted and exclude cancelled or past rows", () => {
  assert.equal(confirmedEnrollmentCount([{ confirmedMembers: 4 }, { confirmedMembers: 3 }]), 7);
  assert.equal(confirmedEnrollmentCount(null), 0);
  const result = upcomingTeachingSessions([
    { classSessionId: 8, startTime: "2099-02-02T09:00:00", sessionStatus: "SCHEDULED" },
    { classSessionId: 9, startTime: "2099-02-02T09:00:00", sessionStatus: "SCHEDULED" },
    { classSessionId: 3, startTime: "2099-02-01T09:00:00", sessionStatus: "CANCELLED" },
    { classSessionId: 4, startTime: "2099-02-01T10:00:00", sessionStatus: "COMPLETED" },
    { classSessionId: 2, startTime: "2000-01-01T09:00:00", sessionStatus: "SCHEDULED" },
  ], new Date("2099-01-01T00:00:00").getTime());
  assert.deepEqual(result.map((item) => item.classSessionId), [8, 9]);
});

test("Coach Dashboard uses coach-scoped class, session and PT APIs with per-source error and empty states", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const api = await readFile(new URL("../src/api.js", import.meta.url), "utf8");
  assert.match(dashboard, /COACH: \{[\s\S]*getAssignedPtBookingsApi,[\s\S]*getTrainingClassesApi,[\s\S]*getCoachAttendanceSessionsApi/);
  assert.match(api, /request\("\/api\/training\/classes"/);
  assert.match(api, /request\("\/api\/attendance\/coach\/sessions"/);
  assert.match(dashboard, /errors\.classes/);
  assert.match(dashboard, /errors\.teachingSessions/);
  assert.match(dashboard, /errors\.bookings/);
  assert.match(dashboard, /const shownClasses = classes\.slice\(\(Math\.min\(classPage, classPages\) - 1\) \* 6/);
  assert.match(dashboard, /coach-class-pager/);
  assert.doesNotMatch(dashboard, /showAllClasses/);
  assert.match(dashboard, /icon: "members"/);
  assert.match(dashboard, /icon: "clipboardCheck"/);
  assert.match(dashboard, /icon: "calendarClock"/);
  assert.match(dashboard, /icon: "dumbbell"/);
  assert.match(dashboard, /icon: "clipboardList"/);
  assert.match(dashboard, /coach-dashboard-header/);
  assert.match(dashboard, /classPage <= 1/);
  assert.match(dashboard, /Lượt đăng ký lớp đã xác nhận/);
  assert.match(dashboard, /Chưa có lớp nào được phân công/);
  assert.match(dashboard, /Không có buổi dạy sắp tới/);
});

test("KPI icons stay anchored independently of labels across portal dashboards", async () => {
  const [dashboard, memberStyles, guidance] = await Promise.all([
    readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/Membertheme.css", import.meta.url), "utf8"),
    readFile(new URL("../AGENTS.md", import.meta.url), "utf8"),
  ]);
  assert.match(dashboard, /grid-template-areas: "label icon" "value value" "note note"/);
  assert.match(dashboard, /grid-area: icon; position: static;[^}]*transform: translate\(5px, -5px\)/);
  assert.match(memberStyles, /\.md-stat header i \{ position: static;[^}]*transform: translate\(5px, -5px\)/);
  assert.match(guidance, /KPI cards across roles keep the domain icon anchored at the card's top-right/);
});

test("KPI icons stay anchored independently of labels across portal dashboards", async () => {
  const [dashboard, memberStyles, guidance] = await Promise.all([
    readFile(new URL("../src/pages/Dashboard.css", import.meta.url), "utf8"),
    readFile(new URL("../src/pages/Membertheme.css", import.meta.url), "utf8"),
    readFile(new URL("../AGENTS.md", import.meta.url), "utf8"),
  ]);
  assert.match(dashboard, /grid-template-areas: "label icon" "value value" "note note"/);
  assert.match(dashboard, /grid-area: icon; position: static;[^}]*transform: translate\(5px, -5px\)/);
  assert.match(memberStyles, /\.md-stat header i \{ position: static;[^}]*transform: translate\(5px, -5px\)/);
  assert.match(guidance, /KPI cards across roles keep the domain icon anchored at the card's top-right/);
});

test("Coach Dashboard includes only pending assessments and presents the assignment IDs when names are unavailable", () => {
  const assignments = [
    { id: 2, memberId: 7, sportId: 1, assignedCoachId: 4, status: "PENDING" },
    { id: 1, memberId: 7, sportId: 1, assignedCoachId: 4, status: "COMPLETED" },
    { id: 3, memberId: 8, sportId: 2, assignedCoachId: 4, status: "CANCELLED" },
  ];
  const pending = pendingCoachAssessments(assignments);
  assert.deepEqual(pending.map((item) => item.id), [2]);
  assert.deepEqual(coachAssessmentSummary(pending[0]), {
    member: "Học viên #7",
    sport: "Bộ môn #1",
    status: "Đang chờ",
    assignment: "Được phân công cho HLV #4",
  });
  assert.equal(coachAssessmentSummary({ ...pending[0], status: "COMPLETED" }).status, "Đã hoàn tất");
  assert.equal(coachAssessmentSummary({ ...pending[0], status: "COMPLETED" }).status, "Đã hoàn tất");
  assert.equal(coachAssessmentSummary({ ...pending[0], memberName: "Demo Member" }, [{ id: 1, name: "Badminton" }]).member, "Demo Member");
  assert.equal(coachAssessmentSummary(pending[0], [{ id: 1, name: "Badminton" }]).sport, "Badminton");
  assert.deepEqual(pendingCoachAssessments([]), []);
});

test("Coach Dashboard uses available names, stays Coach-scoped, and links to the existing assessment page", async () => {
  const dashboard = await readFile(new URL("../src/pages/Dashboard.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const coachLoader = dashboard.slice(dashboard.indexOf("  COACH: {"), dashboard.indexOf("  MEMBER: {"));
  assert.match(coachLoader, /assessments: getMySkillAssessmentAssignmentsApi/);
  assert.match(coachLoader, /sports: getSportsApi/);
  assert.doesNotMatch(coachLoader, /getConsultationRequestsApi|consultations/);
  assert.match(dashboard, /coachAssessmentSummary\(a, sports\)/);
  assert.match(dashboard, /to="\/coach\/skill-assessments"/);
  assert.match(app, /"coach-assessments": "\/coach\/skill-assessments"/);
});
