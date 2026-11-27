/** Joins renewal attempts to Payments only through their persisted paymentId association. */
export function buildPendingMembershipPresentation(intents = [], payments = [], now = Date.now()) {
  const pendingPayments = (Array.isArray(payments) ? payments : []).filter((payment) =>
    ["MEMBERSHIP", "UPGRADE"].includes(String(payment?.purpose || "").toUpperCase())
      && String(payment?.status || "").toUpperCase() === "PENDING");
  const paymentById = new Map(pendingPayments.map((payment) => [String(payment.id), payment]));
  const pairedIntentByPaymentId = new Map();
  const requestIntents = [];
  const unresolvedIntents = [];

  for (const intent of Array.isArray(intents) ? intents : []) {
    const attempts = Array.isArray(intent?.attempts) ? intent.attempts : [];
    const latestAttempt = attempts.at(-1) || null;
    const linkedPayment = latestAttempt?.paymentId == null ? null : paymentById.get(String(latestAttempt.paymentId)) || null;
    const needsReconciliation = ["REFUND_REQUIRED", "REFUNDED", "CONFLICT", "COLLECTION_CONFLICT", "RECONCILIATION_REQUIRED"].includes(String(intent?.latestResult || "").toUpperCase())
      || ["CONFLICT", "COLLECTION_CONFLICT", "RECONCILIATION_REQUIRED"].includes(String(intent?.status || "").toUpperCase())
      || attempts.some((attempt) => attempt?.manualReconciliationRequired === true
        || attempt?.collectionRecorded === true && attempt?.settlementAccepted !== true
        || ["CONFLICT", "COLLECTION_CONFLICT", "RECONCILIATION_REQUIRED"].includes(String(attempt?.status || "").toUpperCase()));

    if (intent?.status === "OPEN" && !needsReconciliation) {
      if (linkedPayment) pairedIntentByPaymentId.set(String(linkedPayment.id), { intent, attempt: latestAttempt });
      else requestIntents.push({ intent, attempt: latestAttempt, retry: Boolean(latestAttempt) });
    } else if (needsReconciliation) {
      unresolvedIntents.push({ intent, attempt: latestAttempt });
    }
  }

  const paymentItems = pendingPayments.map((payment) => {
    const paired = pairedIntentByPaymentId.get(String(payment.id));
    const attempt = paired?.attempt || null;
    const deadlineText = String(attempt?.deadline || "");
    const deadlineValue = deadlineText && !/[zZ]|[+-]\d{2}:\d{2}$/.test(deadlineText) ? `${deadlineText}+07:00` : deadlineText;
    const deadline = deadlineValue ? new Date(deadlineValue).getTime() : NaN;
    const attemptExpired = attempt?.status !== "PENDING"
      || Number.isFinite(deadline) && deadline <= now;
    return { payment, renewalIntent: paired?.intent || null, renewalAttempt: attempt,
      renewalRetry: Boolean(paired?.intent && attemptExpired) };
  });

  return { paymentItems, requestIntents, unresolvedIntents };
}
