import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  buildCoursePayload, classSessionVietnamDate, filterCourses, filterClassSessions, filterCourseClasses,
  filterCourseOfferings, formatClassSessionDate, formatClassSessionTime, offeringTimeStatus, paginateItems,
  qualifiedClassCoaches, vietnamBusinessDate,
} from "../src/courseManagementModel.js";
import { validateClassSessionSchedule } from "../src/classSessionValidation.js";
const managementPages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
const apiSource = await readFile(new URL("../src/api.js", import.meta.url), "utf8");

const courses = [
  { id: 1, name: "Khóa Cờ Vua U16", sportId: 2, sportName: "Cờ Vua", status: "ACTIVE" },
  { id: 2, name: "Cầu lông cơ bản", sportId: 1, sportName: "Cầu lông", status: "INACTIVE" },
];

test("course list shows all sports by default and filters only the displayed results", () => {
  assert.deepEqual(filterCourses(courses), courses);
  assert.deepEqual(filterCourses(courses, { search: "cờ vua" }).map((course) => course.id), [1]);
  assert.deepEqual(filterCourses(courses, { sportId: "1" }).map((course) => course.id), [2]);
  assert.deepEqual(filterCourses(courses, { status: "active" }).map((course) => course.id), [1]);
});

test("Class coach selection requires ACTIVE status and CoachSport match for the selected Sport ID", () => {
  const coaches = [
    { id: 1, status: "ACTIVE", userName: "Qualified" },
    { id: 2, status: "INACTIVE", userName: "Inactive" },
    { id: 3, status: "ACTIVE", userName: "Other sport" },
  ];
  const assignments = [{ coachId: 1, sportId: 5 }, { coachId: 2, sportId: 5 }, { coachId: 3, sportId: 8 }];
  assert.deepEqual(qualifiedClassCoaches(coaches, assignments, 5).map((coach) => coach.id), [1]);
  assert.deepEqual(qualifiedClassCoaches(coaches, assignments, 999), []);
});

test("Class forms preserve a legacy assigned Coach and report unverified qualification without changing backend validation", () => {
  assert.match(apiSource, /export function getCoachSportsApi[\s\S]*?\/api\/coaches\/[\s\S]*?\/sports/);
  assert.match(managementPages, /Promise\.allSettled\(activeCoaches\.map\(\(coach\) => getCoachSportsApi/);
  assert.match(managementPages, /HLV hiện được phân công không còn ACTIVE hoặc chưa được xác minh đủ chuyên môn/);
  assert.match(managementPages, /classFields\[1\]\.options = \[\[courseClass\.coachId/);
  assert.match(managementPages, /HLV chưa xác minh đã được ẩn/);
  assert.match(managementPages, /onActionError: setError/);
});

test("new course payload requires and submits the explicit form Sport selection", () => {
  const form = { sportId: "2", name: "Khóa Cờ Vua U16", level: "BEGINNER", targetAgeMin: "8", targetAgeMax: "16" };
  assert.equal(buildCoursePayload(form).sportId, 2);
  assert.equal(buildCoursePayload(form).agePolicy, "RECOMMENDED");
  assert.throws(() => buildCoursePayload({ ...form, sportId: "" }), /chọn Sport/i);
});

test("course age policy payload defaults to RECOMMENDED and validates REQUIRED ranges", () => {
  const base = { sportId: "2", name: "Course", level: "BEGINNER" };
  assert.equal(buildCoursePayload(base).agePolicy, "RECOMMENDED");
  assert.throws(() => buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "8" }), /cả tuổi tối thiểu và tối đa/i);
  assert.throws(() => buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "12", targetAgeMax: "8" }), /không được lớn hơn/i);
  assert.throws(() => buildCoursePayload({ ...base, targetAgeMin: "-1" }), /số nguyên không âm/i);
  assert.deepEqual(
    { ...buildCoursePayload({ ...base, agePolicy: "REQUIRED", targetAgeMin: "5", targetAgeMax: "9" }) },
    { name: "Course", description: "", targetAgeMin: 5, targetAgeMax: 9, agePolicy: "REQUIRED", level: "BEGINNER", learningOutcome: "", sportId: 2 },
  );
});

