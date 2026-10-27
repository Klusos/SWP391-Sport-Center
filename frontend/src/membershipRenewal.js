// Payment.note of renewal requests created by the backend auto-renewal job (MembershipAutoRenewalService.NOTE_PREFIX).
export const AUTO_RENEWAL_NOTE_PREFIX = "AUTO_RENEWAL membership #";
export const RENEWAL_NOTICE_DAYS = 3;

export const isAutoRenewalPayment = (payment) =>
  payment?.purpose === "MEMBERSHIP" && String(payment?.note || "").startsWith(AUTO_RENEWAL_NOTE_PREFIX);

// Current and already-bought upcoming memberships, oldest first, so the renewal chain reads in order.
export function activeMembershipChain(memberships, today) {
  return (memberships || [])
    .filter((membership) => String(membership?.status || "").toUpperCase() === "ACTIVE" && String(membership.endDate || "") > today)
    .sort((a, b) => String(a.startDate).localeCompare(String(b.startDate)));
}
