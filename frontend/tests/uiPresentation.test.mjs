import test from "node:test";
import assert from "node:assert/strict";
import {
  formatDateTimeVN, formatDateVN, formatVnd, roleLabel, statusLabel,
} from "../src/uiPresentation.js";

test("domain role names have consistent Vietnamese labels and safe fallback", () => {
  assert.equal(roleLabel("ADMIN"), "Quản trị viên");
  assert.equal(roleLabel("RECEPTIONIST"), "Lễ tân");
  assert.equal(roleLabel("COACH"), "Huấn luyện viên");
  assert.equal(roleLabel("MEMBER"), "Hội viên");
  assert.equal(roleLabel("UNKNOWN"), "Người dùng");
});

test("status labels depend on business context and do not leak unknown enums", () => {
  assert.equal(statusLabel("ACTIVE", "membership"), "Đang có hiệu lực");
  assert.equal(statusLabel("ACTIVE", "account"), "Đang hoạt động");
  assert.equal(statusLabel("PENDING_PAYMENT", "registration"), "Chờ thanh toán");
  assert.equal(statusLabel("OPEN", "offering"), "Đang mở đăng ký");
  assert.equal(statusLabel("NO_SHOW", "ptBooking"), "Vắng mặt (buổi tập đã sử dụng)");
  assert.equal(statusLabel("FUTURE_ENUM", "payment"), "Chưa xác định");
});

test("dates preserve date-only values and format timestamps in Vietnam time", () => {
  assert.equal(formatDateVN("2026-11-26"), "26/11/2026");
  assert.equal(formatDateVN("not-a-date"), "—");
  assert.equal(formatDateTimeVN("2026-11-26T09:05:00"), "09:05 26/11/2026");
  assert.equal(formatDateTimeVN(null), "—");
});

test("VND uses the shared Vietnamese currency format", () => {
  assert.equal(formatVnd(1500000), "1.500.000 ₫");
  assert.equal(formatVnd("invalid"), "—");
});