test("course update payload preserves existing update contract without changing persisted Sport", () => {
  const payload = buildCoursePayload({ sportId: "2", name: "Updated", level: "ADVANCED", status: "INACTIVE" }, true);
  assert.equal("sportId" in payload, false);
  assert.equal(payload.status, "INACTIVE");
});

test("Admin Course Form exposes policy, age safety guidance, and disables invalid REQUIRED ranges", async () => {
  const form = await readFile(new URL("../src/pages/CourseForm.jsx", import.meta.url), "utf8");
  assert.match(form, /name="agePolicy" value=\{form\.agePolicy\}/);
  assert.match(form, /value="RECOMMENDED">Độ tuổi khuyến nghị/);
  assert.match(form, /value="REQUIRED">Độ tuổi bắt buộc/);
  assert.match(form, /form\.agePolicy === "REQUIRED" && \(ageMin == null \|\| ageMax == null\)/);
  assert.match(form, /trẻ dưới 10 tuổi/);
  assert.match(form, /disabled=\{busy \|\| ageRangeInvalid\}/);
});

test("Course list has only the management action while Course Management keeps editing", async () => {
  const list = await readFile(new URL("../src/pages/CourseList.jsx", import.meta.url), "utf8");
  const management = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(list, /onClick=\{\(\) => navigate\("\/staff\/courses\/" \+ course\.id\)\}>Quản lý/);
  assert.doesNotMatch(list, /\/edit.*>Sửa|>Sửa<\/button>/);
  assert.match(management, /className="cm-icon-button" aria-label=\{`Sửa \$\{title\}:/);
  assert.match(management, /navigate\("\/staff\/courses\/" \+ course\.id \+ "\/edit"\)/);
});

test("offering time status uses Vietnam business date and inclusive offering dates", () => {
  assert.equal(vietnamBusinessDate(new Date("2026-11-09T17:30:00.000Z")), "2026-11-10");
  const offering = { startDate: "2026-11-10", endDate: "2026-11-12" };
  assert.equal(offeringTimeStatus(offering, "2026-11-09"), "UPCOMING");
  assert.equal(offeringTimeStatus(offering, "2026-11-10"), "ONGOING");
  assert.equal(offeringTimeStatus(offering, "2026-11-12"), "ONGOING");
  assert.equal(offeringTimeStatus(offering, "2026-11-13"), "PAST");
  assert.equal(offeringTimeStatus({ startDate: "2026-11-13", endDate: "2026-11-12" }, "2026-11-12"), null);
});

test("offering filters keep backend status separate and default to newest start date", () => {
  const data = [
    { id: 1, name: "Spring", startDate: "2026-02-01", endDate: "2026-02-28", status: "OPEN" },
    { id: 2, name: "Autumn", startDate: "2026-10-01", endDate: "2026-10-30", status: "CLOSED" },
    { id: 3, name: "Summer", startDate: "2026-06-01", endDate: "2026-06-30", status: "OPEN" },
  ];
  assert.deepEqual(filterCourseOfferings(data).map((row) => row.id), [2, 3, 1]);
  assert.deepEqual(filterCourseOfferings(data, { status: "OPEN", businessDate: "2026-06-10" }).map((row) => row.id), [3, 1]);
  assert.deepEqual(filterCourseOfferings(data, { timeStatus: "PAST", status: "OPEN", businessDate: "2026-11-10" }).map((row) => row.id), [3, 1]);
  assert.deepEqual(filterCourseOfferings(data, { sort: "OLDEST" }).map((row) => row.id), [1, 3, 2]);
  assert.deepEqual(filterCourseOfferings(data, { search: "aut" }).map((row) => row.id), [2]);
});

test("Admin labels OPEN/CLOSED as Offering status and explains registration eligibility is separate", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(pages, /Trạng thái đợt học/);
  assert.match(pages, /Chỉ có thể đăng ký khi đợt học đang mở, còn trong thời gian nhận đăng ký và có lớp đủ điều kiện/);
  assert.match(pages, /14 ngày trước hạn đăng ký/);
  assert.doesNotMatch(pages, /máy chủ là nơi thực thi quy tắc/);
  assert.doesNotMatch(pages, /Trạng thái đăng ký/);
});

test("class and session filters remain bounded by status and chronological order", () => {
  assert.deepEqual(filterCourseClasses([
    { id: 1, name: "B", status: "ACTIVE" }, { id: 2, name: "A", status: "INACTIVE" },
  ], { status: "ACTIVE" }).map((row) => row.id), [1]);
  assert.deepEqual(filterClassSessions([
    { id: 1, startTime: "2026-11-10T12:00:00", status: "SCHEDULED" },
    { id: 2, startTime: "2026-11-10T09:00:00", status: "COMPLETED" },
  ]).map((row) => row.id), [2, 1]);
  assert.deepEqual(filterClassSessions([
    { id: 1, startTime: "2026-11-10T12:00:00", status: "SCHEDULED" },
    { id: 2, startTime: "2026-11-10T09:00:00", status: "COMPLETED" },
  ], { status: "COMPLETED" }).map((row) => row.id), [2]);
});

test("class session list filters by status and inclusive Vietnam-local date range, then sorts both ways", () => {
  const sessions = [
    { id: 3, status: "SCHEDULED", startTime: "2027-01-03T09:00:00" },
    { id: 1, status: "CANCELLED", startTime: "2027-01-01T10:00:00" },
    { id: 2, status: "COMPLETED", startTime: "2027-01-02T08:00:00" },
  ];
  assert.deepEqual(filterClassSessions(sessions).map((session) => session.id), [1, 2, 3]);
  assert.deepEqual(filterClassSessions(sessions, { status: "CANCELLED" }).map((session) => session.id), [1]);
  assert.deepEqual(filterClassSessions(sessions, { startDate: "2027-01-02", endDate: "2027-01-03" }).map((session) => session.id), [2, 3]);
  assert.deepEqual(filterClassSessions(sessions, { startDate: "2027-01-02", endDate: "2027-01-03", sort: "DESC" }).map((session) => session.id), [3, 2]);
  assert.deepEqual(paginateItems(filterClassSessions(sessions, { startDate: "2027-01-02" }), 0, 1).items.map((session) => session.id), [2]);
});

test("class session timestamps display as Vietnamese date and 24-hour time without UTC day shifts", () => {
  const timestamp = "2026-01-01T18:00:00Z";
  assert.equal(classSessionVietnamDate(timestamp), "2026-01-02");
  assert.equal(formatClassSessionDate(timestamp), "02/01/2026");
  assert.equal(formatClassSessionTime(timestamp), "01:00");
  assert.equal(formatClassSessionDate("not-a-date"), "—");
  assert.equal(formatClassSessionTime("not-a-date"), "—");
});

test("manual Class Session form requires a facility and retains schedule validation", () => {
  const offering = { startDate: "2027-01-01", endDate: "2027-01-31", sessionDuration: 60 };
  const schedule = { startTime: "2027-01-04T10:00", endTime: "2027-01-04T11:00" };
  assert.match(validateClassSessionSchedule(schedule, offering), /chọn cơ sở/i);
  assert.match(validateClassSessionSchedule({ ...schedule, facilityId: null }, offering), /chọn cơ sở/i);
  assert.equal(validateClassSessionSchedule({ ...schedule, facilityId: "12" }, offering), null);
});

test("Admin Class Session list keeps filters, Vietnamese statuses, format, and pagination after filtering", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.match(pages, /<option value="">Tất cả<\/option><option value="SCHEDULED">Đã lên lịch<\/option><option value="COMPLETED">Đã hoàn thành<\/option><option value="CANCELLED">Đã hủy<\/option>/);
  assert.match(pages, /type="date" value=\{sessionStartDateFilter\}/);
  assert.match(pages, /type="date" value=\{sessionEndDateFilter\}/);
  assert.match(pages, /value="ASC">Thời gian tăng dần/);
  assert.match(pages, /value="DESC">Thời gian giảm dần/);
  assert.match(pages, /filterClassSessions\(sessions, \{[\s\S]*startDate: sessionStartDateFilter[\s\S]*sort: sessionSort/);
  assert.match(pages, /pageResetKey=\{sessionFilterRevision\}/);
  assert.match(pages, /const page = pagination\.key === pageResetKey \? pagination\.page : 0/);
  assert.match(pages, /setSessionFilterRevision\(\(value\) => value \+ 1\)/);
  assert.match(pages, /formatClassSessionDate\(session\.startTime\)/);
  assert.match(pages, /formatClassSessionTime\(session\.startTime\)/);
  assert.match(pages, /sessionStatusPill\(session\.status\)/);
  assert.match(pages, /hint=\{allSessions\.length \? "Không có buổi học nào khớp bộ lọc\." : "Chưa có buổi học\."\}/);
  assert.match(pages, /label: "Huấn luyện viên"[\s\S]*key: "facilityName", label: "Cơ sở vật chất"/);
  assert.match(pages, /key: "facilityId", label: "Cơ sở vật chất", type: "select", num: true, required: true, options: suitableFacilities\.filter\(\(facility\) => String\(facility\.status \|\| ""\)\.toUpperCase\(\) === "ACTIVE"\)/);
  assert.match(pages, /availability\.canComplete[\s\S]*availability\.canCancel/);
  assert.match(css, /\.course-management-pages \.course-session-list \.queue-table \{ min-width: 900px; \}/);
  assert.match(css, /\.course-management-pages \.queue-table-wrap \{ overflow-x: auto;/);
  assert.match(css, /@media \(max-width: 1100px\)[\s\S]*course-session-filters \{ grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/);
});

test("pagination retains access to all historical offerings without an unbounded table", () => {
  const data = Array.from({ length: 27 }, (_, index) => ({ id: index + 1 }));
  assert.equal(paginateItems(data, 0, 10).items.length, 10);
  assert.equal(paginateItems(data, 0, 10).pageCount, 3);
  assert.deepEqual(paginateItems(data, 2, 10).items.map((row) => row.id), [21, 22, 23, 24, 25, 26, 27]);
  assert.equal(paginateItems(data, 20, 10).page, 2);
});

test("course hierarchy routes and detail pages preserve deep navigation and management actions", async () => {
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(app, /path="\/staff\/courses\/:courseId" element=\{<CourseManagementPages/);
  assert.match(app, /path="\/staff\/courses\/:courseId\/offerings\/:offeringId"/);
  assert.match(app, /path="\/staff\/courses\/:courseId\/offerings\/:offeringId\/classes\/:classId"/);
  assert.match(app, /AdminCourseRouteRedirect depth=\{3\}/);
  assert.match(pages, /getOfferingApi\(token, offeringId\)/);
  assert.match(pages, /getClassApi\(token, classId\)/);
  assert.match(pages, /Number\(loadedOffering\.courseId\) !== Number\(courseId\)/);
  assert.match(pages, /Number\(loadedClass\.courseOfferingId\) !== Number\(offeringId\)/);
  assert.match(pages, /if \(!\/\^\\d\+\$\//);
  assert.match(pages, /if \(active\) setClasses\(rows\(loadedClasses\)\)/);
  assert.match(pages, /if \(active\) setSessions\(rows\(loadedSessions\)\)/);
  assert.match(pages, /role="alert"/);
  assert.match(pages, /navigate\("\/staff\/courses\/" \+ course\.id \+ "\/edit"\)/);
  assert.match(pages, /onView=\{\(item\) => navigate\(offeringPathFor\(item\.id\)\)\}/);
  assert.match(pages, /onView=\{\(item\) => navigate\(classPathFor\(item\.id\)\)\}/);
  assert.match(pages, /const parentPath = pageKind === "class" \? offeringPath : pageKind === "offering" \? coursePath : basePath/);
  assert.match(pages, /const parentLabel = pageKind === "class" \? "Quay lại đợt học" : pageKind === "offering" \? "Quay lại khóa học" : "Danh sách khóa học"/);
  assert.match(pages, /onClick=\{\(\) => navigate\(parentPath\)\}><CourseIcon name="back" \/> \{parentLabel\}/);
  assert.doesNotMatch(pages, /navigate\(-1\)/);
  assert.match(pages, /SCHEDULED/);
  assert.match(pages, /COMPLETED/);
  assert.match(pages, /CANCELLED/);
  assert.match(pages, /ClassSessionGenerationPanel/);
});

test("Course and Offering detail polish keeps accessible editing and distinct status badges", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.doesNotMatch(pages, /QUẢN TRỊ ĐÀO TẠO · KHÓA HỌC/);
  assert.doesNotMatch(pages, /CHI TIẾT ĐỢT HỌC/);
  assert.match(pages, /course-info-card[\s\S]*aria-label="Sửa thông tin khóa học"[\s\S]*<CourseIcon name="edit" \/>[\s\S]*<span>Sửa<\/span>/);
  assert.equal((pages.match(/aria-label="Sửa thông tin khóa học"/g) || []).length, 1);
  assert.match(pages, /panelClassName="course-offering-info" editLabel="" editAccessibleName="Sửa thông tin" showPencil/);
  assert.match(pages, /className=\{showPencil \? "cm-icon-button" : undefined\} aria-label=\{editAccessibleName \|\| editLabel\} title=\{editAccessibleName \|\| editLabel\} onClick=\{open\} disabled=\{!entity\}>\{showPencil && <CourseIcon name="edit" \/>\}\{editLabel && <span>\{editLabel\}<\/span>\}/);
  assert.match(pages, /<CourseIcon name="edit" \/>\}\{editLabel && <span>\{editLabel\}<\/span>\}<\/button>/);
  assert.match(pages, /course-title-group"><h1>\{offering\.name\}<\/h1><span className=\{`course-time-status time-/);
  assert.match(pages, /statusPill\(item\.status\)/);
  assert.match(pages, /TIME_STATUS_LABEL\[timeStatus\]/);
  assert.match(css, /\.course-management-pages \.time-upcoming[^\n]*background: #fff5d9;[^\n]*color: #684500/);
  assert.match(css, /\.course-management-pages \.time-ongoing[^\n]*background: #e8f6ed;[^\n]*color: #145c30/);
  assert.match(css, /\.course-management-pages \.time-past[^\n]*background: #f1f2f4;[^\n]*color: #4b5563/);
  assert.match(css, /\.course-management-pages \.status-pill\.status-open/);
  assert.match(css, /\.course-management-pages \.status-pill\.status-closed/);
  assert.match(css, /\.course-title-group \{[^}]*flex-wrap: wrap/);
  assert.match(css, /@media \(max-width: 700px\)/);
});

test("Class detail puts its status beside the title and reuses accessible class status badges", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.doesNotMatch(pages, /CHI TIẾT LỚP/);
  assert.match(pages, /course-title-group"><h1>\{courseClass\.name\}<\/h1>\{courseClassStatusPill\(courseClass\.status\)\}/);
  assert.equal((pages.match(/courseClassStatusPill\(courseClass\.status\)/g) || []).length, 2);
  assert.match(pages, /render: \(item\) => courseClassStatusPill\(item\.status\)/);
  assert.match(pages, /panelClassName="course-class-info" editLabel="" editAccessibleName="Sửa thông tin" showPencil/);
  assert.doesNotMatch(pages, /panelClassName="course-class-info"[^\n]*editLabel="Sửa"/);
  assert.match(css, /\.course-management-pages \.course-class-status\.status-active[^\n]*background: #e8f6ed;[^\n]*color: #145c30/);
  assert.match(css, /\.course-management-pages \.course-class-status\.status-inactive/);
  assert.match(css, /\.course-title-group \{[^}]*flex-wrap: wrap/);
  assert.match(pages, /value=\{sessionStatusFilter\}/);
  assert.match(pages, /availability\.canComplete/);
  assert.match(pages, /availability\.canCancel/);
});

test("Course hierarchy row actions use named icon controls and retain text for consequential actions", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.match(pages, /className="cm-icon-button" aria-label=\{`Xem \$\{title\}:/);
  assert.match(pages, /className="cm-icon-button" aria-label=\{`Sửa \$\{title\}:/);
  assert.match(pages, /aria-label=\{`Làm mới \$\{title\}`\}/);
  assert.match(pages, /Hoàn thành/);
  assert.match(pages, /Hủy buổi/);
  assert.match(css, /\.course-management-pages \.cm-icon-button[\s\S]*width: 40px/);
  assert.match(css, /\.course-management-pages button:focus-visible[\s\S]*box-shadow: var\(--cm-focus\)/);
  assert.match(css, /prefers-reduced-motion: reduce/);
});

test("Course hierarchy action icons render visible inline SVG and preserve accessible action semantics", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.match(pages, /function CourseIcon\(\{ name \}\)[\s\S]*?<svg className="course-management-icon"[^>]*stroke="currentColor"[^>]*aria-hidden="true"/);
  for (const icon of ["back", "refresh", "add", "view", "edit", "previous", "next"]) {
    assert.match(pages, new RegExp(`<CourseIcon name="${icon}" \/>`));
  }
  assert.doesNotMatch(pages, /<i className="bi bi-/);
  assert.match(pages, /aria-label=\{`Xem \$\{title\}: \$\{item\.name/);
  assert.match(pages, /aria-label=\{`Sửa \$\{title\}: \$\{item\.name/);
  assert.match(pages, /aria-label=\{`Làm mới \$\{title\}`\}/);
  assert.match(pages, /disabled=\{disabled\} onClick=\{\(\) => open\(null\)\}/);
  assert.match(pages, /disabled=\{pageData\.page === 0\}/);
  assert.match(pages, /disabled=\{pageData\.page \+ 1 >= pageData\.pageCount\}/);
  assert.match(css, /\.course-management-pages \.course-management-icon\s*\{[^}]*width: 16px;[^}]*height: 16px;[^}]*color: currentColor/);
});

test("Course hierarchy pagination stays aligned at desktop widths with a narrow-screen fallback", async () => {
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(css, /\.course-management-pages \.course-management-pagination\s*\{[^}]*display: grid;[^}]*grid-template-columns: minmax\(0, 1fr\) auto/);
  assert.match(css, /\.course-management-pages \.course-management-pagination > span\s*\{[^}]*white-space: nowrap/);
  assert.match(css, /\.course-management-pages \.course-management-pagination > div\s*\{[^}]*flex-wrap: nowrap/);
  assert.match(css, /\.course-management-pages \.course-management-pagination button\s*\{[^}]*min-height: 40px;[^}]*white-space: nowrap/);
  assert.match(css, /@media \(max-width: 520px\)[\s\S]*?\.course-management-pages \.course-management-pagination\s*\{[^}]*grid-template-columns: 1fr/);
  assert.match(pages, /disabled=\{pageData\.page === 0\}/);
  assert.match(pages, /disabled=\{pageData\.page \+ 1 >= pageData\.pageCount\}/);
});

test("Course hierarchy tables align actions and show a labeled outline refresh control", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const courseList = await readFile(new URL("../src/pages/CourseList.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.match(pages, /className="cm-refresh-button"[^>]*aria-label=\{`Làm mới \$\{title\}`}[^>]*onClick=\{onRefresh\}><CourseIcon name="refresh" \/><span>Làm mới<\/span>/);
  assert.match(pages, /className="adm-primary" disabled=\{disabled\} onClick=\{\(\) => open\(null\)\}/);
  assert.match(pages, /<th aria-label="Thao tác"><\/th>/);
  assert.match(courseList, /<th aria-label="Thao tác" \/>/);
  assert.doesNotMatch(pages, /<th>Thao tác<\/th>/);
  assert.match(css, /\.course-management-pages \.cm-refresh-button\s*\{[^}]*border: 1px solid var\(--cm-border\)[^}]*background: #fff/);
  assert.match(css, /\.course-management-pages \.queue-table td:last-child\s*\{[^}]*text-align: right;[^}]*white-space: nowrap/);
  assert.match(css, /\.course-management-pages \.queue-table td:last-child \.adm-actions\s*\{[^}]*justify-content: flex-end;[^}]*flex-wrap: nowrap/);
  assert.match(css, /\.course-list-main \.queue-table td:last-child \.adm-actions\s*\{[^}]*justify-content: flex-end;[^}]*flex-wrap: nowrap/);
  assert.match(css, /\.course-management-pages \.queue-table-wrap\s*\{[^}]*overflow-x: auto/);
});

test("Course hierarchy toolbar keeps Refresh and Add buttons on one aligned row", async () => {
  const pages = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  assert.match(pages, /className="adm-head cm-list-toolbar"[\s\S]*className="adm-actions cm-toolbar-actions"/);
  assert.match(pages, /className="cm-refresh-button"[^>]*onClick=\{onRefresh\}/);
  assert.match(pages, /className="adm-primary" disabled=\{disabled\} onClick=\{\(\) => open\(null\)\}/);
  assert.match(css, /\.course-management-pages \.cm-list-toolbar\s*\{[^}]*flex-wrap: wrap/);
  assert.match(css, /\.course-management-pages \.cm-toolbar-actions\s*\{[^}]*flex: 0 0 auto;[^}]*flex-wrap: nowrap;[^}]*justify-content: flex-end;[^}]*gap: 8px/);
  assert.match(css, /\.course-management-pages \.cm-toolbar-actions > button\s*\{[^}]*display: inline-flex;[^}]*flex-direction: row;[^}]*align-items: center;[^}]*justify-content: center;[^}]*flex-wrap: nowrap;[^}]*gap: 8px;[^}]*height: 40px;[^}]*white-space: nowrap/);
  assert.match(css, /\.course-management-pages \.cm-toolbar-actions \.course-management-icon\s*\{[^}]*width: 16px;[^}]*height: 16px;[^}]*flex: 0 0 16px/);
});

