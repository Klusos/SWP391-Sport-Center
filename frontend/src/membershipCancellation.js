// Mirrors MembershipCancellationRequestService.NOTICE_HOURS: requests close 72 hours before 00:00 (Vietnam) of the start date.
export const CANCELLATION_NOTICE_HOURS = 72;

const NOTICE_DAYS = CANCELLATION_NOTICE_HOURS / 24;

function minusDays(isoDate, days) {
  const date = new Date(`${String(isoDate).slice(0, 10)}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - days);
  return date.toISOString().slice(0, 10);
}

// Last day on which a request can still be sent ("YYYY-MM-DD"); requests close at 00:00 of the following day.
export function lastRequestDay(startDate) {
  return minusDays(startDate, NOTICE_DAYS + 1);
}

export function canRequestCancellation(membership, today) {
  return String(membership?.startDate || "") > today && today <= lastRequestDay(membership.startDate);
}

export const formatDate = (value) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(value || ""));
  return match ? `${match[3]}/${match[2]}/${match[1]}` : "—";
};

export const formatDateTime = (value) => {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(String(value || ""));
  return match ? `${match[4]}:${match[5]} ${match[3]}/${match[2]}/${match[1]}` : "—";
};

export const requestStatusLabel = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Bị từ chối",
  WITHDRAWN: "Đã rút",
  EXPIRED: "Hết hiệu lực",
};

export const paymentStatusLabel = {
  PENDING: "Chờ thanh toán",
  SUCCESS: "Đã thanh toán",
  CANCELLED: "Đã hủy",
  REFUND_REQUIRED: "Chờ hoàn tiền",
  REFUNDED: "Đã hoàn tiền",
};

export const paymentMethodLabel = { COUNTER: "Tại quầy", ONLINE: "VNPay" };

export const paymentPurposeLabel = {
  MEMBERSHIP: "Gói hội viên",
  UPGRADE: "Nâng cấp gói",
  COURSE_REGISTRATION: "Đăng ký khóa học",
  PT_BOOKING: "Buổi PT",
};

export function cancellationRequestErrorMessage(error) {
  const message = error?.message || "";
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Bạn không có quyền thực hiện thao tác này với yêu cầu hủy gói.";
  if (error?.status === 400) return "Cần nhập lý do khi từ chối yêu cầu hủy gói.";
  if (/72 hours/.test(message)) return `Đã quá hạn gửi yêu cầu hủy (chậm nhất ${CANCELLATION_NOTICE_HOURS} giờ trước ngày gói bắt đầu). Vui lòng liên hệ lễ tân.`;
  if (/already has a pending/.test(message)) return "Gói này đã có yêu cầu hủy đang chờ duyệt.";
  if (/starts after this one/.test(message)) return "Hãy hủy gói nối tiếp phía sau trước.";
  if (/running membership/.test(message)) return "Gói đang chạy không thể hủy; bạn có thể tắt tự gia hạn.";
  if (/already started/.test(message)) return "Gói đã bắt đầu nên không thể duyệt yêu cầu hủy nữa.";
  if (/already processed/.test(message)) return "Yêu cầu đã được xử lý trước đó. Hãy làm mới danh sách.";
  return message || "Không thể xử lý yêu cầu hủy gói.";
}
