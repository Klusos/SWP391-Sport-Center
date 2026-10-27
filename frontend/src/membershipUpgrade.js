// Shared by the member membership page, the payment result page and the front-desk queue.
export const isMembershipPayment = (purpose) => purpose === "MEMBERSHIP" || purpose === "UPGRADE";

export const UPGRADE_BLOCKED_MESSAGES = {
  NO_ACTIVE_MEMBERSHIP: "Bạn chưa có gói đang hoạt động nên chưa thể nâng cấp. Hãy mua gói trước.",
  MULTIPLE_ACTIVE_MEMBERSHIPS: "Tài khoản có nhiều gói cùng hiệu lực. Vui lòng liên hệ lễ tân để kiểm tra trước khi nâng cấp.",
  QUEUED_MEMBERSHIP: "Bạn đã mua gói nối tiếp sau gói hiện tại nên không thể nâng cấp giữa kỳ.",
  PENDING_PAYMENT: "Bạn còn yêu cầu thanh toán gói đang chờ. Hãy thanh toán hoặc hủy yêu cầu đó trước.",
  TOO_FEW_DAYS_LEFT: "Gói hiện tại còn dưới 3 ngày. Hãy mua gói mới khi gói này hết hạn thay vì nâng cấp.",
};

export function upgradeBlockedMessage(code) {
  return code ? UPGRADE_BLOCKED_MESSAGES[code] || "Hiện chưa thể nâng cấp gói." : "";
}

export function upgradeErrorMessage(error) {
  const message = error?.message || "";
  const code = Object.keys(UPGRADE_BLOCKED_MESSAGES).find((key) => message.includes(key));
  if (code) return UPGRADE_BLOCKED_MESSAGES[code];
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn chỉ có thể nâng cấp gói của chính mình.";
  if (error?.status === 503) return "Thanh toán trực tuyến hiện chưa được bật. Vui lòng chọn thanh toán tại quầy.";
  if (/midnight/i.test(message)) return "Không thể nâng cấp qua VNPay trong vài phút trước 0h. Vui lòng thử lại sau 0h hoặc thanh toán tại quầy.";
  if (/keep every benefit/i.test(message)) return "Gói này không phải là gói cao hơn gói hiện tại.";
  if (/expired/i.test(message)) return "Báo giá nâng cấp đã hết hạn. Hãy hủy yêu cầu này và tạo yêu cầu nâng cấp mới.";
  return message || "Không thể tạo yêu cầu nâng cấp.";
}
