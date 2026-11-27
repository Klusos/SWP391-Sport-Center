import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import { attendanceDonutShares, attendancePercentage, buildMemberAttendanceClasses, buildMemberAttendanceOverview, filterMemberAttendanceClasses, memberAttendanceYears, paginateAttendance } from "../src/memberAttendance.js";

const courseClass = (classId, startDate, sportId = 1) => ({
  classId, className: `Lớp ${classId}`, courseId: 8, courseName: "Khóa học chung",
  sportId, sportName: sportId === 1 ? "Bóng rổ" : "Cầu lông", startDate,
  endDate: "2027-02-13", courseOfferingName: "Đợt xuân", coachName: "HLV thử nghiệm",
});

test("Member attendance groups by class identity, not shared course or sport", () => {
  const classes = buildMemberAttendanceClasses([
    { courseClass: courseClass(10, "2026-12-01", 1) },
    { courseClass: courseClass(11, "2027-01-15", 1) },
    { courseClass: courseClass(12, "2026-11-01", 2) },
  ]);
  assert.deepEqual(classes.map((item) => item.classId), [11, 10, 12]);
  assert.equal(classes.length, 3);
  assert.equal(classes[0].courseClass.courseName, classes[1].courseClass.courseName);
});

test("attendance percentage counts present over valid recorded statuses and excludes cancelled sessions", () => {
  const classes = buildMemberAttendanceClasses(
    [{ courseClass: courseClass(10, "2026-12-01") }],
    [
      { attendanceId: 1, classSessionId: 101, courseClassId: 10, status: "PRESENT", sessionStatus: "COMPLETED", validForStatistics: true },
      { attendanceId: 2, classSessionId: 102, courseClassId: 10, status: "ABSENT", sessionStatus: "COMPLETED", validForStatistics: true },
      { attendanceId: 3, classSessionId: 103, courseClassId: 10, status: "EXCUSED", sessionStatus: "COMPLETED", validForStatistics: true },
      { attendanceId: 4, classSessionId: 104, courseClassId: 10, status: "PRESENT", sessionStatus: "CANCELLED", validForStatistics: false },
    ],
    [
      { id: 105, classId: 10, status: "SCHEDULED", startTime: "2099-01-01T10:00:00" },
      { id: 106, classId: 10, status: "COMPLETED", startTime: "2026-01-01T10:00:00" },
      { id: 107, classId: 10, status: "CANCELLED", startTime: "2026-01-02T10:00:00" },
    ],
    new Date("2026-11-26T10:00:00+07:00").getTime(),
  );
  const summary = classes[0].summary;
  assert.deepEqual(summary, { present: 1, absent: 1, excused: 1, recorded: 3, future: 1, unrecorded: 1 });
  assert.equal(classes[0].attendancePercent, 33);
  assert.equal(classes[0].sessions.some((item) => item.classSessionId === 104), true, "cancelled history remains visible");
  assert.equal(attendancePercentage(0, 0), null);
  assert.deepEqual(attendanceDonutShares({ present: 1, absent: 1, excused: 0, recorded: 2 }), { present: 50, absent: 50, excused: 0 });
  assert.deepEqual(attendanceDonutShares({ present: 1, absent: 0, excused: 1, recorded: 2 }), { present: 50, absent: 0, excused: 50 });
  assert.deepEqual(attendanceDonutShares({ present: 2, absent: 1, excused: 1, recorded: 4 }), { present: 50, absent: 25, excused: 25 });
  assert.deepEqual(attendanceDonutShares({ present: 0, absent: 0, excused: 0, recorded: 0 }), { present: 0, absent: 0, excused: 0 });
});

