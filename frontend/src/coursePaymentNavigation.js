export function parseCoursePaymentTarget(params) {
  const rawPaymentId = params.get("paymentId");
  const rawRegistrationId = params.get("registrationId");
  const paymentId = Number(rawPaymentId);
  const registrationId = rawRegistrationId == null ? null : Number(rawRegistrationId);
  if (!/^\d+$/.test(rawPaymentId || "") || !Number.isSafeInteger(paymentId) || paymentId < 1) return { valid: false };
  if (rawRegistrationId != null && (!/^\d+$/.test(rawRegistrationId) || !Number.isSafeInteger(registrationId) || registrationId < 1)) return { valid: false };
  return { valid: true, paymentId, registrationId };
}

export async function locateCoursePayment(fetchPage, target, size = 20) {
  if (!target?.valid) return { found: false, reason: "invalid" };
  const first = await fetchPage(0, size);
  const pageCount = Number.isSafeInteger(first?.totalPages) ? first.totalPages : 0;
  for (let page = 0; page < Math.max(1, pageCount); page += 1) {
    const data = page === 0 ? first : await fetchPage(page, size);
    const row = (data?.content || []).find((item) => Number(item.paymentId) === target.paymentId
      && (target.registrationId == null || Number(item.registrationId) === target.registrationId));
    if (row) return { found: true, page, row };
  }
  return { found: false, reason: "not-found" };
}
