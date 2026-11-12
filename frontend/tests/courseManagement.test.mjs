import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  buildCoursePayload, filterCourses, filterClassSessions, filterCourseClasses,
  filterCourseOfferings, offeringTimeStatus, paginateItems, qualifiedClassCoaches, vietnamBusinessDate,
} from "../src/courseManagementModel.js";
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
  assert.match(management, /onClick=\{\(\) => open\(item\)\}>Sửa/);
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
  assert.match(pages, /OPEN chỉ cho phép đăng ký khi đang trong thời gian đăng ký và có lớp đủ điều kiện/);
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
  assert.match(pages, /course-info-card[\s\S]*aria-label="Sửa thông tin khóa học"[\s\S]*bi bi-pencil[\s\S]*<span>Sửa<\/span>/);
  assert.equal((pages.match(/aria-label="Sửa thông tin khóa học"/g) || []).length, 1);
  assert.match(pages, /course-title-group"><h1>\{offering\.name\}<\/h1><span className=\{`course-time-status time-/);
  assert.match(pages, /statusPill\(item\.status\)/);
  assert.match(pages, /TIME_STATUS_LABEL\[timeStatus\]/);
  assert.match(css, /\.time-upcoming[^\n]*background: #fff3cf;[^\n]*color: #684500/);
  assert.match(css, /\.time-ongoing[^\n]*background: #e8f6ed;[^\n]*color: #145c30/);
  assert.match(css, /\.time-past[^\n]*background: #f1f2f4;[^\n]*color: #4b5563/);
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
  assert.match(pages, /editAccessibleName="Sửa thông tin lớp" showPencil/);
  assert.match(css, /\.course-management-pages \.course-class-status\.status-active[^\n]*background: #e8f6ed;[^\n]*color: #145c30/);
  assert.match(css, /\.course-management-pages \.course-class-status\.status-inactive/);
  assert.match(css, /\.course-title-group \{[^}]*flex-wrap: wrap/);
  assert.match(pages, /value=\{sessionStatusFilter\}/);
  assert.match(pages, /availability\.canComplete/);
  assert.match(pages, /availability\.canCancel/);
});
