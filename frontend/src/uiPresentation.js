const ROLE_LABELS = {
  ADMIN: "Quản trị viên",
  RECEPTIONIST: "Lễ tân",
  COACH: "Huấn luyện viên",
  MEMBER: "Hội viên",
  GUEST: "Khách",
};

const STATUS_LABELS = {
  payment: {
    SUCCESS: "Đã thanh toán",
    PENDING: "Đang chờ thanh toán",
    CANCELLED: "Đã hủy",
    FAILED: "Thất bại",
    REFUND_REQUIRED: "Chờ hoàn tiền",
    REFUNDED: "Đã hoàn tiền",
  },
  membership: {
    ACTIVE: "Đang có hiệu lực",
    UPCOMING: "Sắp có hiệu lực",
    EXPIRED: "Đã hết hạn",
    CANCELLED: "Đã hủy",
    UPGRADED: "Đã được nâng cấp",
  },
  account: {
    ACTIVE: "Đang hoạt động",
    INACTIVE: "Ngừng hoạt động",
    DEACTIVATED: "Đã khóa",
  },
  membershipPlan: { ACTIVE: "Đang mở bán", INACTIVE: "Ngừng mở bán" },
  sport: { ACTIVE: "Đang hoạt động", INACTIVE: "Ngừng hoạt động" },
  course: { ACTIVE: "Đang mở đăng ký", INACTIVE: "Ngừng đăng ký" },
  offering: {
    OPEN: "Đang mở đăng ký", CLOSED: "Đã đóng đăng ký", DRAFT: "Bản nháp",
    ACTIVE: "Đang diễn ra", COMPLETED: "Đã kết thúc", CANCELLED: "Đã hủy",
  },
  facility: { ACTIVE: "Đang hoạt động", INACTIVE: "Ngừng hoạt động" },
  courseClass: { ACTIVE: "Đang hoạt động", INACTIVE: "Ngừng hoạt động" },
  classSession: {
    SCHEDULED: "Đã lên lịch",
    COMPLETED: "Đã hoàn thành",
    CANCELLED: "Đã hủy",
  },
  registration: {
    PENDING_PAYMENT: "Chờ thanh toán",
    CONFIRMED: "Đã xác nhận đăng ký",
    EXPIRED: "Đã hết hạn",
    CANCELLED: "Đã hủy",
  },
  renewalIntent: {
    OPEN: "Đang chờ thanh toán",
    SETTLED: "Đã gia hạn thành công",
    EXPIRED: "Đã hết hạn",
    CLOSED: "Đã đóng",
    CANCELLED: "Đã hủy",
    CONFLICT: "Cần kiểm tra",
    COLLECTION_CONFLICT: "Cần đối soát",
    RECONCILIATION_REQUIRED: "Cần đối soát",
    REFUND_REQUIRED: "Chờ hoàn tiền",
    REFUNDED: "Đã hoàn tiền",
  },
  ptBooking: {
    PENDING_PAYMENT: "Chờ thanh toán",
    CONFIRMED: "Đã xác nhận lịch",
    COMPLETED: "Đã hoàn thành",
    NO_SHOW: "Vắng mặt (buổi tập đã sử dụng)",
    CANCELLED: "Đã hủy",
    EXPIRED: "Đã hết hạn",
  },
  consultation: {
    PENDING: "Chờ tiếp nhận",
    IN_PROGRESS: "Đang xử lý",
    COMPLETED: "Đã hoàn tất",
    CANCELLED: "Đã hủy",
  },
  assessment: {
    PENDING: "Đang chờ đánh giá",
    COMPLETED: "Đã hoàn tất",
    CANCELLED: "Đã hủy",
  },
  generic: {
    ACTIVE: "Đang hoạt động",
    INACTIVE: "Ngừng hoạt động",
    PENDING: "Đang chờ xử lý",
    PENDING_PAYMENT: "Chờ thanh toán",
    CONFIRMED: "Đã xác nhận",
    COMPLETED: "Đã hoàn tất",
    CANCELLED: "Đã hủy",
    EXPIRED: "Đã hết hạn",
    SUCCESS: "Thành công",
    FAILED: "Thất bại",
  },
};

export function roleLabel(role) {
  return ROLE_LABELS[String(role || "").toUpperCase()] || "Người dùng";
}

export function statusLabel(status, context = "generic") {
  const key = String(status || "").toUpperCase();
  if (!key) return "Chưa xác định";
  return STATUS_LABELS[context]?.[key] || STATUS_LABELS.generic[key] || "Chưa xác định";
}

export function formatDateVN(value) {
  if (value == null || value === "") return "—";
  const text = String(value);
  const dateOnly = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  if (dateOnly) return `${dateOnly[3]}/${dateOnly[2]}/${dateOnly[1]}`;
  const date = new Date(text);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit", month: "2-digit", year: "numeric", timeZone: "Asia/Ho_Chi_Minh",
  }).format(date);
}

export function formatDateTimeVN(value) {
  if (value == null || value === "") return "—";
  const text = String(value);
  const date = new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(text) ? text : `${text}+07:00`);
  if (Number.isNaN(date.getTime())) return "—";
  const parts = new Intl.DateTimeFormat("en-GB", {
    day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit",
    hourCycle: "h23", timeZone: "Asia/Ho_Chi_Minh",
  }).formatToParts(date);
  const get = (type) => parts.find((part) => part.type === type)?.value || "";
  return `${get("hour")}:${get("minute")} ${get("day")}/${get("month")}/${get("year")}`;
}

export function formatVnd(value) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return "—";
  return new Intl.NumberFormat("vi-VN", { style: "currency", currency: "VND", maximumFractionDigits: 0 }).format(amount);
}