test("Member attendance overview filters by Offering start year and sport with independent pagination", () => {
  const rows = buildMemberAttendanceClasses([
    { courseClass: courseClass(10, "2026-12-10", 1) },
    { courseClass: courseClass(11, "2027-01-10", 1) },
    { courseClass: courseClass(12, "2026-05-01", 2) },
  ]);
  assert.deepEqual(memberAttendanceYears(rows), ["2027", "2026"]);
  assert.deepEqual(filterMemberAttendanceClasses(rows, { year: "2026", sportId: "1" }).map((item) => item.classId), [10]);
  assert.deepEqual(filterMemberAttendanceClasses(rows).map((item) => item.classId), [11, 10, 12]);
  assert.deepEqual(paginateAttendance(rows, 2, 2), { items: [rows[2]], page: 2, totalPages: 2, total: 3 });
});

test("attendance overview keeps elapsed unrecorded sessions separate and does not invent a rate without records", () => {
  const [item] = buildMemberAttendanceOverview([{ courseClassId: 18, courseClassName: "A", courseId: 3,
    courseName: "Course", courseOfferingId: 5, courseOfferingName: "Spring", offeringStartDate: "2026-12-01",
    offeringEndDate: "2027-02-01", sportId: 2, sportName: "Tennis", coachName: "Coach",
    presentCount: 0, absentCount: 0, excusedCount: 0, recordedElapsedCount: 0,
    eligibleElapsedCount: 2, unrecordedElapsedCount: 2, futureSessionCount: 1,
    sessions: [{ classSessionId: 1, startTime: "2026-11-27T10:00:00", sessionStatus: "COMPLETED", validForStatistics: true }] }]);
  assert.equal(item.attendancePercent, null);
  assert.equal(item.summary.unrecorded, 2);
  assert.equal(item.summary.eligibleElapsed, 2);
  assert.equal(item.summary.future, 1);
  assert.equal(item.courseClass.coachName, "Coach");
});

test("Member attendance UI uses authenticated read APIs, class drill-in, and keeps attendance read-only", async () => {
  const page = await readFile(new URL("../src/pages/MemberAttendance.jsx", import.meta.url), "utf8");
  const service = await readFile(new URL("../../backend/src/main/java/group5/example/sportCenter/service/AttendanceService.java", import.meta.url), "utf8");
  assert.match(page, /getMyAttendanceOverviewApi\(token\)/);
  const css = await readFile(new URL("../src/pages/MemberAttendance.css", import.meta.url), "utf8");
  assert.match(page, /<strong>Lớp:<\/strong>/);
  assert.match(page, /<strong>Đợt học:<\/strong>/);
  assert.match(page, /<strong>Thời gian:<\/strong>/);
  assert.match(page, /<strong>Huấn luyện viên:<\/strong>/);
  assert.match(page, /status-\$\{status === "UNRECORDED" \? "unrecorded"/);
  assert.match(page, /attendance-count-present/);
  assert.match(page, /attendance-count-absent/);
  assert.match(page, /attendance-count-excused/);
  assert.match(css, /conic-gradient\(#15803d 0 var\(--present-share\), #dc3545 var\(--present-share\) calc\(var\(--present-share\) \+ var\(--absent-share\)\), #e5a500/);
  assert.match(page, /attendanceDonutShares\(summary\)/);
  assert.match(css, /member-attendance-page \.member-attendance-status\.status-absent \{ background:#f8d7da; color:#842029; \}/);
  assert.match(css, /member-attendance-page \.member-attendance-status\.status-excused \{ background:#fff0bf; color:#795900; \}/);
  assert.match(page, /buildMemberAttendanceOverview\(overview\)/);
  assert.match(page, /setSelectedClassId\(item\.classId\)/);
  assert.match(page, /Quay lại danh sách lớp/);
  assert.match(page, /paginateAttendance\(selectedClass\?\.sessions \|\| \[\], sessionPage, 10\)/);
  assert.match(page, /paginateAttendance\(filteredClasses, classPage, 6\)/);
  assert.match(page, /selectedClass\.summary\.future/);
  assert.match(page, /selectedClass\.summary\.unrecorded/);
  assert.doesNotMatch(page, /markAttendanceApi|PUT/);
  assert.match(service, /access\.requireMember\(actor\)/);
  assert.match(service, /findByMemberIdOrderByRegisteredAtDescIdDesc\(actor\.getId\(\)\)/);
});
