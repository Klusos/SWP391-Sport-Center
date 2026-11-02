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
