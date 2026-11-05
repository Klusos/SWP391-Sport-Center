export function coachPtStatusSummary(bookings) {
  const rows = Array.isArray(bookings) ? bookings : [];
  return {
    total: rows.length,
    confirmed: rows.filter((booking) => booking?.status === "CONFIRMED").length,
    pendingPayment: rows.filter((booking) => booking?.status === "PENDING_PAYMENT").length,
  };
}
