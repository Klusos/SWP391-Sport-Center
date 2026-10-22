import { vietnamDate, validVnpayUrl } from "./courseCheckout.js";

export function renewalPayments(payments) {
  return (Array.isArray(payments) ? payments : []).filter((payment) => payment?.isRenewal === true);
}

export function renewalAttemptState(payment, now = Date.now()) {
  const attempt = payment?.renewalCheckoutAttempt;
  if (payment?.status === "REFUND_REQUIRED" || attempt?.status === "RECONCILIATION_REQUIRED") return "RECONCILIATION";
  if (!attempt) return "NONE";
  if (attempt.status === "URL_ISSUED") {
    const expiry = vietnamDate(attempt.expiresAt)?.getTime();
    return Number.isFinite(expiry) && expiry <= now ? "EXPIRED" : "ISSUED";
  }
  if (attempt.status === "FAILED") return "FAILED";
  return attempt.status;
}

export function canRequestRenewalCheckout(payment, now = Date.now()) {
  return payment?.isRenewal === true && payment.status === "PENDING" && payment.method === "ONLINE"
    && typeof payment.renewalAutoRenewChoice === "boolean"
    && ["NONE", "EXPIRED", "FAILED"].includes(renewalAttemptState(payment, now));
}

export function renewalOptionsPayload(method, autoRenewChoice) {
  if (!["ONLINE", "COUNTER"].includes(method) || typeof autoRenewChoice !== "boolean") {
    throw new Error("Choose a payment method and explicitly choose Auto Renew on or off.");
  }
  return { method, renewalAutoRenewChoice: autoRenewChoice };
}

export function safeRenewalCheckoutUrl(value) {
  return validVnpayUrl(value);
}
