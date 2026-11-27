import test from "node:test";
import assert from "node:assert/strict";
import { apiErrorMessage } from "../src/apiErrorPresentation.js";
import { notificationPresentation } from "../src/notificationPresentation.js";
import { statusLabel } from "../src/uiPresentation.js";

test("backend errors preserve Vietnamese details and translate English HTTP errors", () => {
  assert.equal(apiErrorMessage("Tài khoản không hoạt động.", 403), "Tài khoản không hoạt động.");
  assert.equal(apiErrorMessage("Forbidden", 403), "Bạn không có quyền thực hiện thao tác này.");
  assert.equal(apiErrorMessage("Conflict", 409), "Dữ liệu đã thay đổi hoặc đang có xung đột. Hãy làm mới rồi thử lại.");
  assert.equal(apiErrorMessage("Unauthorized", 401, "/api/auth/login"), "Email hoặc mật khẩu chưa đúng.");
  assert.equal(apiErrorMessage("Internal Server Error", 500), "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
});

test("known system notifications are localized without changing unknown user-entered content", () => {
  assert.deepEqual(notificationPresentation({
    title: "PT booking confirmed", message: "Your personal training booking #42 has been confirmed.",
  }), { title: "Lịch huấn luyện cá nhân đã xác nhận", message: "Lịch huấn luyện cá nhân #42 đã được xác nhận." });
  assert.deepEqual(notificationPresentation({ title: "Lời nhắn", message: "Nội dung do người dùng nhập" }), {
    title: "Lời nhắn", message: "Nội dung do người dùng nhập",
  });
});

test("Renewal intent collection states use contextual Vietnamese labels", () => {
  assert.equal(statusLabel("OPEN", "renewalIntent"), "Đang chờ thanh toán");
  assert.equal(statusLabel("REFUND_REQUIRED", "renewalIntent"), "Chờ hoàn tiền");
  assert.equal(statusLabel("RECONCILIATION_REQUIRED", "renewalIntent"), "Cần đối soát");
  assert.equal(statusLabel("REFUNDED", "renewalIntent"), "Đã hoàn tiền");
});
