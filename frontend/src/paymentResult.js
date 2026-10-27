const paymentPurposes = {
  PT_BOOKING: {
    label: "Thanh toán buổi tập cá nhân",
    action: "Xem lịch PT",
    path: "/member/pt-booking",
  },
  MEMBERSHIP: {
    label: "Thanh toán gói hội viên",
    action: "Xem gói hội viên",
    path: "/member/membership",
  },
  COURSE_REGISTRATION: {
    label: "Thanh toán khóa học",
    action: "Xem khóa học của tôi",
    path: "/member/courses",
  },
};

const paymentStatuses = {
  SUCCESS: {
    tone: "success",
    icon: "✓",
    title: "Thanh toán thành công",
    explanation: "Máy chủ xác nhận khoản thanh toán. Trạng thái dịch vụ bên dưới được hiển thị riêng theo dữ liệu mới nhất.",
  },
  PENDING: {
    tone: "pending",
    icon: "…",
    title: "Đang chờ xác nhận",
    explanation: "Giao dịch đang được xử lý. Trạng thái này chưa xác nhận buổi tập, đăng ký khóa học hoặc gói hội viên.",
  },
  CANCELLED: {
    tone: "cancelled",
    icon: "×",
    title: "Giao dịch đã hủy",
    explanation: "Máy chủ ghi nhận giao dịch đã hủy hoặc không hoàn tất. Không ghi nhận thanh toán thành công.",
  },
  FAILED: {
    tone: "cancelled",
    icon: "×",
    title: "Thanh toán thất bại",
    explanation: "Máy chủ ghi nhận thanh toán thất bại. Kiểm tra trạng thái dịch vụ trước khi thử lại.",
  },
  REFUND_REQUIRED: {
    tone: "warning",
    icon: "!",
    title: "Cần xử lý hoàn tiền",
    explanation: "Khoản thanh toán cần nhân viên đối soát hoặc xử lý hoàn tiền. Trạng thái này không xác nhận dịch vụ đã được kích hoạt.",
  },
};

export function paymentPurposePresentation(payment) {
  return paymentPurposes[payment?.purpose] ?? null;
}

export function paymentStatusPresentation(status) {
  return paymentStatuses[status] ?? {
    tone: "unknown",
    icon: "?",
    title: "Chưa xác định được trạng thái thanh toán",
    explanation: "Không thể xác nhận kết quả lúc này. Hãy làm mới trạng thái hoặc liên hệ lễ tân.",
  };
}

export function paymentServiceLabel(purpose) {
  switch (purpose) {
    case "PT_BOOKING": return "Trạng thái buổi tập";
    case "MEMBERSHIP": return "Gói hội viên trên tài khoản";
    case "COURSE_REGISTRATION": return "Trạng thái đăng ký";
    default: return "Trạng thái dịch vụ";
  }
}

export function serviceStatusLabel(status) {
  const labels = {
    SUCCESS: "Thành công",
    PENDING: "Đang chờ xác nhận",
    FAILED: "Thất bại",
    CONFIRMED: "Đã xác nhận",
    PENDING_PAYMENT: "Chờ thanh toán",
    ACTIVE: "Đang hoạt động",
    EXPIRED: "Đã hết hạn",
    CANCELLED: "Đã hủy",
    COMPLETED: "Đã hoàn thành",
    REFUND_REQUIRED: "Cần xử lý hoàn tiền",
  };
  return labels[status] ?? status ?? "Chưa có dữ liệu";
}
