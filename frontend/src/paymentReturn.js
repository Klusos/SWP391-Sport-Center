const positiveIdentifier = (value) => /^[1-9]\d*$/.test(String(value ?? "")) && Number.isSafeInteger(Number(value))
  ? Number(value) : null;

/** Resolve only an ID returned by the authenticated owner's payment-history response. */
export function findOwnedPayment(payments, paymentId) {
  const id = positiveIdentifier(paymentId);
  return id === null || !Array.isArray(payments) ? null : payments.find((payment) => payment.id === id) ?? null;
}

export function findOwnedPtBooking(bookings, payment) {
  if (payment?.purpose !== "PT_BOOKING" || !Array.isArray(bookings) || !positiveIdentifier(payment.referenceId)) return null;
  return bookings.find((booking) => booking.id === Number(payment.referenceId)) ?? null;
}

export function ptPaymentReturnMessage(payment) {
  switch (payment?.status) {
    case "SUCCESS": return "The backend confirms payment success. Booking status below is loaded separately and remains authoritative.";
    case "PENDING": return "Payment is still pending backend confirmation. The VNPay browser return alone does not confirm payment or the booking.";
    case "FAILED": return "The backend reports payment failure. Check the booking status separately before taking another action.";
    case "CANCELLED": return "The backend reports this payment as cancelled. The booking status is shown separately; do not infer settlement from the return URL.";
    case "REFUND_REQUIRED": return "The backend requires staff reconciliation or refund handling. Do not pay again for this booking.";
    default: return `Backend payment status: ${payment?.status || "unknown"}. Check payment history or contact Receptionist.`;
  }
}
