// Shared by the member membership page, the payment result page and the front-desk queue.
export const isMembershipPayment = (purpose) => purpose === "MEMBERSHIP" || purpose === "UPGRADE";

export const UPGRADE_BLOCKED_MESSAGES = {
  NO_ACTIVE_MEMBERSHIP: "Bạn chưa có gói đang hoạt động nên chưa thể nâng cấp. Hãy mua gói trước.",
  MULTIPLE_ACTIVE_MEMBERSHIPS: "Tài khoản có nhiều gói cùng hiệu lực. Vui lòng liên hệ lễ tân để kiểm tra trước khi nâng cấp.",
  QUEUED_MEMBERSHIP: "Bạn đã mua gói nối tiếp sau gói hiện tại nên không thể nâng cấp giữa kỳ.",
  PENDING_PAYMENT: "Bạn còn yêu cầu thanh toán gói đang chờ. Hãy thanh toán hoặc hủy yêu cầu đó trước.",
  TOO_FEW_DAYS_LEFT: "Gói hiện tại còn dưới 3 ngày. Hãy mua gói mới khi gói này hết hạn thay vì nâng cấp.",
  UNKNOWN_PAID_VALUE: "Membership hiện tại chưa có thông tin giá trị đã thanh toán được xác minh. Vui lòng liên hệ quầy để kiểm tra.",
  NO_CONFIGURED_PATHS: "Hiện chưa có lộ trình nâng cấp nào được Admin cấu hình.",
  NO_VALID_UPGRADE_OPTIONS: "Không có lộ trình nâng cấp phù hợp với Membership hiện tại.",
  INVALID_SOURCE_PLAN: "Thông tin gói Membership nguồn không còn hợp lệ để tạo yêu cầu nâng cấp.",
  INACTIVE_SOURCE_PLAN: "Gói Membership hiện tại đã ngừng áp dụng nên không thể tạo yêu cầu nâng cấp mới.",
  FUTURE_MEMBERSHIP: "Bạn đã có Membership bắt đầu trong tương lai nên chưa thể nâng cấp.",
};

export function upgradeBlockedMessage(code) {
  return code ? UPGRADE_BLOCKED_MESSAGES[code] || "Hiện chưa thể nâng cấp gói." : "";
}

export function upgradeErrorMessage(error) {
  const message = error?.message || "";
  const code = Object.keys(UPGRADE_BLOCKED_MESSAGES).find((key) => message.includes(key));
  if (code) return UPGRADE_BLOCKED_MESSAGES[code];
  if (/No active Admin-configured Upgrade Path/i.test(message)) return UPGRADE_BLOCKED_MESSAGES.NO_CONFIGURED_PATHS;
  if (/not available for a new Upgrade quote|zero payable/i.test(message)) return UPGRADE_BLOCKED_MESSAGES.NO_VALID_UPGRADE_OPTIONS;
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn chỉ có thể nâng cấp gói của chính mình.";
  if (error?.status === 503) return "Thanh toán trực tuyến hiện chưa được bật. Vui lòng chọn thanh toán tại quầy.";
  if (/midnight/i.test(message)) return "Không thể nâng cấp qua VNPay trong vài phút trước 0h. Vui lòng thử lại sau 0h hoặc thanh toán tại quầy.";
  if (/expired/i.test(message)) return "Báo giá nâng cấp đã hết hạn. Hãy hủy yêu cầu này và tạo yêu cầu nâng cấp mới.";
  return message || "Không thể tạo yêu cầu nâng cấp.";
}
