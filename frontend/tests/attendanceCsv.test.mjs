import assert from "node:assert/strict";
import test from "node:test";
import { buildAttendanceCsv } from "../src/attendanceCsv.js";

test("attendance CSV preserves Vietnamese text and quotes commas and quotes", () => {
  assert.equal(buildAttendanceCsv([["Họ tên", "Ghi chú"], ["Nguyễn An", 'Có mặt, "đúng giờ"']]),
    '\uFEFF"Họ tên","Ghi chú"\r\n"Nguyễn An","Có mặt, ""đúng giờ"""');
});

test("attendance CSV neutralizes spreadsheet formula values after leading whitespace", () => {
  const csv = buildAttendanceCsv([["=1+1", " +SUM(A1:A2)", "-cmd", "@value"]]);
  assert.equal(csv, '\uFEFF"\'=1+1","\' +SUM(A1:A2)","\'-cmd","\'@value"');
});
