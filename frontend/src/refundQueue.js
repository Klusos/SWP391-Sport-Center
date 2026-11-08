export const REFUND_PURPOSES = ["MEMBERSHIP", "COURSE_REGISTRATION", "PT_BOOKING", "UPGRADE"];

const labels = {
  MEMBERSHIP: "Gói hội viên",
  COURSE_REGISTRATION: "Đăng ký khóa học",
  PT_BOOKING: "Buổi PT",
  UPGRADE: "Nâng cấp gói",
};

export function refundPurposeLabel(payment) {
  const purpose = payment?.purpose;
  return labels[purpose] || `Mục đích ${purpose || "không xác định"}`;
}

export function refundReferenceLabel(payment) {
  if (payment?.purpose === "COURSE_REGISTRATION") {
    return `Đăng ký #${payment.registrationId ?? payment.referenceId}`;
  }
  if (payment?.purpose === "PT_BOOKING") return `Booking PT #${payment.referenceId}`;
  if (payment?.purpose === "MEMBERSHIP") return `Gói #${payment.referenceId}`;
  if (payment?.purpose === "UPGRADE") return `Gói nâng cấp #${payment.referenceId}`;
  return `Tham chiếu #${payment?.referenceId ?? "—"}`;
}

export function refundQueueRows(page) {
  return Array.isArray(page?.content) ? page.content : [];
}
