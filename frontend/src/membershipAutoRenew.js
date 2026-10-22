function paymentById(payments, id) {
  return (payments || []).find((payment) => Number(payment.id) === Number(id));
}

export function businessToday(now = new Date()) {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Ho_Chi_Minh", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

/** Mirrors only the visible eligibility checks; backend remains authoritative. */
export function canEnableMembershipAutoRenew(membership, memberships, payments, today = businessToday()) {
  if (!membership || membership.status?.toUpperCase() !== "ACTIVE" || membership.autoRenew === true
      || !membership.startDate || membership.startDate > today || !membership.endDate || membership.endDate <= today) return false;
  const sourcePayment = paymentById(payments, membership.paymentId);
  if (!sourcePayment || sourcePayment.status !== "SUCCESS") return false;
  const paidPeriods = (memberships || [])
    .filter((item) => paymentById(payments, item.paymentId)?.status === "SUCCESS")
    .sort((a, b) => String(b.startDate || "").localeCompare(String(a.startDate || "")) || Number(b.id) - Number(a.id));
  if (Number(paidPeriods[0]?.id) !== Number(membership.id)) return false;
  return !(payments || []).some((payment) => payment?.isRenewal === true
    && Number(payment.renewalSourceMembershipId) === Number(membership.id)
    && payment.status !== "PENDING");
}

export function canCancelMembershipAutoRenew(membership) {
  return membership?.status?.toUpperCase() === "ACTIVE" && membership.autoRenew === true;
}

/** Categorizes persisted Membership rows by status and Vietnam business date. */
export function membershipSections(memberships, today = businessToday()) {
  const current = [];
  const future = [];
  const history = [];
  for (const membership of memberships || []) {
    if (membership?.status?.toUpperCase() === "ACTIVE"
        && membership.startDate <= today && membership.endDate > today) current.push(membership);
    else if (membership?.status?.toUpperCase() === "ACTIVE" && membership.startDate > today) future.push(membership);
    else history.push(membership);
  }
  return { current, future, history, hasOverlap: current.length > 1 };
}

/** Safe user-facing reason for hidden enable controls; server remains authoritative. */
export function autoRenewEnableReason(membership, memberships, payments, today = businessToday()) {
  if (!membership) return "Không tìm thấy thông tin Membership.";
  if (membership.status?.toUpperCase() !== "ACTIVE") return "Chỉ Membership đang hoạt động mới có thể bật Auto Renew.";
  if (membership.autoRenew === true) return "Auto Renew đang được bật.";
  if (!membership.startDate || membership.startDate > today) return "Auto Renew chỉ bật được cho Membership đã bắt đầu.";
  if (!membership.endDate || membership.endDate <= today) return "Membership đã hết hạn.";
  const sourcePayment = paymentById(payments, membership.paymentId);
  if (!sourcePayment) return "Không có thông tin thanh toán nguồn để xác minh Membership đã thanh toán.";
  if (sourcePayment.status !== "SUCCESS") return "Membership nguồn chưa có thanh toán thành công.";
  const paidPeriods = (memberships || [])
    .filter((item) => paymentById(payments, item.paymentId)?.status === "SUCCESS")
    .sort((a, b) => String(b.startDate || "").localeCompare(String(a.startDate || "")) || Number(b.id) - Number(a.id));
  if (Number(paidPeriods[0]?.id) !== Number(membership.id)) return "Chỉ kỳ Membership đã thanh toán cuối cùng mới đủ điều kiện bật Auto Renew.";
  if ((payments || []).some((payment) => payment?.isRenewal === true
      && Number(payment.renewalSourceMembershipId) === Number(membership.id)
      && payment.status !== "PENDING")) return "Yêu cầu gia hạn của kỳ này đã ở trạng thái kết thúc; hệ thống không cho bật lại.";
  return "Membership đủ điều kiện bật Auto Renew.";
}
