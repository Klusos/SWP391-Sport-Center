// Mirrors PersonalTrainingBookingService: 15-minute payment hold, members cancel confirmed sessions up to 24 hours before.
export const PT_HOLD_MINUTES = 15;
export const PT_CANCEL_NOTICE_HOURS = 24;

// Backend LocalDateTime values have no offset; they are Vietnam time.
export function vietnamInstant(value) {
  if (!value) return null;
  const text = String(value);
  const time = new Date(/[zZ]|[+-]\d\d:\d\d$/.test(text) ? text : text + "+07:00");
  return Number.isNaN(time.getTime()) ? null : time;
}

export function formatPtTime(value, withDate = true) {
  const time = vietnamInstant(value);
  if (!time) return "—";
  return new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    ...(withDate ? { hour: "2-digit", minute: "2-digit", day: "2-digit", month: "2-digit", year: "numeric" } : { hour: "2-digit", minute: "2-digit" }),
  }).format(time);
}

export const ptStatusLabel = {
  PENDING_PAYMENT: "Chờ thanh toán",
  CONFIRMED: "Đã xác nhận",
  CANCELLED: "Đã hủy",
  EXPIRED: "Hết hạn giữ chỗ",
};

export const ptStatusClass = { PENDING_PAYMENT: "pending", CONFIRMED: "completed", CANCELLED: "cancelled", EXPIRED: "cancelled" };

export function holdActive(booking, now = Date.now()) {
  const expires = vietnamInstant(booking?.paymentExpiresAt);
  return booking?.status === "PENDING_PAYMENT" && Boolean(expires) && expires.getTime() > now;
}

// Bookings that still hold a slot: confirmed ones, and pending ones whose payment hold is running.
export function ptBookingActive(booking, now = Date.now()) {
  return booking?.status === "CONFIRMED" || holdActive(booking, now);
}

// The calendar also keeps cancelled sessions (greyed out) but drops expired holds and lapsed pending rows.
export function ptBookingOnCalendar(booking, now = Date.now()) {
  return ptBookingActive(booking, now) || booking?.status === "CANCELLED";
}

// Upcoming sessions for dashboards, soonest first.
export function upcomingPtBookings(bookings, now = Date.now()) {
  return (bookings || [])
    .filter((booking) => ptBookingActive(booking, now) && (vietnamInstant(booking.startTime)?.getTime() ?? 0) > now)
    .sort((a, b) => vietnamInstant(a.startTime) - vietnamInstant(b.startTime));
}

export function minutesLeft(booking, now = Date.now()) {
  const expires = vietnamInstant(booking?.paymentExpiresAt);
  return expires ? Math.max(0, Math.ceil((expires.getTime() - now) / 60000)) : 0;
}

export function canMemberCancel(booking, now = Date.now()) {
  if (booking?.status === "PENDING_PAYMENT") return true;
  if (booking?.status !== "CONFIRMED") return false;
  const start = vietnamInstant(booking.startTime);
  return Boolean(start) && start.getTime() - PT_CANCEL_NOTICE_HOURS * 3600000 > now;
}

// The single PENDING payment attempt for a booking, if any (the backend allows only one at a time).
export function pendingPtPayment(payments, bookingId) {
  return (payments || []).find((payment) => payment?.purpose === "PT_BOOKING"
    && Number(payment.referenceId) === Number(bookingId) && payment.status === "PENDING") || null;
}

export function ptErrorMessage(error, fallback = "Không thể xử lý yêu cầu đặt PT.") {
  const message = error?.message || "";
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (/1-on-1 coaching eligibility/.test(message)) return "Gói hội viên của bạn vào ngày tập không có quyền đặt PT 1-1. Vui lòng nâng cấp gói hoặc liên hệ lễ tân.";
  if (/More than one eligible membership/.test(message)) return "Có nhiều gói hợp lệ cùng ngày; vui lòng liên hệ lễ tân để kiểm tra.";
  if (/must start in the future/.test(message)) return "Giờ bắt đầu phải ở tương lai.";
  if (/conflicts with a coach class or an existing PT booking/.test(message)) return "Khung giờ này trùng lịch của huấn luyện viên hoặc một buổi PT khác của bạn. Hãy chọn giờ khác.";
  if (/not qualified/.test(message)) return "Huấn luyện viên không dạy môn đã chọn.";
  if (/pricing/.test(message)) return "Giá buổi PT chưa được cấu hình; vui lòng liên hệ quản trị viên.";
  if (/payment hold has expired|hold expired/.test(message)) return "Đã hết thời gian giữ chỗ 15 phút. Vui lòng đặt lại.";
  if (/pending payment already exists/.test(message)) return "Buổi này đã có yêu cầu thanh toán đang chờ. Hủy yêu cầu đó trước khi đổi cách thanh toán.";
  if (/already been paid/.test(message)) return "Buổi này đã được thanh toán.";
  if (/24 hours/.test(message)) return `Chỉ hủy được buổi đã xác nhận trước giờ tập ít nhất ${PT_CANCEL_NOTICE_HOURS} giờ. Vui lòng liên hệ lễ tân.`;
  if (/already started/.test(message)) return "Buổi tập đã bắt đầu, không thể hủy.";
  if (/cannot be cancelled/.test(message)) return "Buổi này đã kết thúc hoặc đã hủy trước đó.";
  if (error?.status === 403) return "Bạn không có quyền thực hiện thao tác này.";
  return message || fallback;
}
