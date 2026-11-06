const RELEVANT = new Set(["SUCCESS", "REFUND_REQUIRED", "REFUNDED"]);
function matchingPayments(payments, id) {
  return (Array.isArray(payments) ? payments : []).filter((p) => p?.purpose === "COURSE_REGISTRATION" && Number(p.referenceId) === Number(id));
}
export function coursePaymentInfo(payments, id) {
  const rows = matchingPayments(payments, id);
  const status = rows.some((p) => p.status === "REFUND_REQUIRED") ? "REFUND_REQUIRED" : rows.some((p) => p.status === "REFUNDED") ? "REFUNDED" : rows.some((p) => p.status === "SUCCESS") ? "SUCCESS" : null;
  const amounts = rows.filter((p) => RELEVANT.has(p.status));
  return { status, amount: amounts.length === 1 ? amounts[0].amount : null };
}
export function paidCancellationEligibility(registration, payments, now = new Date()) {
  if (registration?.status !== "CONFIRMED") return { allowed: false, reason: "not-confirmed" };
  const value = registration?.cancellationDeadline;
  if (typeof value !== "string" || !value.trim()) return { allowed: false, reason: "deadline-unavailable" };
  const local = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(value);
  const deadline = new Date(local ? value + "+07:00" : value);
  if (Number.isNaN(deadline.getTime())) return { allowed: false, reason: "deadline-unavailable" };
  if (now.getTime() >= deadline.getTime()) return { allowed: false, reason: "deadline-passed" };
  const rows = matchingPayments(payments, registration.id).filter((p) => RELEVANT.has(p.status));
  return rows.length === 1 && rows[0].status === "SUCCESS" ? { allowed: true, reason: null } : { allowed: false, reason: "payment-unverified" };
}
export function cancellationErrorMessage(error) {
  const message = String(error?.message || "");
  if (/deadline|cancellation deadline/i.test(message)) return "Đã qua hạn hủy đăng ký này.";
  if (/already cancelled|already canceled|only a confirmed/i.test(message)) return "Đăng ký đã thay đổi trạng thái. Danh sách sẽ được cập nhật.";
  if (/not owned|own course/i.test(message)) return "Bạn không có quyền hủy đăng ký này.";
  if (/no successful payment|missing.*payment/i.test(message)) return "Chưa xác minh được khoản thanh toán thành công. Vui lòng liên hệ lễ tân.";
  if (/multiple successful|ambiguous|reconciliation/i.test(message)) return "Khoản thanh toán cần được lễ tân đối soát trước khi hủy.";
  if (/refund workflow|refund_required|refunded/i.test(message)) return "Khoản thanh toán đã ở quy trình hoàn tiền. Vui lòng làm mới trạng thái.";
  return "Không thể xác nhận việc hủy. Hãy kiểm tra lại trạng thái trước khi thử lại.";
}
