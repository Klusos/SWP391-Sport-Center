import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import {
  buildRegistrationScheduleSummary,
  formatRegistrationSessionDate,
  formatRegistrationSessionTime,
  formatScheduleMissingSlot,
} from "../src/registrationSchedulePresentation.js";

const session = (id, startTime, endTime) => ({ id, startTime, endTime });
const date = (day, time = "13:00") => `2026-12-${String(day).padStart(2, "0")}T${time}:00`;
const oneHour = (day, time = "13:00", id = day) => {
  const [hour, minute] = time.split(":").map(Number);
  const end = `${String(hour + 1).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
  return session(id, date(day, time), date(day, end));
};
const tuesdayFriday = [
  oneHour(8, "13:00", "w1-tu"), oneHour(11, "13:00", "w1-fr"),
  oneHour(15, "13:00", "w2-tu"), oneHour(18, "13:00", "w2-fr"),
  oneHour(22, "13:00", "w3-tu"), oneHour(25, "13:00", "w3-fr"),
];

test("schedule timestamp formatting follows Vietnam local date and time without UTC day shifts", () => {
  assert.equal(formatRegistrationSessionDate("2026-11-17T08:05:00"), "17/11/2026");
  assert.equal(formatRegistrationSessionTime("2026-11-17T08:05:00"), "08:05");
  assert.equal(formatRegistrationSessionDate("2026-12-08T17:00:00Z"), "09/12/2026");
  assert.equal(formatRegistrationSessionTime("2026-12-08T17:00:00Z"), "00:00");
  assert.equal(formatRegistrationSessionDate(null), "—");
  assert.equal(formatRegistrationSessionTime("unavailable"), "—");
});

test("repeating Tuesday and Friday sessions produce a weekly pattern and accurate range/count", () => {
  const summary = buildRegistrationScheduleSummary(tuesdayFriday);
  assert.deepEqual(summary.pattern.weekdays.map((day) => day.label), ["Thứ Ba", "Thứ Sáu"]);
  assert.equal(summary.totalSessions, 6);
  assert.equal(summary.weekCount, 3);
  assert.equal(summary.startDate.day, 8);
  assert.equal(summary.endDate.day, 25);
  assert.equal(summary.weeks.some((week) => week.exceptional), false);
});

test("an additional weekday is shown as an exceptional week, without implying cancellation or replacement", () => {
  const summary = buildRegistrationScheduleSummary([
    ...tuesdayFriday,
    oneHour(17, "18:00", "extra-thu"),
  ]);
  const exception = summary.weeks.find((week) => week.exceptional);
  assert.equal(exception.number, 2);
  assert.ok(exception.actualSlots.some((slot) => slot.weekday === 3));
  assert.equal(exception.missingSlots.length, 0);
});

test("a missing regular weekday in an interior week is identified from published schedule only", () => {
  const summary = buildRegistrationScheduleSummary(tuesdayFriday.filter((item) => item.sessionId !== "w2-fr" && item.id !== "w2-fr"));
  const exception = summary.weeks.find((week) => week.exceptional);
  assert.equal(exception.number, 2);
  assert.match(formatScheduleMissingSlot(exception.missingSlots[0]), /Thứ Sáu 13:00–14:00/);
  assert.match(formatScheduleMissingSlot(exception.missingSlots[0]), /lịch đã công bố tuần này/);
});

test("a changed session time is reported as an exception with its actual slot", () => {
  const changed = tuesdayFriday.map((item) => item.id === "w2-fr" ? oneHour(18, "14:00", "w2-fr") : item);
  const exception = buildRegistrationScheduleSummary(changed).weeks.find((week) => week.exceptional);
  assert.equal(exception.number, 2);
  assert.ok(exception.actualSlots.some((slot) => slot.startMinutes === 14 * 60));
  assert.ok(exception.missingSlots.some((slot) => slot.startMinutes === 13 * 60));
});

test("partial first and last weeks are not marked exceptional for missing recurring sessions", () => {
  const sessions = [
    oneHour(11, "13:00", "w1-fr"),
    oneHour(15, "13:00", "w2-tu"), oneHour(18, "13:00", "w2-fr"),
    oneHour(22, "13:00", "w3-tu"), oneHour(25, "13:00", "w3-fr"),
    oneHour(29, "13:00", "w4-tu"),
  ];
  const summary = buildRegistrationScheduleSummary(sessions);
  assert.ok(summary.pattern);
  assert.equal(summary.weeks[0].exceptional, false);
  assert.equal(summary.weeks.at(-1).exceptional, false);
  assert.equal(summary.weeks.filter((week) => week.exceptional).length, 0);
});

test("a single recurring-looking occurrence is not enough to infer a weekly pattern", () => {
  const summary = buildRegistrationScheduleSummary([
    oneHour(8, "09:00", "one"),
    oneHour(15, "10:00", "two"),
    oneHour(22, "11:00", "three"),
  ]);
  assert.equal(summary.pattern, null);
  assert.equal(summary.weeks.length, 3);
  assert.equal(summary.weeks.some((week) => week.exceptional), false);
});

test("one or multiple sessions per week and repeated same-day sessions remain supported", () => {
  const sessions = [
    oneHour(8, "09:00", "a"), oneHour(8, "15:00", "b"),
    oneHour(15, "09:00", "c"), oneHour(15, "15:00", "d"),
  ];
  const summary = buildRegistrationScheduleSummary(sessions);
  assert.ok(summary.pattern);
  assert.deepEqual(summary.pattern.weekdays.map((day) => day.label), ["Thứ Ba"]);
  assert.equal(summary.totalSessions, 4);
  assert.equal(summary.weekCount, 2);
});

test("sessions are grouped Monday through Sunday and numbered chronologically", () => {
  const summary = buildRegistrationScheduleSummary([
    oneHour(15, "13:00", "later"), oneHour(8, "13:00", "earlier"),
  ]);
  assert.equal(summary.sessions[0].session.id, "earlier");
  assert.equal(summary.sessions[0].number, 1);
  assert.equal(summary.weeks[0].startDate.day, 7);
  assert.equal(summary.weeks[0].endDate.day, 13);
  assert.equal(summary.weeks[1].startDate.day, 14);
});

test("invalid or missing timestamps fall back safely and remain counted", () => {
  const summary = buildRegistrationScheduleSummary([
    oneHour(8), session("invalid", "not-a-date", null),
  ]);
  assert.equal(summary.pattern, null);
  assert.equal(summary.totalSessions, 2);
  assert.equal(summary.undatedSessions.length, 1);
  assert.equal(summary.undatedSessions[0].dateLabel, "—");
});

test("Member confirmation keeps schedule details expandable and preserves registration actions and warning", async () => {
  const source = await readFile(new URL("../src/public-site/pages/CourseDetail.jsx", import.meta.url), "utf8");
  const app = await readFile(new URL("../src/App.jsx", import.meta.url), "utf8");
  assert.match(app, /path="\/member\/courses\/:courseId"[\s\S]*?<CourseDetail[^>]*memberRoute/);
  assert.match(source, /<h3 id="member-registration-confirm-title">Xác nhận đăng ký<\/h3>/);
  assert.match(source, /<RegistrationSchedule key=\{selectedClass\.id\}[^>]*sessions=\{classSchedules\[selectedClass\.id\] \|\| \[\]\}/);
  assert.match(source, /aria-expanded=\{expanded\} aria-controls=\{detailsId\}/);
  assert.match(source, /Xem chi tiết \$\{summary\.totalSessions\} buổi/);
  assert.match(source, /summary\.weeks\.map\(\(week\) =>/);
  assert.match(source, /selectedClass\.coachName/);
  assert.match(source, /Học phí tham khảo: \{formatPrice\(selectedOffering\.price\)\}/);
  assert.match(source, /PENDING_PAYMENT[\s\S]*?không xác nhận thanh toán/);
  assert.match(source, /onClick=\{\(\) => void confirmRegistration\(\)\}/);
  assert.doesNotMatch(source, /classSchedules\[selectedClass\.id\]\.sort/);
});

test("weekly summary and exceptional week styles remain Member Course Detail scoped", async () => {
  const css = await readFile(new URL("../src/pages/MemberCourseDetail.css", import.meta.url), "utf8");
  assert.match(css, /\.member-registration-weekly-summary/);
  assert.match(css, /\.member-registration-weekday-badges/);
  assert.match(css, /\.member-registration-exception-weeks/);
  assert.match(css, /\.member-registration-schedule-table-wrap \{ width: 100%; overflow-x: auto;/);
  assert.match(css, /\.member-registration-full-schedule\[hidden\] \{ display: none; \}/);
});
