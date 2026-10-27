// Payment.note of renewal requests created by the backend auto-renewal job (MembershipAutoRenewalService.NOTE_PREFIX).
export const AUTO_RENEWAL_NOTE_PREFIX = "AUTO_RENEWAL membership #";
export const RENEWAL_NOTICE_DAYS = 3;

export const isAutoRenewalPayment = (payment) =>
  payment?.purpose === "MEMBERSHIP" && String(payment?.note || "").startsWith(AUTO_RENEWAL_NOTE_PREFIX);

// Only the last membership of the chain may be cancelled, and members may cancel it only before it starts.
export function cancellableMembershipId(chain, today, { staffCanCancelRunning = false } = {}) {
  const last = chain[chain.length - 1];
  if (!last) return null;
  return String(last.startDate) > today || staffCanCancelRunning ? last.id : null;
}

export function cancelMembershipErrorMessage(error) {
  if (error?.status === 401) return "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
  if (error?.status === 403) return "Gói đang chạy không thể hủy hay hoàn tiền; bạn có thể tắt tự gia hạn. Trường hợp đặc biệt vui lòng liên hệ quản trị viên.";
  if (error?.status === 400) return "Cần nhập lý do khi hủy gói đang chạy.";
  if (/starts after this one/.test(error?.message || "")) return "Hãy hủy gói nối tiếp phía sau trước.";
  if (/pending membership payment/.test(error?.message || "")) return "Thành viên còn thanh toán gói đang chờ; hãy xử lý hoặc hủy thanh toán đó trước.";
  return error?.message || "Không thể hủy gói.";
}

// Current and already-bought upcoming memberships, oldest first, so the renewal chain reads in order.
export function activeMembershipChain(memberships, today) {
  return (memberships || [])
    .filter((membership) => String(membership?.status || "").toUpperCase() === "ACTIVE" && String(membership.endDate || "") > today)
    .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)));
}