test("Auto Schedule requires an explicit facility before Preview or Generate", async () => {
  const panel = await readFile(new URL("../src/pages/ClassSessionGenerationPanel.jsx", import.meta.url), "utf8");
  const management = await readFile(new URL("../src/pages/CourseManagementPages.jsx", import.meta.url), "utf8");
  assert.match(panel, /if \(!facilityId\) throw new Error\("Vui lòng chọn cơ sở trước khi xem trước hoặc tạo lịch\."\)/);
  assert.match(panel, /<label>Cơ sở<select value=\{facilityId\} aria-required="true"/);
  assert.match(panel, /<option value="">Chọn cơ sở<\/option>/);
  assert.doesNotMatch(panel, /Chưa chọn cơ sở|Cơ sở \(không bắt buộc\)/);
  assert.match(panel, /facilityId: Number\(facilityId\)/);
  assert.match(panel, /request = payload\(\)/);
  assert.match(panel, /previewClassSessionGenerationApi\(token, request\)/);
  assert.match(panel, /confirmClassSessionGenerationApi\(token, request\)/);
  assert.match(panel, /setFacilityId\(event\.target\.value\)/);
  assert.match(panel, /const activeFacilities = rows\(facilities\)\.filter\(\(facility\) => String\(facility\?\.status\)\.toUpperCase\(\) === "ACTIVE"\)/);
  assert.match(management, /const suitableFacilities = facilities\.filter\(\(facility\) => \(facility\.sports \|\| \(facility\.sportId \? \[\{ id: facility\.sportId \}\] : \[\]\)\)[\s\S]*?Number\(sport\.id\) === Number\(course\?\.sportId\)\)/);
});

test("Public design tokens and broad typography/focus rules are scoped to the public shell", async () => {
  const css = await readFile(new URL("../src/public-site/styles.css", import.meta.url), "utf8");
  assert.match(css, /^\.public-site \{[\s\S]*--blue:/);
  assert.doesNotMatch(css, /^:root\s*\{/m);
  assert.match(css, /\.public-site h1, \.public-site h2/);
  assert.match(css, /\.public-site :focus-visible/);
  assert.match(css, /\.public-site \.btn \{/);
});

test("Course list and form retain their established styles outside the redesigned detail route", async () => {
  const css = await readFile(new URL("../src/pages/CourseManagement.css", import.meta.url), "utf8");
  const list = await readFile(new URL("../src/pages/CourseList.jsx", import.meta.url), "utf8");
  const form = await readFile(new URL("../src/pages/CourseForm.jsx", import.meta.url), "utf8");
  assert.match(list, /className="course-list-heading"/);
  assert.match(css, /\.main \.course-list-heading\s*\{/);
  assert.match(css, /\.main \.course-filters\s*\{/);
  assert.match(form, /className="adm-form course-form adm-block"/);
  assert.match(css, /\.main \.course-form input/);
  assert.match(css, /\.main > \.adm-back/);
});
